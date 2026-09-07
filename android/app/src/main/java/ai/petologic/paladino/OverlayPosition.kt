package ai.petologic.paladino

/** Normalized anchors survive density, rotation and display size changes. */
internal object OverlayPosition {
 fun coordinate(fraction:Float,start:Int,end:Int,extent:Int):Int =
  start + ((end-start-extent).coerceAtLeast(0)*fraction.coerceIn(0f,1f)).toInt()
 fun fraction(coordinate:Int,start:Int,end:Int,extent:Int):Float =
  ((coordinate-start).toFloat()/(end-start-extent).coerceAtLeast(1)).coerceIn(0f,1f)
}
