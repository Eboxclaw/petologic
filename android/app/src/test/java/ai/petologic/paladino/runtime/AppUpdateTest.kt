package ai.petologic.paladino.runtime
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class AppUpdateTest{
 @Test fun versions_parse_and_compare(){
  assertEquals(listOf(0,2,1),AppUpdate.parseVersion("v0.2.1-preview"))
  assertEquals(listOf(0,2,1),AppUpdate.parseVersion("0.2.1"))
  assertEquals(listOf(1,0),AppUpdate.parseVersion("V1.0"))
  assertNull(AppUpdate.parseVersion("nightly"));assertNull(AppUpdate.parseVersion(""))
  listOf("0.2.2-preview" to "0.2.1-preview","1.0" to "0.9.9","0.3" to "0.2.9","0.2.10" to "0.2.9")
   .forEach{(r,c)->assertTrue("$r must be newer than $c",AppUpdate.isNewer(r,c))}
  listOf("0.2.1-preview" to "0.2.1-preview","0.2.0" to "0.2.1-preview","junk" to "0.2.1")
   .forEach{(r,c)->assertFalse("$r must not be newer than $c",AppUpdate.isNewer(r,c))}
 }
 @Test fun releases_parse_picks_newest_apk_release_above_current(){
  val json="""[
   {"tag_name":"v0.2.2-preview","prerelease":true,"body":"- in-app updates","assets":[
    {"name":"petologic-0.2.2-preview-arm64.apk","browser_download_url":"https://x/petologic-0.2.2-preview-arm64.apk","size":96708332},
    {"name":"SHA256SUMS.txt","browser_download_url":"https://x/SHA256SUMS.txt"}]},
   {"tag_name":"v0.2.1-preview","body":"old","assets":[
    {"name":"petologic-0.2.1-preview-arm64.apk","browser_download_url":"https://x/old.apk","size":1}]}
  ]"""
  val release=AppUpdate.parseReleases(json,"0.2.1-preview")!!
  assertEquals("v0.2.2-preview",release.tag)
  assertEquals(96708332L,release.apkSize)
  assertEquals("https://x/SHA256SUMS.txt",release.shaUrl)
  assertTrue(release.notes.contains("in-app updates"))
  assertNull("Same version must read as up to date",AppUpdate.parseReleases(json,"0.2.2-preview"))
  assertNull("Releases without an APK asset are skipped",AppUpdate.parseReleases("""[{"tag_name":"v9.9.9","assets":[{"name":"notes.txt","browser_download_url":"https://x/n"}]}]""","0.2.1-preview"))
  assertNull("Unparseable JSON tags are skipped",AppUpdate.parseReleases("""[{"tag_name":"nightly","assets":[{"name":"x.apk","browser_download_url":"https://x/x.apk","size":1}]}]""","0.2.1-preview"))
 }
 @Test fun checksum_line_is_matched_exactly(){
  val body="3d10b6ab11111111111111111111111111111111111111111111111111111111  petologic-0.2.1-preview-arm64.apk\ndeadbeef  other.txt"
  assertEquals("3d10b6ab11111111111111111111111111111111111111111111111111111111",AppUpdate.checksumFor(body,"petologic-0.2.1-preview-arm64.apk"))
  assertNull(AppUpdate.checksumFor(body,"missing.apk"))
  assertNull("Non-hex lines are not checksums",AppUpdate.checksumFor("zzz  petologic.apk","petologic.apk"))
 }
 @Test fun file_hash_is_stable_and_well_formed(){
  val file=File.createTempFile("update",".apk").apply{writeBytes(byteArrayOf(1,2,3,4))}
  file.deleteOnExit()
  val hash=AppUpdate.hash(file)
  assertEquals(64,hash.length)
  assertEquals(hash,AppUpdate.hash(file))
 }
}
