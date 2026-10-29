package ai.petologic.paladino

import ai.petologic.core.IdleCycle
import ai.petologic.core.IdleSlot
import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays pet animations on a plain ImageView. Idle chains clip 1 (one cycle) and
 * clip 2 (two cycles) through [IdleCycle] so looping keeps moving; other
 * reactions repeat their single clip. While [gate] is false nothing decodes or
 * plays and the target keeps its static drawable. Presentation only: it cannot
 * grant tools or trigger inference.
 */
internal class SpriteAnimator(
 private val resources: Resources,
 private val scope: CoroutineScope,
 private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
 private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
){
 /** Owner gate: lifecycle/resumed state, animation pref and system reduced motion. */
 var gate: () -> Boolean = { true }
 private var target: ImageView? = null
 private var shownOn: ImageView? = null
 private var shown: AnimatedImageDrawable? = null
 private var playing: Pair<Int, Boolean>? = null
 private var idlePair: Pair<Int, Int>? = null
 private var slot: IdleSlot = IdleCycle.first()
 private var load: Job? = null

 fun attach(view: ImageView) { target = view }

 /** Chain the idle pair: first clip once, second clip twice, repeating. */
 fun playIdle(first: Int, second: Int) {
  if (idlePair == first to second && shown != null) { restartIfIdle(); return }
  idlePair = first to second
  slot = IdleCycle.first()
  playClip(if (slot.clip == 0) first else second, infinite = false, chain = ::clipEnded)
 }

 /** Play one clip on infinite repeat (working/other reactions). */
 fun playLooping(resource: Int) {
  if (idlePair == null && playing == resource to true && shown != null) { restartIfIdle(); return }
  idlePair = null
  playClip(resource, infinite = true, chain = null)
 }

 /** Re-evaluate the gate after unlock, pref change or foregrounding. */
 fun refresh() {
  val image = shown
  if (!gate()) { image?.stop(); return }
  val pair = idlePair
  if (image != null) { if (!image.isRunning) image.start() }
  else if (pair != null) playIdle(pair.first, pair.second)
  else playing?.let { playClip(it.first, it.second, chain = null) }
 }

 fun pause() { shown?.stop() }

 fun release() {
  load?.cancel(); shown?.clearAnimationCallbacks(); shown?.stop()
  shown = null; playing = null; idlePair = null; shownOn = null; target = null
 }

 private fun restartIfIdle() { if (gate() && shown?.isRunning == false) shown?.start() }

 private fun clipEnded() {
  val pair = idlePair ?: return
  slot = IdleCycle.next(slot)
  playClip(if (slot.clip == 0) pair.first else pair.second, infinite = false, chain = ::clipEnded)
 }

 private fun playClip(resource: Int, infinite: Boolean, chain: (() -> Unit)?) {
  val key = resource to infinite
  val view = target
  if (playing == key && shown != null && shownOn === view) { restartIfIdle(); return }
  if (playing == key && shown != null && view != null) { shownOn = view; view.setImageDrawable(shown); restartIfIdle(); return }
  playing = key; load?.cancel()
  shown?.clearAnimationCallbacks(); shown?.stop(); shown = null; shownOn = null
  if (view == null || !gate()) return
  load = scope.launch {
   val drawable = withContext(ioDispatcher) { runCatching {
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, resource)) as? AnimatedImageDrawable
   }.getOrNull() } ?: return@launch
   if (playing != key || target !== view) return@launch
   drawable.repeatCount = if (infinite) AnimatedImageDrawable.REPEAT_INFINITE else 0
   // registerAnimationCallback requires a Looper thread; decoding happens on IO, so hop to Main
   // for all drawable wiring.
   withContext(mainDispatcher) {
    if (playing != key || target !== view) return@withContext
    if (chain != null) drawable.registerAnimationCallback(object : android.graphics.drawable.Animatable2.AnimationCallback() {
     override fun onAnimationEnd(d: android.graphics.drawable.Drawable) { scope.launch { if (shown === drawable) chain() } }
    })
    shown = drawable; shownOn = view
    view.setImageDrawable(drawable)
    if (gate()) drawable.start()
   }
  }
 }
}
