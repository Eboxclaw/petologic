#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <algorithm>
#include <chrono>

// All model ownership is serialized. The atomic cancellation flag is independent
// so the UI never waits for generation to release the model mutex to cancel.
static std::mutex model_mutex;
static std::atomic<bool> cancelled{false};
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static void fail(JNIEnv *env, const char *msg) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg); }
static std::string utf8(JNIEnv *env, jbyteArray data) {
 std::string s(env->GetArrayLength(data), '\0');
 env->GetByteArrayRegion(data, 0, s.size(), reinterpret_cast<jbyte *>(s.data())); return s;
}
static bool abort_generation(void *) { return cancelled.load(); }
extern "C" JNIEXPORT void JNICALL Java_ai_petologic_paladino_runtime_NativeLfm_load(JNIEnv *env,jobject,jstring path,jint contextSize,jint threads,jint batchSize,jint microBatch,jboolean mmap) {
 std::lock_guard<std::mutex> lock(model_mutex);
 if (model) return;
 __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","load begin context=%d threads=%d batch=%d micro=%d",contextSize,threads,batchSize,microBatch);
 llama_backend_init();
 auto params=llama_model_default_params(); params.n_gpu_layers=0; params.load_mode=mmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;
 const char *p=env->GetStringUTFChars(path,nullptr);
 model=llama_model_load_from_file(p,params); env->ReleaseStringUTFChars(path,p);
 if (!model) { fail(env,"Unable to load the verified LFM model."); return; }
 auto cp=llama_context_default_params(); cp.n_ctx=contextSize; cp.n_batch=batchSize; cp.n_ubatch=microBatch; cp.n_threads=threads; cp.n_threads_batch=threads;
 cp.abort_callback=abort_generation;
 __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","weights loaded; allocating context");
 ctx=llama_init_from_model(model,cp);
 __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","context allocation completed");
 if (!ctx) { llama_model_free(model);model=nullptr;fail(env,"Unable to allocate local model context."); }
}
extern "C" JNIEXPORT void JNICALL Java_ai_petologic_paladino_runtime_NativeLfm_cancel(JNIEnv *,jobject) { cancelled.store(true); __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","cancel requested"); }
extern "C" JNIEXPORT void JNICALL Java_ai_petologic_paladino_runtime_NativeLfm_prepare(JNIEnv *,jobject) { cancelled.store(false); }
extern "C" JNIEXPORT void JNICALL Java_ai_petologic_paladino_runtime_NativeLfm_unload(JNIEnv *,jobject) {
 std::lock_guard<std::mutex> lock(model_mutex); if(ctx) llama_free(ctx);if(model)llama_model_free(model);ctx=nullptr;model=nullptr;
}
extern "C" JNIEXPORT jlongArray JNICALL Java_ai_petologic_paladino_runtime_NativeLfm_generate(JNIEnv *env,jobject,jbyteArray systemBytes,jbyteArray userBytes,jobjectArray roles,jobjectArray contents,jobject callback,jint maxOutput,jfloat temperature,jint topK,jfloat topP,jfloat repeatPenalty,jint seed) {
 std::lock_guard<std::mutex> lock(model_mutex);
 if(!ctx) { fail(env,"Local model is not loaded.");return nullptr; }
 const auto system=utf8(env,systemBytes), user=utf8(env,userBytes);
 // Use the model's official chat template, never concatenate unescaped role markers.
 std::vector<std::string> roleStrings, contentStrings;
 const int count=env->GetArrayLength(roles);
 if(count!=env->GetArrayLength(contents)||count>64){fail(env,"Invalid conversation messages.");return nullptr;}
 for(int i=0;i<count;i++){
  auto role=(jstring)env->GetObjectArrayElement(roles,i);
  const char *r=env->GetStringUTFChars(role,nullptr);roleStrings.emplace_back(r);env->ReleaseStringUTFChars(role,r);env->DeleteLocalRef(role);
  if(roleStrings.back()!="user"&&roleStrings.back()!="assistant"&&roleStrings.back()!="tool"){fail(env,"Invalid conversation role.");return nullptr;}
  auto content=(jbyteArray)env->GetObjectArrayElement(contents,i);contentStrings.push_back(utf8(env,content));env->DeleteLocalRef(content);
 }
 std::vector<llama_chat_message> messages={{"system",system.c_str()}};
 if(count==0)messages.push_back({"user",user.c_str()});
 else for(int i=0;i<count;i++)messages.push_back({roleStrings[i].c_str(),contentStrings[i].c_str()});
 const char *tmpl=llama_model_chat_template(model,nullptr);
 int n=llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,nullptr,0);
 if(n<0) { fail(env,"Unsupported LFM chat template.");return nullptr; }
 std::vector<char> formatted(n+1);
 llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,formatted.data(),formatted.size());
 const auto *vocab=llama_model_get_vocab(model);
 int nt=-llama_tokenize(vocab,formatted.data(),n,nullptr,0,true,true);
 if(nt<=0 || nt+maxOutput+16>static_cast<int>(llama_n_ctx(ctx))) { fail(env,"This message exceeds the local context. Please shorten it.");return nullptr; }
 std::vector<llama_token> tokens(nt);
 llama_tokenize(vocab,formatted.data(),n,tokens.data(),nt,true,true);
 llama_memory_clear(llama_get_memory(ctx),true);
 __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","prefill begin tokens=%d",nt);
 const auto prefillStart=std::chrono::steady_clock::now();
 const int batchSize=llama_n_batch(ctx);
 for(int offset=0;offset<nt;offset+=batchSize) {
  if(cancelled.load())return nullptr;
  auto batch=llama_batch_get_one(tokens.data()+offset,std::min(batchSize,nt-offset));
  if(llama_decode(ctx,batch)!=0) { if(!cancelled.load())fail(env,"Local prompt evaluation failed.");return nullptr; }
 }
 __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","prefill complete");
 const auto decodeStart=std::chrono::steady_clock::now();
 int generated=0;
 auto *sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 llama_sampler_chain_add(sampler,llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,repeatPenalty,0,0));
 if(temperature<=0)llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
 else{llama_sampler_chain_add(sampler,llama_sampler_init_top_k(topK));llama_sampler_chain_add(sampler,llama_sampler_init_top_p(topP,1));llama_sampler_chain_add(sampler,llama_sampler_init_temp(temperature));llama_sampler_chain_add(sampler,llama_sampler_init_dist(seed));}
 auto method=env->GetMethodID(env->GetObjectClass(callback),"onToken","([B)V");
 for(int i=0;i<maxOutput && !cancelled.load();i++) {
  auto token=llama_sampler_sample(sampler,ctx,-1);
  if(llama_vocab_is_eog(vocab,token))break;
  generated++;
  char buf[256];int len=llama_token_to_piece(vocab,token,buf,sizeof(buf),0,true);
  std::vector<char> large;
  const char *piece=buf;
  if(len<0){large.resize(-len);len=llama_token_to_piece(vocab,token,large.data(),large.size(),0,true);piece=large.data();}
  if(len>0){auto bytes=env->NewByteArray(len);env->SetByteArrayRegion(bytes,0,len,reinterpret_cast<const jbyte *>(piece));env->CallVoidMethod(callback,method,bytes);env->DeleteLocalRef(bytes);}
  if(env->ExceptionCheck())break;
  if(llama_decode(ctx,llama_batch_get_one(&token,1))!=0){if(!cancelled.load())fail(env,"Local generation failed.");break;}
 }
 __android_log_print(ANDROID_LOG_INFO,"PaladinoNative","decode complete tokens=%d",generated);
 llama_sampler_free(sampler);
 auto end=std::chrono::steady_clock::now();
 jlong stats[]={nt,generated,std::chrono::duration_cast<std::chrono::microseconds>(decodeStart-prefillStart).count(),std::chrono::duration_cast<std::chrono::microseconds>(end-decodeStart).count()};
 auto result=env->NewLongArray(4);env->SetLongArrayRegion(result,0,4,stats);return result;
}
