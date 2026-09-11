package ai.petologic.paladino
import ai.petologic.paladino.data.UserCopy
import org.junit.Test
import org.junit.Assert.*
class UserCopyManifestTest {
 @Test fun loadable_accepts_same_and_older_schema(){
  val running=3
  assertTrue(UserCopy.loadable(UserCopy.FORMAT,3,running))
  assertTrue(UserCopy.loadable(UserCopy.FORMAT,1,running))
 }
 @Test fun loadable_rejects_wrong_format_and_versions(){
  val running=3
  assertFalse(UserCopy.loadable(0,3,running)) // no format field / unknown format
  assertFalse(UserCopy.loadable(2,3,running)) // future copy format
  assertFalse(UserCopy.loadable(UserCopy.FORMAT,4,running)) // newer schema
  assertFalse(UserCopy.loadable(UserCopy.FORMAT,0,running)) // missing schema version
 }
}
