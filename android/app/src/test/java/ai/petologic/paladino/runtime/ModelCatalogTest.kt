package ai.petologic.paladino.runtime

import ai.petologic.core.ModelCatalog
import org.junit.Test
import org.junit.Assert.*

class ModelCatalogTest {
 @Test fun ids_and_filenames_are_unique(){
  assertEquals(ModelCatalog.artifacts.size,ModelCatalog.artifacts.map{it.id}.distinct().size)
  assertEquals(ModelCatalog.artifacts.size,ModelCatalog.artifacts.map{it.filename}.distinct().size)
 }
 @Test fun main_brain_is_the_qad_quant(){
  val lfm350=ModelCatalog.get("lfm350")
  assertEquals("QAD-Q4_0",lfm350.quantization)
  assertEquals("LFM2.5-350M-QAD-Q4_0.gguf",lfm350.filename)
  assertEquals(219312832L,lfm350.size)
 }
 @Test fun qad_swap_covers_every_generative_family(){
  // 350M main, 2.6B big, 230M sub-agent: all QAD; VL has no QAD quant (stays Q4_0, vision pending).
  assertEquals("QAD-Q4_0",ModelCatalog.get("lfm26-qad").quantization)
  assertEquals("QAD-Q4_0",ModelCatalog.get("lfm230-qad").quantization)
  assertTrue(ModelCatalog.get("lfm25vl-450m").visionPending)
  assertTrue(ModelCatalog.get("lfm25vl-450m-mmproj").visionPending)
 }
 @Test fun selectable_excludes_encoder_and_vision(){
  val ids=ModelCatalog.selectable().map{it.id}
  assertEquals(listOf("lfm350","lfm26-qad","lfm230-qad"),ids)
 }
 @Test fun sub_agent_fallback_and_import_match_still_resolve(){
  assertEquals("lfm230-qad",SubAgent.modelId(setOf("lfm230-qad","lfm350")))
  assertEquals("lfm350",SubAgent.modelId(emptySet()))
  assertNotNull(ModelCatalog.match(149081056,"e75f83268de11b2a1bcfab5f3b5c5c0c97569ddbbc0990aad88437e45b8ba292"))
 }
}
