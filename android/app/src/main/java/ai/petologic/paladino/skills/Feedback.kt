package ai.petologic.paladino.skills

import ai.petologic.paladino.R
import android.view.HapticFeedbackConstants

/** Sounds live in an R-free enum so the gating matrix is testable without Android resources. */
enum class FeedbackSound(val res:Int){NONE(0),DROP(R.raw.fx_drop),PLOP(R.raw.fx_plop),BLIP(R.raw.fx_blip),CONFIRM(R.raw.fx_confirm)}

/**
 * The whole feedback vocabulary: a handful of quiet events, each one sound + at most one haptic.
 * Gating is pure so tests own the matrix — the player only applies it.
 */
enum class FeedbackEvent(val sound:FeedbackSound,val haptic:Int){
 MESSAGE_SENT(FeedbackSound.DROP,HapticFeedbackConstants.VIRTUAL_KEY),
 REPLY_DONE(FeedbackSound.PLOP,-1),
 APPROVAL_ASKED(FeedbackSound.BLIP,-1),
 APPROVAL_OK(FeedbackSound.CONFIRM,HapticFeedbackConstants.CONFIRM),
 APPROVAL_NO(FeedbackSound.NONE,HapticFeedbackConstants.REJECT),
 ERROR(FeedbackSound.PLOP,HapticFeedbackConstants.REJECT),
 SPRITE_TAP(FeedbackSound.DROP,HapticFeedbackConstants.VIRTUAL_KEY),
 WIDGET_OPEN(FeedbackSound.BLIP,HapticFeedbackConstants.VIRTUAL_KEY)
}

fun shouldPlay(soundOn:Boolean,ringerNormal:Boolean,powerSave:Boolean,event:FeedbackEvent):Boolean=
 soundOn&&ringerNormal&&!powerSave&&event.sound!=FeedbackSound.NONE

fun shouldBuzz(hapticsOn:Boolean,event:FeedbackEvent):Boolean=hapticsOn&&event.haptic>=0
