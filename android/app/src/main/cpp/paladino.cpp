#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <mtmd.h>
#include <mtmd-helper.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <cmath>

// All model ownership is serialized. The atomic cancellation flag is independent
// so the UI never waits for generation to release the model mutex to cancel.
static std::mutex model_mutex;
static std::once_flag backend_once;
static std::atomic<bool> cancelled{false};
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static std::mutex vector_mutex;
static llama_model *vector_model = nullptr;
static llama_context *vector_ctx = nullptr;
static void ensure_backend() { std::call_once(backend_once, [] { llama_backend_init(); }); }
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
 ensure_backend();
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

extern "C" JNIEXPORT jlongArray JNICALL Java_ai_petologic_paladino_runtime_NativeLfm_generateVisual(JNIEnv *env,jobject,jstring projectorPath,jbyteArray systemBytes,jbyteArray userBytes,jbyteArray rgbBytes,jint width,jint height,jobject callback,jint maxOutput,jfloat temperature,jint topK,jfloat topP,jfloat repeatPenalty,jint seed,jint threads) {
 std::lock_guard<std::mutex> lock(model_mutex);
 if(!ctx||!model){fail(env,"Local model is not loaded.");return nullptr;}
 if(width<=0||height<=0||width>512||height>512||static_cast<int64_t>(width)*height*3!=env->GetArrayLength(rgbBytes)){fail(env,"Invalid visual input.");return nullptr;}
 const auto system=utf8(env,systemBytes),user=utf8(env,userBytes),rgb=utf8(env,rgbBytes);
 const char *projector=env->GetStringUTFChars(projectorPath,nullptr);
 auto mp=mtmd_context_params_default();mp.use_gpu=false;mp.print_timings=false;mp.n_threads=std::max(1,static_cast<int>(threads));mp.warmup=false;mp.image_min_tokens=32;mp.image_max_tokens=256;
 auto *multimodal=mtmd_init_from_file(projector,model,mp);env->ReleaseStringUTFChars(projectorPath,projector);
 if(!multimodal){fail(env,"Unable to load the verified vision projector.");return nullptr;}
 if(!mtmd_support_vision(multimodal)){mtmd_free(multimodal);fail(env,"The installed projector does not support vision.");return nullptr;}
 auto *bitmap=mtmd_bitmap_init(width,height,reinterpret_cast<const unsigned char *>(rgb.data()));
 auto *chunks=mtmd_input_chunks_init();
 if(!bitmap||!chunks){if(bitmap)mtmd_bitmap_free(bitmap);if(chunks)mtmd_input_chunks_free(chunks);mtmd_free(multimodal);fail(env,"Unable to prepare the image.");return nullptr;}
 const std::string marker=mtmd_get_marker(multimodal);
 const std::string visualUser=marker+"\n"+user;
 std::vector<llama_chat_message> messages={{"system",system.c_str()},{"user",visualUser.c_str()}};
 const char *tmpl=llama_model_chat_template(model,nullptr);
 int formattedSize=llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,nullptr,0);
 if(formattedSize<0){mtmd_input_chunks_free(chunks);mtmd_bitmap_free(bitmap);mtmd_free(multimodal);fail(env,"Unsupported vision chat template.");return nullptr;}
 std::vector<char> formattedBuffer(formattedSize+1);
 llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,formattedBuffer.data(),formattedBuffer.size());
 const std::string formatted(formattedBuffer.data(),formattedSize);
 const auto markerPosition=formatted.find(marker);
 if(markerPosition==std::string::npos){mtmd_input_chunks_free(chunks);mtmd_bitmap_free(bitmap);mtmd_free(multimodal);fail(env,"Vision marker was not preserved by the chat template.");return nullptr;}
 const std::string before=formatted.substr(0,markerPosition),after=formatted.substr(markerPosition+marker.size());
 mtmd_input_text beforeText={before.data(),before.size(),false,true};
 mtmd_input_text afterText={after.data(),after.size(),false,true};
 mtmd_input_part beforePart={&beforeText,nullptr};
 mtmd_input_part imagePart={nullptr,bitmap};
 mtmd_input_part afterPart={&afterText,nullptr};
 const mtmd_input_part *parts[]={&beforePart,&imagePart,&afterPart};
 if(mtmd_tokenize_from_parts(multimodal,chunks,parts,3,true)!=0){mtmd_input_chunks_free(chunks);mtmd_bitmap_free(bitmap);mtmd_free(multimodal);fail(env,"Unable to tokenize the visual request.");return nullptr;}
 const size_t promptTokens=mtmd_helper_get_n_tokens(chunks);
 const llama_pos promptPositions=mtmd_helper_get_n_pos(chunks);
 if(promptPositions+maxOutput+16>llama_n_ctx(ctx)){mtmd_input_chunks_free(chunks);mtmd_bitmap_free(bitmap);mtmd_free(multimodal);fail(env,"This image and prompt exceed the local context.");return nullptr;}
 llama_memory_clear(llama_get_memory(ctx),true);
 const auto prefillStart=std::chrono::steady_clock::now();
 llama_pos nPast=0;
 const int evalResult=mtmd_helper_eval_chunks(multimodal,ctx,chunks,0,0,llama_n_batch(ctx),true,&nPast);
 mtmd_input_chunks_free(chunks);mtmd_bitmap_free(bitmap);mtmd_free(multimodal);
 if(evalResult!=0){if(!cancelled.load())fail(env,"Local image evaluation failed.");return nullptr;}
 if(cancelled.load())return nullptr;
 const auto decodeStart=std::chrono::steady_clock::now();
 int generated=0;
 auto *sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 llama_sampler_chain_add(sampler,llama_sampler_init_penalties(llama_vocab_n_tokens(llama_model_get_vocab(model)),64,repeatPenalty,0,0));
 if(temperature<=0)llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
 else{llama_sampler_chain_add(sampler,llama_sampler_init_top_k(topK));llama_sampler_chain_add(sampler,llama_sampler_init_top_p(topP,1));llama_sampler_chain_add(sampler,llama_sampler_init_temp(temperature));llama_sampler_chain_add(sampler,llama_sampler_init_dist(seed));}
 const auto *vocab=llama_model_get_vocab(model);
 auto method=env->GetMethodID(env->GetObjectClass(callback),"onToken","([B)V");
 for(int i=0;i<maxOutput&&!cancelled.load();i++){
  auto token=llama_sampler_sample(sampler,ctx,-1);if(llama_vocab_is_eog(vocab,token))break;generated++;
  char buf[256];int len=llama_token_to_piece(vocab,token,buf,sizeof(buf),0,true);std::vector<char> large;const char *piece=buf;
  if(len<0){large.resize(-len);len=llama_token_to_piece(vocab,token,large.data(),large.size(),0,true);piece=large.data();}
  if(len>0){auto bytes=env->NewByteArray(len);env->SetByteArrayRegion(bytes,0,len,reinterpret_cast<const jbyte *>(piece));env->CallVoidMethod(callback,method,bytes);env->DeleteLocalRef(bytes);}
  if(env->ExceptionCheck())break;
  if(llama_decode(ctx,llama_batch_get_one(&token,1))!=0){if(!cancelled.load())fail(env,"Local visual generation failed.");break;}
 }
 llama_sampler_free(sampler);
 const auto end=std::chrono::steady_clock::now();
 jlong stats[]={static_cast<jlong>(promptTokens),generated,std::chrono::duration_cast<std::chrono::microseconds>(decodeStart-prefillStart).count(),std::chrono::duration_cast<std::chrono::microseconds>(end-decodeStart).count()};
 auto result=env->NewLongArray(4);env->SetLongArrayRegion(result,0,4,stats);return result;
}

