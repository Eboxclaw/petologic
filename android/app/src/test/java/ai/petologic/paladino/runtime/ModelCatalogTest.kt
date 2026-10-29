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
 @Test fun main_brain_is_the_vl_quant(){
  val speaker=ModelCatalog.get(ModelCatalog.SPEAKER_ID)
  assertEquals("Q4_0",speaker.quantization)
  assertEquals("LFM2.5-VL-450M-Q4_0.gguf",speaker.filename)
  assertEquals(219311264L,speaker.size)
 }
 @Test fun local_catalog_has_speakers_router_projectors_and_vectors(){
  assertEquals(7,ModelCatalog.artifacts.size)
  assertEquals(ModelKind.VISION_PROJECTOR,ModelCatalog.get(ModelCatalog.VISION_PROJECTOR_ID).kind)
  val vectors=ModelCatalog.get(ModelCatalog.EMBEDDING_ID)
  assertEquals(ModelKind.EMBEDDING,vectors.kind)
  assertEquals("Q4_K_M",vectors.quantization)
  assertEquals(229311232L,vectors.size)
 }
 @Test fun selectable_contains_the_default_speaker_first_then_lfm3(){
  val ids=ModelCatalog.selectable().map{it.id}
  assertEquals(listOf(ModelCatalog.SPEAKER_ID,ModelCatalog.OMNI_SPEAKER_ID),ids)
 }
 @Test fun vector_import_match_resolves(){
  assertEquals(ModelCatalog.SPEAKER_ID,SubAgent.modelId(emptySet()))
  assertEquals(ModelCatalog.EMBEDDING_ID,ModelCatalog.match(229311232,"4d7aa9dc6406a10fc3dec2c11f8f06781af063bf49211b8e4132e9b876d3f32a")?.id)
 }
 @Test fun projector_is_optional_and_not_selectable(){
  val projector=ModelCatalog.get(ModelCatalog.VISION_PROJECTOR_ID)
  assertEquals(102815168L,projector.size)
  assertTrue(ModelCatalog.optionalComponents().contains(projector))
  assertFalse(ModelCatalog.selectable().contains(projector))
 }
 @Test fun lfm3_and_d1_artifacts_are_pinned(){
  val decision=ModelCatalog.decision()
  assertEquals(ModelKind.DECISION,decision.kind)
  assertEquals("d1-2B-Q4_K_M.gguf",decision.filename)
  assertEquals(1099629888L,decision.size)
  assertEquals(1049617920L,ModelCatalog.get(ModelCatalog.OMNI_SPEAKER_ID).size)
  assertEquals(540892768L,ModelCatalog.get(ModelCatalog.OMNI_PROJECTOR_ID).size)
  assertFalse(ModelCatalog.selectable().any{it.kind!=ModelKind.GENERATIVE})
  assertFalse(ModelCatalog.optionalComponents().any{it.kind!=ModelKind.VISION_PROJECTOR})
 }
}
