package ai.petologic.paladino.runtime

import ai.petologic.core.ModelCatalog
import ai.petologic.core.ModelKind
import org.junit.Test
import org.junit.Assert.*

class ModelCatalogTest {
 @Test fun ids_and_filenames_are_unique(){
  assertEquals(ModelCatalog.artifacts.size,ModelCatalog.artifacts.map{it.id}.distinct().size)
  assertEquals(ModelCatalog.artifacts.size,ModelCatalog.artifacts.map{it.filename}.distinct().size)
 }
 @Test fun main_brain_is_the_lfm3_omni_quant(){
  val speaker=ModelCatalog.get(ModelCatalog.SPEAKER_ID)
  assertEquals("LFM3-Omni-2B",speaker.model)
  assertEquals("Q4_0",speaker.quantization)
  assertEquals("LFM3-Omni-2B-Q4_0.gguf",speaker.filename)
  assertEquals(1049617920L,speaker.size)
 }
 @Test fun local_catalog_has_speaker_router_and_projectors(){
  assertEquals(4,ModelCatalog.artifacts.size)
  assertEquals(ModelKind.GENERATIVE,ModelCatalog.get(ModelCatalog.SPEAKER_ID).kind)
  assertEquals(ModelKind.DECISION,ModelCatalog.decision().kind)
  assertTrue(ModelCatalog.optionalComponents().contains(ModelCatalog.get(ModelCatalog.VISION_PROJECTOR_ID)))
 }
 @Test fun selectable_contains_only_the_lfm3_speaker(){
  val ids=ModelCatalog.selectable().map{it.id}
  assertEquals(listOf(ModelCatalog.SPEAKER_ID),ids)
 }
 @Test fun vector_import_match_resolves(){
  assertEquals(ModelCatalog.SPEAKER_ID,SubAgent.modelId(emptySet()))
  assertEquals(ModelCatalog.SPEAKER_ID,ModelCatalog.match(1049617920,"6d2273a383b92dd7c5db0eb4c8dfeae13e805b485a6621c4b391ae179c2b5f28")?.id)
 }
 @Test fun projector_is_optional_and_not_selectable(){
  val projector=ModelCatalog.get(ModelCatalog.VISION_PROJECTOR_ID)
  assertEquals(540892768L,projector.size)
  assertTrue(ModelCatalog.optionalComponents().contains(projector))
  assertFalse(ModelCatalog.selectable().contains(projector))
 }
 @Test fun the_speaker_pairs_with_its_own_projector(){
  assertEquals(ModelCatalog.VISION_PROJECTOR_ID,ModelCatalog.projectorFor(ModelCatalog.SPEAKER_ID).id)
  assertEquals(ModelCatalog.SPEAKER_ID,ModelCatalog.visualSpeakerOrDefault(ModelCatalog.SPEAKER_ID))
  assertEquals(ModelCatalog.SPEAKER_ID,ModelCatalog.visualSpeakerOrDefault(ModelCatalog.DECISION_ID))
 }
 @Test fun d1_artifacts_are_pinned(){
  val decision=ModelCatalog.decision()
  assertEquals("d1-2B-Q4_K_M.gguf",decision.filename)
  assertEquals(1099629888L,decision.size)
  assertEquals(203031584L,ModelCatalog.get(ModelCatalog.DECISION_PROJECTOR_ID).size)
  assertFalse(ModelCatalog.selectable().any{it.kind!=ModelKind.GENERATIVE})
  assertFalse(ModelCatalog.optionalComponents().any{it.kind!=ModelKind.VISION_PROJECTOR})
 }
}
