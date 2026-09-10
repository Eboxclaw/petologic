package ai.petologic.paladino
import org.junit.Assert.*
import org.junit.Test
class ReplyPreviewTest {
 @Test fun only_web_links_without_credentials_are_opened(){
  assertTrue(isWebLink("https://example.com/docs?q=hello"))
  listOf("javascript:alert(1)","file:///data/local/tmp/x","intent://open","https://user:secret@example.com","//example.com").forEach{assertFalse(it,isWebLink(it))}
 }
}
