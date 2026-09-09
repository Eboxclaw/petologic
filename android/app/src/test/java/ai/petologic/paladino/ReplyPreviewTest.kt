package ai.petologic.paladino
import org.junit.Assert.assertEquals
import org.junit.Test
class ReplyPreviewTest {
 @Test fun compact_emphasis_does_not_change_literal_symbols_or_links(){
  assertEquals("Code safira 742",replyPreview("Code **safira** `742`"))
  assertEquals("2 * 3; file_name; https://example.com",replyPreview("2 * 3; file_name; https://example.com"))
  assertEquals("**unfinished",replyPreview("**unfinished"))
 }
}