extern "C" JNIEXPORT void JNICALL Java_ai_petologic_paladino_runtime_NativeVectorModel_load(JNIEnv *env,jobject,jstring path,jint threads) {
 std::lock_guard<std::mutex> lock(vector_mutex);
 if(vector_model)return;
 ensure_backend();
 auto params=llama_model_default_params();params.n_gpu_layers=0;params.load_mode=LLAMA_LOAD_MODE_MMAP;
 const char *p=env->GetStringUTFChars(path,nullptr);
 vector_model=llama_model_load_from_file(p,params);env->ReleaseStringUTFChars(path,p);
 if(!vector_model){fail(env,"Unable to load the verified vector model.");return;}
 auto cp=llama_context_default_params();cp.n_ctx=512;cp.n_batch=512;cp.n_ubatch=512;cp.n_seq_max=1;cp.n_threads=threads;cp.n_threads_batch=threads;cp.embeddings=true;
 vector_ctx=llama_init_from_model(vector_model,cp);
 if(!vector_ctx){llama_model_free(vector_model);vector_model=nullptr;fail(env,"Unable to allocate vector model context.");}
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_ai_petologic_paladino_runtime_NativeVectorModel_embed(JNIEnv *env,jobject,jbyteArray textBytes) {
 std::lock_guard<std::mutex> lock(vector_mutex);
 if(!vector_ctx){fail(env,"Vector model is not loaded.");return nullptr;}
 const auto text=utf8(env,textBytes);
 const auto *vocab=llama_model_get_vocab(vector_model);
 int count=-llama_tokenize(vocab,text.data(),text.size(),nullptr,0,true,true);
 if(count<=0||count>512){fail(env,"Vector input is empty or exceeds 512 tokens.");return nullptr;}
 std::vector<llama_token> tokens(count);
 if(llama_tokenize(vocab,text.data(),text.size(),tokens.data(),count,true,true)<0){fail(env,"Vector tokenization failed.");return nullptr;}
 llama_memory_clear(llama_get_memory(vector_ctx),true);
 auto batch=llama_batch_init(count,0,1);
 for(int i=0;i<count;i++){
  batch.token[i]=tokens[i];batch.pos[i]=i;batch.n_seq_id[i]=1;batch.seq_id[i][0]=0;batch.logits[i]=true;
 }
 batch.n_tokens=count;
 if(llama_decode(vector_ctx,batch)!=0){llama_batch_free(batch);fail(env,"Vector inference failed.");return nullptr;}
 const float *raw=llama_get_embeddings_seq(vector_ctx,0);
 if(!raw){llama_batch_free(batch);fail(env,"Vector model did not return a pooled embedding.");return nullptr;}
 const int dimensions=llama_model_n_embd_out(vector_model);
 std::vector<float> normalized(dimensions);double squared=0;
 for(int i=0;i<dimensions;i++)squared+=static_cast<double>(raw[i])*raw[i];
 const float norm=std::max(static_cast<float>(std::sqrt(squared)),1e-12f);
 for(int i=0;i<dimensions;i++)normalized[i]=raw[i]/norm;
 llama_batch_free(batch);
 auto result=env->NewFloatArray(dimensions);env->SetFloatArrayRegion(result,0,dimensions,normalized.data());return result;
}

extern "C" JNIEXPORT void JNICALL Java_ai_petologic_paladino_runtime_NativeVectorModel_unload(JNIEnv *,jobject) {
 std::lock_guard<std::mutex> lock(vector_mutex);
 if(vector_ctx)llama_free(vector_ctx);if(vector_model)llama_model_free(vector_model);vector_ctx=nullptr;vector_model=nullptr;
}
