package ai.petologic.paladino

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.PowerManager
import android.view.View

/** Applies the pure feedback gate with real device state; quiet by design (volume 0.25). */
object FeedbackPlayer{
 private var pool:SoundPool?=null
 private val ids=mutableMapOf<ai.petologic.paladino.skills.FeedbackSound,Int>()
 private var loadRequested=false

 private fun soundPool(context:Context):SoundPool=pool?:SoundPool.Builder()
  .setMaxStreams(2)
  .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
   .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
  .build().also{pool=it}

 private fun ensureLoaded(context:Context){
  if(loadRequested)return;loadRequested=true
  val sp=soundPool(context)
  for(sound in ai.petologic.paladino.skills.FeedbackSound.entries.filter{it!=ai.petologic.paladino.skills.FeedbackSound.NONE})
   ids[sound]=sp.load(context,sound.res,1)
 }

 fun play(context:Context,event:ai.petologic.paladino.skills.FeedbackEvent){
  val app=context.applicationContext as? PaladinoApplication?:return
  val prefs=app.tinyPets.state.value
  val audio=context.getSystemService(AudioManager::class.java)
  val power=context.getSystemService(PowerManager::class.java)
  if(!ai.petologic.paladino.skills.shouldPlay(prefs.sound,audio?.ringerMode==AudioManager.RINGER_MODE_NORMAL,
   power?.isPowerSaveMode==true,event))return
  ensureLoaded(context)
  ids[event.sound]?.let{soundPool(context).play(it,0.25f,0.25f,1,0,1f)}
 }

 fun buzz(view:View,event:ai.petologic.paladino.skills.FeedbackEvent){
  val app=view.context.applicationContext as? PaladinoApplication?:return
  if(!ai.petologic.paladino.skills.shouldBuzz(app.tinyPets.state.value.haptics,event))return
  view.performHapticFeedback(event.haptic)
 }

 fun combined(context:Context,view:View,event:ai.petologic.paladino.skills.FeedbackEvent){play(context,event);buzz(view,event)}
}
