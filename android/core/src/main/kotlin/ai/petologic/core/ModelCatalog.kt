package ai.petologic.core

data class ModelArtifact(val id:String,val publisher:String,val model:String,val quantization:String,val filename:String,val size:Long,val sha256:String,val url:String)
object ModelCatalog {
 val artifacts=listOf(
  ModelArtifact("lfm350","LiquidAI","LFM2.5-350M","Q4_K_M","LFM2.5-350M-Q4_K_M.gguf",229312224,"7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4","https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/resolve/9969000761ce34de907bf20017cbfc3d52d6eaf9/LFM2.5-350M-Q4_K_M.gguf"),
  ModelArtifact("lfm26-q4km","LiquidAI","LFM2.5-2.6B","Q4_K_M","LFM2.5-2.6B-Q4_K_M.gguf",1674455040,"02a8b7e17487d326e46d68ce0ba24211e1b80a14c4cd0597fa73c1cd697f52ed","https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF/resolve/84022ce711b28455e8c4fc364ce68c00cf995875/LFM2.5-2.6B-Q4_K_M.gguf"),
  ModelArtifact("lfm26-qad","LiquidAI","LFM2.5-2.6B","QAD-Q4_0","LFM2.5-2.6B-QAD-Q4_0.gguf",1593894944,"a247afd6414918eac8e520a9e6137dc271235461ecbe1180462221d5b8d40b03","https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF/resolve/84022ce711b28455e8c4fc364ce68c00cf995875/LFM2.5-2.6B-QAD-Q4_0.gguf"),
  ModelArtifact("minilm","sentence-transformers","all-MiniLM-L6-v2","qint8-arm64","minilm.onnx",23026053,"4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474","https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/1110a243fdf4706b3f48f1d95db1a4f5529b4d41/onnx/model_qint8_arm64.onnx")
 )
 fun get(id:String)=artifacts.single{it.id==id}
 fun match(size:Long,hash:String)=artifacts.find{it.size==size&&it.sha256==hash}
}
