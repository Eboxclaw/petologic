package ai.petologic.security

import java.io.InputStream
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Streaming only: no extraction paths, native execution, network, or whole-file allocations. */
class FileScanner(private val budget:ScanBudget=ScanBudget(),private val rules:RuleEngine?=null) {
 fun scan(input:InputStream, indicators:List<HashIndicator>, packVersion:String,
  cancelled:()->Boolean={false}):Finding {
  val started=System.nanoTime();val findings=mutableListOf<Evidence>()
  val limits=mutableListOf(if(rules==null)"YARA rules unavailable; only exact SHA-256 indicators are checked." else "YARA includes only a harmless pipeline test rule; malware-family rule coverage is not installed.",
   "This scan cannot establish whether the file or device is safe.")
  val checks=mutableListOf<String>();var fingerprint:String?=null;var partial=false
  fun checkBudget(){
   if(cancelled()||Thread.currentThread().isInterrupted)throw InterruptedException("Scan cancelled.")
   require((System.nanoTime()-started)/1_000_000 < budget.timeoutMillis){"Scan deadline reached."}
  }
  fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
  fun match(hash:String){HashIndicatorMatcher.match(hash,indicators)?.let{findings+=Evidence("sha256",packVersion,it.id,it.source,it.license,MatchType.EXACT_SHA256,"Exact file or archive-entry hash matched.")}}
  val containerBuffer=java.io.ByteArrayOutputStream()
  val nativeLimit=8*1024*1024
  var containerFits=true
  fun ruleScan(bytes:ByteArray){if(rules!=null){findings+=rules.scan(bytes);checkBudget()}}
  val containerDigest=MessageDigest.getInstance("SHA-256");var countBytes=0L
  val bounded=object:FilterInputStream(input){
   override fun read():Int {val b=ByteArray(1);return if(read(b,0,1)<0)-1 else b[0].toInt() and 255}
   override fun read(b:ByteArray,off:Int,len:Int):Int {
    checkBudget();val n=`in`.read(b,off,len)
    if(n>0){countBytes+=n;require(countBytes<=budget.maxInput){"Input limit reached."};containerDigest.update(b,off,n)
     if(rules!=null&&containerFits){if(countBytes<=nativeLimit)containerBuffer.write(b,off,n)else{containerFits=false;containerBuffer.reset()}}}
    return n
   }
  }
  val stream=BufferedInputStream(bounded,32768)
  try {
   stream.mark(4);val magic=ByteArray(4);val n=stream.read(magic);stream.reset()
   if(n>=2&&magic[0]==0x50.toByte()&&magic[1]==0x4b.toByte()){
    val zip=ZipInputStream(stream);val names=HashSet<String>();var expanded=0L;var count=0
    while(true){
     checkBudget();val entry=zip.nextEntry?:break;count++
     require(count<=budget.maxEntries){"Archive entry limit reached."}
     require(!entry.name.startsWith("/")&&!entry.name.contains('\\')&&!entry.name.split('/').contains("..")&&names.add(entry.name)){"Unsafe or duplicate archive entry."}
     require(entry.size<=budget.maxEntry){"Archive entry too large."}
     val entryBuffer=java.io.ByteArrayOutputStream();var entryFits=true
     val digest=MessageDigest.getInstance("SHA-256");var size=0L;val bytes=ByteArray(32768)
     while(true){checkBudget();val sizeRead=zip.read(bytes);if(sizeRead<0)break;size+=sizeRead;expanded+=sizeRead
      require(size<=budget.maxEntry&&expanded<=budget.maxExpanded){"Archive expansion limit reached."};digest.update(bytes,0,sizeRead)
      if(rules!=null&&entryFits){if(size<=nativeLimit)entryBuffer.write(bytes,0,sizeRead)else{entryFits=false;entryBuffer.reset()}}
     }
     if(rules!=null){if(entryFits)ruleScan(entryBuffer.toByteArray())else{partial=true;limits+="Native rules skipped an entry above 8 MiB."}}
     if(!entry.isDirectory)match(hex(digest.digest()))
     if(entry.name.lowercase().endsWith(".zip")||entry.name.lowercase().endsWith(".apk")){partial=true;limits+="Nested archive contents were not scanned."}
     zip.closeEntry()
    }
    require(count>0){"Empty or unsupported archive."}
    checks+="SHA-256 of $count first-layer archive entries"
    limits+="Archive central-directory and APK signing validation are not performed."
   }
   val buffer=ByteArray(32768);while(stream.read(buffer)>=0)checkBudget()
   if(rules!=null){if(containerFits){ruleScan(containerBuffer.toByteArray());checks+="YARA 4.5.8 self-test rule"}else{partial=true;limits+="Native rules skipped container above 8 MiB."}}
   fingerprint=hex(containerDigest.digest());match(fingerprint);checks+="Container SHA-256"
  }catch(e:InterruptedException){throw e}
  catch(e:Exception){
   partial=true;limits+="Scan incomplete: ${e.javaClass.simpleName}"
   // A malformed ZIP must not hide an otherwise exact container signature.
   try{require(countBytes<=budget.maxInput);checkBudget();val buffer=ByteArray(32768);while(stream.read(buffer)>=0)checkBudget()
    fingerprint=hex(containerDigest.digest());match(fingerprint);checks+="Container SHA-256"
   }catch(cancel:InterruptedException){throw cancel}catch(_:Exception){fingerprint=null}
  }
  if(indicators.isEmpty()){partial=true;limits+="No active threat indicators installed."}
  if(findings.distinctBy{it.indicatorId}.size>64){partial=true;limits+="Evidence list capped at 64 matches."}
  return Finding(kind="file",fingerprint=fingerprint,evidence=findings.distinctBy{it.indicatorId}.take(64),
   coverage=if(partial)Coverage.PARTIAL else Coverage.COMPLETE,limitations=limits.distinct(),checks=checks)
 }
}
