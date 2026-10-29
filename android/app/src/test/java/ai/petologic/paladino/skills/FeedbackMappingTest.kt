package ai.petologic.paladino.skills

import ai.petologic.paladino.skills.FeedbackEvent.*
import org.junit.Test
import org.junit.Assert.*

class FeedbackMappingTest {
 @Test fun every_event_has_a_known_sound_or_is_silent(){
  // Only APPROVAL_NO is silent; every other event maps to one of the four synthesized sounds.
  for(event in entries){
   if(event==APPROVAL_NO)assertEquals(FeedbackSound.NONE,event.sound)
   else assertTrue("$event needs a sound",event.sound!=FeedbackSound.NONE)
  }
 }
 @Test fun sounds_play_only_when_enabled_ringer_normal_and_not_in_power_save(){
  assertTrue(shouldPlay(soundOn=true,ringerNormal=true,powerSave=false,event=MESSAGE_SENT))
  assertFalse("Silent ringer suppresses sounds",shouldPlay(true,ringerNormal=false,powerSave=false,MESSAGE_SENT))
  assertFalse("Battery saver suppresses sounds",shouldPlay(true,true,powerSave=true,MESSAGE_SENT))
  assertFalse("User toggle off suppresses sounds",shouldPlay(false,true,false,MESSAGE_SENT))
  assertFalse("Silent events never play",shouldPlay(true,true,false,APPROVAL_NO))
 }
 @Test fun haptics_buzz_only_for_events_with_an_effect(){
  assertTrue(shouldBuzz(hapticsOn=true,event=APPROVAL_OK))
  assertTrue(shouldBuzz(true,APPROVAL_NO))
  assertFalse("REPLY_DONE is deliberately silent and still",shouldBuzz(true,REPLY_DONE))
  assertFalse(shouldBuzz(false,APPROVAL_OK))
 }
 @Test fun haptic_constants_are_platform_backed(){
  // The effects come from HapticFeedbackConstants (compile-time constants, safe on minSdk 31).
  assertEquals(android.view.HapticFeedbackConstants.CONFIRM,APPROVAL_OK.haptic)
  assertEquals(android.view.HapticFeedbackConstants.REJECT,APPROVAL_NO.haptic)
 }
}
