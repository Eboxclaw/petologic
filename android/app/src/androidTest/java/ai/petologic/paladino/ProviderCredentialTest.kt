package ai.petologic.paladino
import ai.petologic.paladino.runtime.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

class ProviderCredentialTest {
 @Test fun each_provider_keeps_its_own_encrypted_key_and_disconnect(){
  // Isolated preference file in target UID; never read or mutate production credentials.
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=CredentialStore(context,"provider_credentials_test")
  try{
   CloudProvider.entries.forEach{store.save("synthetic-test-key-${it.id}",it)}
   CloudProvider.entries.forEach{assertEquals("synthetic-test-key-${it.id}",CredentialStore(context,"provider_credentials_test").read(it))}
   val prefs=context.getSharedPreferences("provider_credentials_test",0)
   assertFalse(prefs.all.values.joinToString().contains("synthetic-test-key"))
   store.disconnect(CloudProvider.OPENAI)
   assertNull(store.read(CloudProvider.OPENAI))
   assertNotNull(store.read(CloudProvider.ZAI));assertNotNull(store.read())
  }finally{CloudProvider.entries.forEach{store.disconnect(it)}}
 }
}
