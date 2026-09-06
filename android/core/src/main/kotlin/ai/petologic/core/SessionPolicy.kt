package ai.petologic.core
import kotlinx.serialization.Serializable

enum class Capability { MEMORY_READ, MEMORY_WRITE, TOOL_CALLS, NETWORK, FILES, WEB_SEARCH, MCP, SKILLS, VISION, BACKGROUND, NOTIFICATIONS, APP_ACCESS }
@Serializable data class SessionOptions(
 val memoryRead:Boolean=true,val memoryWrite:Boolean=true,val toolCalls:Boolean=true,val network:Boolean=true,
 val background:Boolean=false,val instructions:String="",
 val contextTokens:Int=4096,val outputTokens:Int=512,val toolReserve:Int=512,
 val compressionPercent:Int=70,val emergencyPercent:Int=90,
 val maxHops:Int=4,val maxToolCalls:Int=3,val maxRetries:Int=1,val hopTimeoutSeconds:Int=60,val totalTimeoutSeconds:Int=180,
 val temperature:Float=0.1f,val topK:Int=50,val topP:Float=0.9f,val repeatPenalty:Float=1.05f,val seed:Int=42,
 val threads:Int=4,val batch:Int=512,val microBatch:Int=256,val mmap:Boolean=true
){
 fun validate():SessionOptions=apply{
  require(contextTokens in 1024..8192&&outputTokens in 16..1024&&toolReserve in 128..2048)
  require(outputTokens+toolReserve+256<contextTokens)
  require(compressionPercent in 40..85&&emergencyPercent in 86..95&&compressionPercent<emergencyPercent)
  require(maxHops in 1..12&&maxToolCalls in 0..12&&maxRetries in 0..2)
  require(hopTimeoutSeconds in 5..180&&totalTimeoutSeconds in hopTimeoutSeconds..600)
  require(temperature in 0f..2f&&topK in 1..100&&topP in 0.05f..1f&&repeatPenalty in 1f..2f)
  require(threads in 1..8&&batch in 32..1024&&microBatch in 32..batch&&instructions.length<=2000)
 }
 fun grants():Set<Capability> = buildSet{
  if(memoryRead)add(Capability.MEMORY_READ);if(memoryWrite)add(Capability.MEMORY_WRITE)
  if(toolCalls)add(Capability.TOOL_CALLS);if(network)add(Capability.NETWORK)
  if(background)add(Capability.BACKGROUND)
 }
}
fun effectiveCapabilities(global:Set<Capability>,agent:Set<Capability>,session:Set<Capability>):Set<Capability> = global intersect agent intersect session
class LoopBudget(private val options:SessionOptions){
 var hops=0;private set
 var calls=0;private set
 private val seen=mutableSetOf<String>()
 fun hop(){check(++hops<=options.maxHops){"Maximum agent hops reached. Start a new request or raise the limit."}}
 fun tool(name:String,args:String){
  check(++calls<=options.maxToolCalls){"Maximum tool calls reached."}
  check(seen.add(digest(name+"\u0000"+args))){"Repeated tool call stopped to prevent a loop."}
 }
}
