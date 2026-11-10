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
 val projectorId:String?=null,
)

/**
 * Pinned Liquid artifacts. LFM3-Omni-2B is the one local speaker: it handles chat and, with its
 * own projector, visual inspection. d1-2B is the decision router (never a chat model). The
 * retired LFM2.5-VL-450M speaker/pair and the LFM2.5-Embedding-350M vector model are removed —
 * semantic vector retrieval stays dormant until a future Liquid embedding model is pinned.
 */
object ModelCatalog {
 const val SPEAKER_ID="lfm3-omni-2b"
 const val VISION_PROJECTOR_ID="lfm3-omni-2b-mmproj"
 const val DECISION_ID="d1-2b"
 const val DECISION_PROJECTOR_ID="d1-2b-mmproj"
 val artifacts=listOf(
  ModelArtifact(
   SPEAKER_ID,"LiquidAI","LFM3-Omni-2B","Q4_0","LFM3-Omni-2B-Q4_0.gguf",
   1049617920,"6d2273a383b92dd7c5db0eb4c8dfeae13e805b485a6621c4b391ae179c2b5f28",
   "https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF/resolve/64660117852a2054cf1f666f47eeb910e2204e4d/LFM3-Omni-2B-Q4_0.gguf",
   ModelKind.GENERATIVE,
   VISION_PROJECTOR_ID,
  ),
  ModelArtifact(
   VISION_PROJECTOR_ID,"LiquidAI","LFM3-Omni-2B vision+audio","mmproj-Q8_0","mmproj-LFM3-Omni-2B-Q8_0.gguf",
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
 fun selectable()=artifacts.filter{it.kind==ModelKind.GENERATIVE}
 fun optionalComponents()=artifacts.filter{it.kind==ModelKind.VISION_PROJECTOR}
 fun decision()=artifacts.single{it.kind==ModelKind.DECISION}
 /** The projector paired with a speaker, defaulting to the speaker's own for unknown ids. */
 fun projectorFor(speakerId:String):ModelArtifact=
  artifacts.single{it.id==(get(speakerId).projectorId?:VISION_PROJECTOR_ID)}
 fun visualSpeakerOrDefault(sessionSpeakerId:String):String=
  if(get(sessionSpeakerId).projectorId!=null)sessionSpeakerId else SPEAKER_ID
 fun get(id:String)=artifacts.single{it.id==id}
 fun match(size:Long,hash:String)=artifacts.find{it.size==size&&it.sha256==hash}
}
