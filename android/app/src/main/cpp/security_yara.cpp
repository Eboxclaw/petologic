#include <jni.h>
#include <yara.h>
#include <string>

static const char* reject_include(const char*, const char*, const char*, void*) { return nullptr; }
static int callback(YR_SCAN_CONTEXT*, int message, void* data, void* user) {
 if(message==CALLBACK_MSG_RULE_MATCHING) {
  auto* output=static_cast<std::string*>(user);
  if(output->size()>8192)return CALLBACK_ABORT;
  if(!output->empty())*output += "\n";
  *output += static_cast<YR_RULE*>(data)->identifier;
 }
 return CALLBACK_CONTINUE;
}
extern "C" JNIEXPORT jstring JNICALL
Java_ai_petologic_skills_security_guard_NativeRules_scanNative(JNIEnv* env,jobject,jstring source,jbyteArray bytes) {
 if(env->GetArrayLength(bytes)>8*1024*1024)return env->NewStringUTF("ERROR:size");
 if(yr_initialize()!=ERROR_SUCCESS)return env->NewStringUTF("ERROR:init");
 YR_COMPILER* compiler=nullptr;YR_RULES* rules=nullptr;std::string matches;
 int result=yr_compiler_create(&compiler);
 if(result==ERROR_SUCCESS){
  yr_compiler_set_include_callback(compiler,reject_include,nullptr,nullptr);
  const char* text=env->GetStringUTFChars(source,nullptr);
  if(!text){yr_compiler_destroy(compiler);yr_finalize();return nullptr;}
  result=yr_compiler_add_string(compiler,text,nullptr);
  env->ReleaseStringUTFChars(source,text);
  if(result==0)result=yr_compiler_get_rules(compiler,&rules);
  if(result==0){
   jbyte* data=env->GetByteArrayElements(bytes,nullptr);
   if(data){result=yr_rules_scan_mem(rules,reinterpret_cast<uint8_t*>(data),env->GetArrayLength(bytes),SCAN_FLAGS_FAST_MODE,callback,&matches,2);
    env->ReleaseByteArrayElements(bytes,data,JNI_ABORT);}
   else result=ERROR_INSUFFICIENT_MEMORY;
  }
 }
 if(rules)yr_rules_destroy(rules);
 if(compiler)yr_compiler_destroy(compiler);
 yr_finalize();
 if(result!=0)matches="ERROR:"+std::to_string(result);
 return env->NewStringUTF(matches.c_str());
}
