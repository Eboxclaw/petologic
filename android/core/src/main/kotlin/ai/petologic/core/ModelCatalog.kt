package ai.petologic.core

/** [visionPending] artifacts are registered and pinned but hidden until the app supports image input. */
data class ModelArtifact(val id:String,val publisher:String,val model:String,val quantization:String,val filename:String,val size:Long,val sha256:String,val url:String,val visionPending:Boolean=false)
object ModelCatalog {
 val artifacts=listOf(
  // Main brain: Liquid's QAD-Q4_0 (distilled) — user decision 2026-09-12; QAD ≈ Q5_K_M quality, faster decode.
  ModelArtifact("lfm350","LiquidAI","LFM2.5-350M","QAD-Q4_0","LFM2.5-350M-QAD-Q4_0.gguf",219312832,"3d10b6ab8fc91a919534b9558e266255aca0bbc7f6d015963599aa9e74e05b1d","https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/resolve/9969000761ce34de907bf20017cbfc3d52d6eaf9/LFM2.5-350M-QAD-Q4_0.gguf"),
  ModelArtifact("lfm26-qad","LiquidAI","LFM2.5-2.6B","QAD-Q4_0","LFM2.5-2.6B-QAD-Q4_0.gguf",1593894944,"a247afd6414918eac8e520a9e6137dc271235461ecbe1180462221d5b8d40b03","https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF/resolve/84022ce711b28455e8c4fc364ce68c00cf995875/LFM2.5-2.6B-QAD-Q4_0.gguf"),
  // Sub-agent brain (plan 15): small sibling, QAD quant, runs as 350M turns until downloaded.
  ModelArtifact("lfm230-qad","LiquidAI","LFM2.5-230M","QAD-Q4_0","LFM2.5-230M-QAD-Q4_0.gguf",149081056,"e75f83268de11b2a1bcfab5f3b5c5c0c97569ddbbc0990aad88437e45b8ba292","https://huggingface.co/LiquidAI/LFM2.5-230M-GGUF/resolve/cdf97bd8205908758f44aec508d68ac1aef98f5c/LFM2.5-230M-QAD-Q4_0.gguf"),
  // Vision-language, registered for the upcoming native multimodal work; needs main gguf + mmproj projector.
  ModelArtifact("lfm25vl-450m","LiquidAI","LFM2.5-VL-450M","Q4_0","LFM2.5-VL-450M-Q4_0.gguf",219311264,"6d2757dd0f0b98aea7dc90477bb5b3a0df1089be85ef92943f8cecb05121ccbf","https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/1abed04b6fe71314d8c446a1371c03d7c332266d/LFM2.5-VL-450M-Q4_0.gguf",visionPending=true),
  ModelArtifact("lfm25vl-450m-mmproj","LiquidAI","LFM2.5-VL-450M","mmproj-Q8_0","mmproj-LFM2.5-VL-450m-Q8_0.gguf",102815168,"ebfc428baa37efad8bae93864f914b2634a09009f91ad59f974fe1a1565d8561","https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/1abed04b6fe71314d8c446a1371c03d7c332266d/mmproj-LFM2.5-VL-450m-Q8_0.gguf",visionPending=true),
  ModelArtifact("minilm","sentence-transformers","all-MiniLM-L6-v2","qint8-arm64","minilm.onnx",23026053,"4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474","https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/1110a243fdf4706b3f48f1d95db1a4f5529b4d41/onnx/model_qint8_arm64.onnx")
 )
 /** Generative Tiny models a session can select (excludes the encoder and vision-pending entries). */
 fun selectable()=artifacts.filter{!it.visionPending&&it.id!="minilm"}
 fun get(id:String)=artifacts.single{it.id==id}
 fun match(size:Long,hash:String)=artifacts.find{it.size==size&&it.sha256==hash}
}
