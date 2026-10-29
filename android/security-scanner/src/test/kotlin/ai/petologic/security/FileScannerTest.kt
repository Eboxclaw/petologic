package ai.petologic.security

import kotlin.test.*
import java.io.*
import java.security.MessageDigest
import java.util.zip.*
class FileScannerTest {
 private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 private fun indicator(bytes:ByteArray)=HashIndicator(sha(bytes),"synthetic-${sha(bytes)}","test fixture","CC0")
 private fun archive(name:String="classes.dex",bytes:ByteArray="benign fixture".toByteArray()):ByteArray=ByteArrayOutputStream().also{out->ZipOutputStream(out).use{zip->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}.toByteArray()
 @Test fun exactMatchAndBenignNoMatch(){val bytes="fixture".toByteArray();val scanner=FileScanner();assertEquals(1,scanner.scan(bytes.inputStream(),listOf(indicator(bytes)),"1").evidence.size);assertTrue(scanner.scan("other".byteInputStream(),listOf(indicator(bytes)),"1").evidence.isEmpty())}
 @Test fun containerAndEntryHashes(){val entry="test dex".toByteArray();val zip=archive(bytes=entry);val result=FileScanner().scan(zip.inputStream(),listOf(indicator(zip),indicator(entry)),"1");assertEquals(sha(zip),result.fingerprint);assertEquals(2,result.evidence.size)}
 @Test fun traversalIsPartial(){val result=FileScanner().scan(archive("../escape").inputStream(),emptyList(),"none");assertEquals(Coverage.PARTIAL,result.coverage);assertNotNull(result.fingerprint)}
 @Test fun expansionBombIsBounded(){val result=FileScanner(ScanBudget(maxEntry=100)).scan(archive(bytes=ByteArray(10000)).inputStream(),emptyList(),"none");assertEquals(Coverage.PARTIAL,result.coverage);assertNotNull(result.fingerprint)}
 @Test fun cancellationEscapes(){assertFailsWith<InterruptedException>{FileScanner().scan("test".byteInputStream(),emptyList(),"none"){true}}}
 @Test fun missingPackNeverComplete(){assertEquals(Coverage.PARTIAL,FileScanner().scan("test".byteInputStream(),emptyList(),"none").coverage)}
 @Test fun malformedArchiveStillMatchesExactHash(){val data=archive("../escape");val result=FileScanner().scan(data.inputStream(),listOf(indicator(data)),"1");assertTrue(result.evidence.any{it.matchType==MatchType.EXACT_SHA256});assertEquals(Coverage.PARTIAL,result.coverage)}
 @Test fun inputLimitEnforced(){assertNull(FileScanner(ScanBudget(maxInput=2)).scan("large".byteInputStream(),emptyList(),"none").fingerprint)}
 @Test fun nestedArchiveIsPartial(){val bytes=archive("nested.zip",archive());assertEquals(Coverage.PARTIAL,FileScanner().scan(bytes.inputStream(),listOf(indicator(bytes)),"1").coverage)}
}
