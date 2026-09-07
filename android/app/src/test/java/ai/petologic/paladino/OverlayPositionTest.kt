package ai.petologic.paladino
import org.junit.Test
import org.junit.Assert.*
class OverlayPositionTest {
 @Test fun anchors_remain_in_bounds_across_rotation_and_size_changes(){
  for((start,end) in listOf(24 to 1080,48 to 2400,0 to 120))for(size in listOf(48,88,304))for(fraction in listOf(-1f,0f,.3f,1f,2f)){
   val result=OverlayPosition.coordinate(fraction,start,end,size)
   assertTrue(result>=start&&result<=maxOf(start,end-size))
  }
 }
 @Test fun stored_anchor_restores_position(){
  val fraction=OverlayPosition.fraction(500,20,1000,80)
  assertTrue(kotlin.math.abs(500-OverlayPosition.coordinate(fraction,20,1000,80))<=1)
 }
}
