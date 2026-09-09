package ai.petologic.paladino.runtime

/** Fixed destinations: a model or imported role cannot change the credential recipient. */
enum class CloudProvider(val id:String,val label:String,val endpoint:String,val keysUrl:String){
 OPENROUTER("openrouter","OpenRouter","https://openrouter.ai/api/v1/chat/completions","https://openrouter.ai/settings/keys"),
 OPENAI("openai","OpenAI","https://api.openai.com/v1/chat/completions","https://platform.openai.com/api-keys"),
 ZAI("zai","Z.ai","https://api.z.ai/api/paas/v4/chat/completions","https://z.ai/manage-apikey/apikey-list");
 fun validModel(value:String)=value.length in 1..200&&value.matches(if(this==OPENROUTER)Regex("[A-Za-z0-9._:-]+/[A-Za-z0-9._:/-]+")else Regex("[A-Za-z0-9][A-Za-z0-9._:-]*"))
 companion object{fun fromId(id:String)=entries.firstOrNull{it.id==id}?:error("Unknown cloud provider")}
}
