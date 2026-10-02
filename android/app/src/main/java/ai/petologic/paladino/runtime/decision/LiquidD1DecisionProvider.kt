package ai.petologic.paladino.runtime.decision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Hosted Liquid d1 adapter.
 *
 * Never enables itself. The caller must explicitly allow remote decisions and
 * provide a key. This keeps Petologic local-first and avoids silently exporting
 * user text just to route a turn.
 */
class LiquidD1DecisionProvider(
 private val apiKey:()->String?,
 private val enabled:()->Boolean,
 private val client:OkHttpClient=OkHttpClient()
):DecisionProvider {
 override suspend fun decide(input:DecisionInput):RouteDecision? {
  if(!enabled()||!input.remoteDecisionAllowed||!input.options.network)return null
  val key=apiKey()?.takeIf{it.startsWith("liquid_")}?:return null

  return withContext(Dispatchers.IO){
   val routeCriteria=JSONObject()
    .put("chat_230m","Short conversation or simple answer that does not require a tool.")
    .put("router_350m","Local language understanding, argument extraction, tool routing, or a harder local answer.")
    .put("cloud","Request requires difficult reasoning, research, or capability beyond the local models.")

   val questions=JSONObject()
    .put("route",JSONObject()
     .put("type","choice")
     .put("instructions","Choose the smallest safe execution tier for this request. Prefer local execution when it is sufficient.")
     .put("criteria",routeCriteria))
    .put("needs_tool",JSONObject()
     .put("type","noul")
     .put("instructions","Does fulfilling this request require using an app capability or tool rather than only answering in text?"))

   val body=JSONObject()
    .put("model","d1:free")
    .put("state",JSONObject()
     .put("request",input.text)
     .put("mode",input.mode.name)
     .put("tool_calls_enabled",input.options.toolCalls)
     .put("network_enabled",input.options.network)
     .toString())
    .put("questions",questions)
    .toString()

   val request=Request.Builder()
    .url(ENDPOINT)
    .header("Authorization","Bearer $key")
    .header("Content-Type","application/json")
    .post(body.toRequestBody(JSON))
    .build()

   client.newCall(request).execute().use{response->
    if(!response.isSuccessful)return@withContext null
    val root=JSONObject(response.body.string())
    val answers=root.optJSONObject("answers")?:return@withContext null
    val route=answers.optJSONObject("route")?:return@withContext null
    val choice=route.optString("choice")
    val confidence=route.optDouble("confidence",0.0)
    val needsTool=answers.optJSONObject("needs_tool")?.optDouble("noul",0.0)?.let{it>=0.5}?:false

    val target=when(choice){
     "chat_230m"->DecisionTarget.CHAT_230M
     "router_350m"->DecisionTarget.ROUTER_350M
     "cloud"->DecisionTarget.CLOUD
     else->return@withContext null
    }
    val model=when(target){
     DecisionTarget.CHAT_230M->preferred(input.installedModels,"lfm230-qad","lfm350")
     DecisionTarget.ROUTER_350M->preferred(input.installedModels,"lfm350","lfm230-qad")
     else->null
    }
    RouteDecision(target,model,confidence,needsTool,DecisionSource.D1_REMOTE,"Liquid d1 bounded decision.")
   }
  }
 }

 private fun preferred(installed:Set<String>,vararg ids:String)=ids.firstOrNull{it in installed}

 companion object {
  const val ENDPOINT="https://api.liquid.ai/decisions/v1/systemone"
  private val JSON="application/json; charset=utf-8".toMediaType()
 }
}
