package ai.petologic.skills.security.guard

import android.content.Context
import ai.petologic.security.YaraRulesPack
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * The verified YARA rule pack downloaded through the signed manifest channel. The pack content is
 * hash-pinned by the signed manifest; this store keeps the verified JSON for the isolated scanner,
 * which receives it as a read-only descriptor like the hash pack.
 */
class YaraRulesStore(private val context:Context) {
 private val baseFile=File(context.noBackupFilesDir,"security-signed-rules.json")
 fun install(pack:YaraRulesPack) {
  val tmp=File(context.noBackupFilesDir,"security-signed-rules.json.new")
  tmp.writeText(Json.encodeToString(pack))
  if(baseFile.exists())baseFile.delete()
  check(tmp.renameTo(baseFile)){"YARA rule pack could not be activated."} }
 fun load():YaraRulesPack?=runCatching {
  if(!baseFile.exists())return null
  val pack=Json.decodeFromString<YaraRulesPack>(baseFile.readText())
  pack.takeIf{it.expiresAt>System.currentTimeMillis()}
 }.getOrNull()
 fun snapshot():File?=load()?.let{pack->File.createTempFile("security-rules-",".json",context.cacheDir).apply{writeText(Json.encodeToString(pack))}}
 fun clear(){runCatching{baseFile.delete()}}
}
