package ai.petologic.security.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

class UnsafeCorpusException(message:String):IllegalArgumentException(message)

object CorpusValidator {
 private const val MAX_CORPUS_BYTES=64*1024*1024
 private const val MAX_TEXT=8192
 private val json=Json{ignoreUnknownKeys=false}
 private val sha=Regex("[0-9a-f]{64}")
 private val id=Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")
 private val link=Regex("(?i)(?:https?://|www\\.)[^\\s<>()]+")
 private val email=Regex("(?i)[a-z0-9._%+-]+@([a-z0-9.-]+\\.[a-z]{2,})")
 private val phone=Regex("(?<![A-Za-z0-9])\\+?\\d(?:[ .()-]*\\d){8,}(?![A-Za-z0-9])")
 private val secret=Regex("""(?i)(?:api[_-]?key|secret|password|passwd|bearer|access[_-]?token)\s*[:=]\s*["']?[a-z0-9/+_.-]{8,}""")
 private val base64=Regex("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{40,}={0,2}(?![A-Za-z0-9+/])")

 @Serializable private data class SourceRegistry(val schema:Int,val sources:List<RegisteredSource>)
 @Serializable private data class RegisteredSource(val id:String,val name:String,val url:String,val license:String,
  val productionEligible:Boolean,val redistributionReviewed:Boolean,val use:String)

 data class Validated(val corpus:SafeBenchmarkCorpus,val raw:ByteArray)

 fun parseAndValidate(raw:ByteArray,registryRaw:ByteArray,mode:BenchmarkMode):Validated {
  rejectFileMagic(raw,"corpus")
  if(raw.size>MAX_CORPUS_BYTES)unsafe("Corpus exceeds $MAX_CORPUS_BYTES bytes.")
  val corpus=runCatching{json.decodeFromString<SafeBenchmarkCorpus>(raw.decodeToString())}
   .getOrElse{unsafe("Malformed benchmark corpus: ${it.message}")}
  val registry=runCatching{json.decodeFromString<SourceRegistry>(registryRaw.decodeToString())}
   .getOrElse{unsafe("Malformed source registry: ${it.message}")}
  if(corpus.schema!=1)unsafe("Unsupported corpus schema ${corpus.schema}.")
  if(corpus.profile !in setOf("smoke","release"))unsafe("Corpus profile must be smoke or release.")
  if(mode==BenchmarkMode.RELEASE&&corpus.profile!="release")unsafe("Release mode requires a release corpus.")
  if(corpus.corpusVersion.isBlank())unsafe("Corpus version is required.")
  val registered=registry.sources.associateBy{it.id}
  if(corpus.sources.isEmpty())unsafe("At least one source is required.")
  corpus.sources.forEach { source->
   val known=registered[source.id]?:unsafe("Unregistered source ${source.id}.")
   if(source.license!=known.license)unsafe("Source license mismatch for ${source.id}.")
   safeText(source.id,source.purpose)
  }
  duplicate("case",corpus.cases.map{it.id});duplicate("retrieval record",corpus.retrievalRecords.map{it.id});duplicate("retrieval query",corpus.retrievalQueries.map{it.id})
  val sourceIds=corpus.sources.map{it.id}.toSet()
  val familySplits=mutableMapOf<String,BenchmarkSplit>()
  corpus.cases.forEach { case->
   if(!id.matches(case.id)||!id.matches(case.familyId))unsafe("Invalid case or family id ${case.id}.")
   if(case.sourceId !in sourceIds)unsafe("Unknown source ${case.sourceId} in ${case.id}.")
   familySplits.putIfAbsent(case.familyId,case.split)?.let{if(it!=case.split)unsafe("Family ${case.familyId} crosses corpus splits.")}
   validateCase(case)
  }
  val recordIds=corpus.retrievalRecords.map{it.id}.toSet()
  corpus.retrievalRecords.forEach{record->
   if(record.sourceId !in sourceIds)unsafe("Unknown source ${record.sourceId} in retrieval record ${record.id}.")
   safeText(record.id,record.text)
  }
  corpus.retrievalQueries.forEach{query->
   if(query.sourceId !in sourceIds)unsafe("Unknown source ${query.sourceId} in retrieval query ${query.id}.")
   safeText(query.id,query.text)
   if(query.relevantRecordIds.isEmpty()||query.relevantRecordIds.any{it !in recordIds})unsafe("Retrieval query ${query.id} has missing relevance labels.")
  }
  if(mode==BenchmarkMode.RELEASE)validateReleaseShape(corpus)
  return Validated(corpus,raw)
 }

