package ai.petologic.security
import kotlin.test.*
class TextBudgetTest {
 @Test fun unicodeBudgetDoesNotSplitCodePoints(){val result=boundText("🙂áxyz",5);assertEquals("🙂",result.text);assertTrue(result.truncated);assertTrue(result.text.toByteArray().size<=5)}
 @Test fun exactBudget(){assertFalse(boundText("á",2).truncated);assertEquals("",boundText("á",1).text)}
}
