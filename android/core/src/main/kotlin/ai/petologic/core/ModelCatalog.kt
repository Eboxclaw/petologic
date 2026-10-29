package ai.petologic.core

enum class ModelKind { GENERATIVE, VISION_PROJECTOR, EMBEDDING, DECISION }

data class ModelArtifact(
 val id:String,
 val publisher:String,
 val model:String,
 val quantization:String,
 val filename:String,
 val size:Long,
 val sha256:String,
 val url:String,
 val kind:ModelKind,
)

/** One Liquid generative model, its optional vision projector, and one Liquid vector model. */
object ModelCatalog {
 const val SPEAKER_ID="lfm25vl-450m"
 const val VISION_PROJECTOR_ID="lfm25vl-450m-mmproj"
 const val EMBEDDING_ID="lfm25-embedding-350m"
 val artifacts=listOf(
  ModelArtifact(
   SPEAKER_ID,"LiquidAI","LFM2.5-VL-450M","Q4_0","LFM2.5-VL-450M-Q4_0.gguf",
   219311264,"6d2757dd0f0b98aea7dc90477bb5b3a0df1089be85ef92943f8cecb05121ccbf",
   "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/1abed04b6fe71314d8c446a1371c03d7c332266d/LFM2.5-VL-450M-Q4_0.gguf",
   ModelKind.GENERATIVE,
  ),
  ModelArtifact(
   VISION_PROJECTOR_ID,"LiquidAI","LFM2.5-VL-450M vision","mmproj-Q8_0","mmproj-LFM2.5-VL-450m-Q8_0.gguf",
   102815168,"ebfc428baa37efad8bae93864f914b2634a09009f91ad59f974fe1a1565d8561",
   "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/1abed04b6fe71314d8c446a1371c03d7c332266d/mmproj-LFM2.5-VL-450m-Q8_0.gguf",
   ModelKind.VISION_PROJECTOR,
  ),
  ModelArtifact(
   EMBEDDING_ID,"LiquidAI","LFM2.5-Embedding-350M","Q4_K_M","LFM2.5-Embedding-350M-Q4_K_M.gguf",
   229311232,"4d7aa9dc6406a10fc3dec2c11f8f06781af063bf49211b8e4132e9b876d3f32a",
   "https://huggingface.co/LiquidAI/LFM2.5-Embedding-350M-GGUF/resolve/a80de9c5b941d429104f0038292a0ef5a860e486/LFM2.5-Embedding-350M-Q4_K_M.gguf",
   ModelKind.EMBEDDING,
  ),
 )
 fun selectable()=artifacts.filter{it.kind==ModelKind.GENERATIVE}
 fun optionalComponents()=artifacts.filter{it.kind==ModelKind.VISION_PROJECTOR}
 fun get(id:String)=artifacts.single{it.id==id}
 fun match(size:Long,hash:String)=artifacts.find{it.size==size&&it.sha256==hash}
}

/**
 * Optional LFM3 generation pack, pinned but inactive: the pinned llama.cpp revision predates LFM3
 * architecture support (b11960 / v0.8.0), so these artifacts stay out of [ModelCatalog.artifacts]
 * and out of every download and selection path until the native bump lands and is validated.
 * License: LFM Open License v1.0.
 */
object Lfm3Pack {
 const val OMNI_SPEAKER_ID="lfm3-omni-2b"
 const val OMNI_PROJECTOR_ID="lfm3-omni-2b-mmproj"
 const val DECISION_ID="d1-2b"
 const val DECISION_PROJECTOR_ID="d1-2b-mmproj"
 val artifacts=listOf(
  ModelArtifact(
   OMNI_SPEAKER_ID,"LiquidAI","LFM3-Omni-2B","Q4_0","LFM3-Omni-2B-Q4_0.gguf",
   1049617920,"6d2273a383b92dd7c5db0eb4c8dfeae13e805b485a6621c4b391ae179c2b5f28",
   "https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF/resolve/64660117852a2054cf1f666f47eeb910e2204e4d/LFM3-Omni-2B-Q4_0.gguf",
   ModelKind.GENERATIVE,
  ),
  ModelArtifact(
   OMNI_PROJECTOR_ID,"LiquidAI","LFM3-Omni-2B vision+audio","mmproj-Q8_0","mmproj-LFM3-Omni-2B-Q8_0.gguf",
   540892768,"720ff4fc09df0e026c4ec641d0c698bf27b5bcd50561944c88eda5e3dffe1839",
   "https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF/resolve/64660117852a2054cf1f666f47eeb910e2204e4d/mmproj-LFM3-Omni-2B-Q8_0.gguf",
   ModelKind.VISION_PROJECTOR,
  ),
  ModelArtifact(
   DECISION_ID,"LiquidAI","d1-2B","Q4_K_M","d1-2B-Q4_K_M.gguf",
   1099629888,"adca5789ad30ae6027e8e936749bb9f19673097492ab26fa1c5b69643ca50b4b",
   "https://huggingface.co/LiquidAI/d1-2B-GGUF/resolve/e968f6575f0d4fad928312d7af15cad90fd365c0/d1-2B-Q4_K_M.gguf",
   ModelKind.DECISION,
  ),
  ModelArtifact(
   DECISION_PROJECTOR_ID,"LiquidAI","d1-2B vision","mmproj-Q8_0","mmproj-d1-2B-Q8_0.gguf",
   203031584,"62cc6d74fd1e5b1d6b48518566bf4c0cfd2468079a0c096ea323b74eed01e0d1",
   "https://huggingface.co/LiquidAI/d1-2B-GGUF/resolve/e968f6575f0d4fad928312d7af15cad90fd365c0/mmproj-d1-2B-Q8_0.gguf",
   ModelKind.VISION_PROJECTOR,
  ),
 )
 fun get(id:String)=artifacts.single{it.id==id}
 fun decision()=artifacts.single{it.kind==ModelKind.DECISION}
}