 private fun validateCase(case:BenchmarkCase){
  when(case.suite){
   BenchmarkSuite.HASH->{
    if(case.input==null||!sha.matches(case.input)||case.knownSha256==null||!sha.matches(case.knownSha256))unsafe("Hash case ${case.id} requires two lowercase SHA-256 strings.")
    if(case.appSignals!=null)unsafe("Hash case ${case.id} contains app signals.")
   }
   BenchmarkSuite.URL,BenchmarkSuite.MESSAGE->{
    val text=case.input?:unsafe("Text case ${case.id} is missing input.")
    safeText(case.id,text)
    if(case.knownSha256!=null||case.appSignals!=null)unsafe("Text case ${case.id} contains fields for another suite.")
   }
   BenchmarkSuite.APP_PROFILE->{
    val app=case.appSignals?:unsafe("App case ${case.id} is missing signals.")
    if(case.input!=null||case.knownSha256!=null)unsafe("App case ${case.id} contains fields for another suite.")
    app.requestedPermissions.forEach{safeText(case.id,it)};app.grantedDangerous.forEach{safeText(case.id,it)}
    app.installer?.let{safeText(case.id,it)}
   }
  }
 }

 private fun validateReleaseShape(corpus:SafeBenchmarkCorpus){
  fun count(suite:BenchmarkSuite,label:BenchmarkLabel,locale:String?=null)=corpus.cases.count{it.suite==suite&&it.label==label&&(locale==null||it.locale==locale)}
  if(count(BenchmarkSuite.HASH,BenchmarkLabel.CRITICAL)<28||count(BenchmarkSuite.HASH,BenchmarkLabel.BENIGN)<28)unsafe("Release corpus needs at least 28 positive and 28 mutated hash cases.")
  for(suite in listOf(BenchmarkSuite.URL,BenchmarkSuite.APP_PROFILE)){
   if(count(suite,BenchmarkLabel.BENIGN)<100||corpus.cases.count{it.suite==suite&&it.label!=BenchmarkLabel.BENIGN}<100)unsafe("Release corpus needs at least 100 benign and 100 suspicious ${suite.name} cases.")
   if(count(suite,BenchmarkLabel.CRITICAL)<25)unsafe("Release corpus needs at least 25 critical ${suite.name} cases.")
  }
  for(locale in listOf("en","pt")){
   if(count(BenchmarkSuite.MESSAGE,BenchmarkLabel.BENIGN,locale)<100||corpus.cases.count{it.suite==BenchmarkSuite.MESSAGE&&it.locale==locale&&it.label!=BenchmarkLabel.BENIGN}<100)unsafe("Release corpus needs at least 100 benign and 100 suspicious $locale messages.")
   if(count(BenchmarkSuite.MESSAGE,BenchmarkLabel.CRITICAL,locale)<25)unsafe("Release corpus needs at least 25 critical $locale messages.")
  }
  if(corpus.retrievalRecords.size<10_000)unsafe("Release corpus needs at least 10,000 retrieval records.")
  for(locale in listOf("en","pt"))if(corpus.retrievalQueries.count{it.locale==locale}<100)unsafe("Release corpus needs at least 100 $locale retrieval queries.")
 }

 private fun safeText(owner:String,value:String){
  if(value.isBlank()||value.length>MAX_TEXT)unsafe("$owner contains blank or oversized text.")
  if("../" in value||"..\\" in value)unsafe("$owner contains path traversal.")
  if(secret.containsMatchIn(value)||"-----BEGIN " in value)unsafe("$owner contains credential material.")
  email.findAll(value).forEach{match->if(!safeHost(match.groupValues[1]))unsafe("$owner contains possible personal email data.")}
  if(phone.containsMatchIn(link.replace(value,"")))unsafe("$owner contains possible personal phone data.")
  link.findAll(value).forEach{match->
   val raw=match.value.trimEnd('.',',',';',':','!','?',')',']','}')
   val host=raw.removePrefix("www.").substringAfter("://",raw).substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':').trim('[',']').lowercase()
   if(!safeHost(host))unsafe("$owner contains an active or non-reserved URL host: $host")
  }
  base64.findAll(value).forEach{match->runCatching{Base64.getDecoder().decode(match.value)}.getOrNull()?.let{rejectFileMagic(it,owner)}}
 }

 private fun safeHost(host:String):Boolean {
  if(host in setOf("example.com","example.org","example.net"))return true
  if(host.endsWith(".invalid")||host.endsWith(".test")||host.endsWith(".example"))return true
  val parts=host.split('.').mapNotNull{it.toIntOrNull()}
  if(parts.size==4&&parts.all{it in 0..255})return parts[0]==192&&parts[1]==0&&parts[2]==2||parts[0]==198&&parts[1]==51&&parts[2]==100||parts[0]==203&&parts[1]==0&&parts[2]==113
  return false
 }

 private fun rejectFileMagic(bytes:ByteArray,owner:String){
  val forbidden=listOf(byteArrayOf(0x50,0x4b),byteArrayOf(0x4d,0x5a),byteArrayOf(0x7f,0x45,0x4c,0x46),"dex\n".encodeToByteArray(),byteArrayOf(0xca.toByte(),0xfe.toByte(),0xba.toByte(),0xbe.toByte()))
  if(forbidden.any{magic->bytes.size>=magic.size&&bytes.copyOfRange(0,magic.size).contentEquals(magic)})unsafe("$owner contains executable or archive bytes.")
 }
 private fun duplicate(kind:String,ids:List<String>){if(ids.size!=ids.toSet().size)unsafe("Duplicate $kind id.")}
 private fun unsafe(message:String):Nothing=throw UnsafeCorpusException(message)
}
