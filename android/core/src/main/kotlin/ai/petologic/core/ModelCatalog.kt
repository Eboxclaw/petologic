package ai.petologic.core

enum class ModelKind { GENERATIVE, VISION_PROJECTOR, EMBEDDING }

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
