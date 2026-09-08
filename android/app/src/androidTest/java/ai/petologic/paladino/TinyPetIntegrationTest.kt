package ai.petologic.paladino
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.test.platform.app.InstrumentationRegistry
import ai.petologic.core.PaladinoManifest
import org.junit.Test
import org.junit.Assert.*

class TinyPetIntegrationTest {
 @Test fun sprite_preferences_restore_without_changing_role_authority(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val app=context.applicationContext as PaladinoApplication
  val original=app.tinyPets.state.value
  try{
   val changed=original.copy(visible=false,animate=false,sizeDp=48,widgetCaption=false,widgetSession="test-session")
   app.tinyPets.update(changed)
   assertEquals(changed,TinyPetManager(context).state.value)
   val role=PaladinoManifest.parse(context.assets.open(TinyPetCatalog.paladino.manifestAsset).bufferedReader().use{it.readText()})
   assertEquals("paladino",TinyPetCatalog.paladino.roleId)
   assertEquals(1,TinyPetCatalog.available.size)
   assertNotNull(role)
  }finally{app.tinyPets.update(original)}
 }
 @Test fun supplied_idle_asset_is_an_android_animated_drawable(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  for(resource in listOf(R.raw.paladino_idle,R.raw.paladino_thinking)){
  val image=ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources,resource))
  assertTrue(image is AnimatedImageDrawable)
  assertTrue(image.intrinsicWidth>0&&image.intrinsicHeight>0)
  (image as AnimatedImageDrawable).stop()
  }
 }
}
