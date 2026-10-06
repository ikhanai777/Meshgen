// JNI bridge between MeshGen (Kotlin) and llama.cpp.
// One Engine = one loaded model + context. The system prompt ("prefix") stays in the KV cache between
// requests (and is saved to disk), so each request only processes its own short suffix.
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include "llama.h"
#include "ggml-backend.h"

#define TAG "meshllm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::string prefix_text;
    int n_prefix = 0;
    int n_batch = 512;
};

std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

std::string jbytes(JNIEnv *env, jbyteArray a) {
    if (!a) return {};
    jsize n = env->GetArrayLength(a);
    std::string out(n, '\0');
    env->GetByteArrayRegion(a, 0, n, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

void throwJava(JNIEnv *env, const std::string &msg) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(cls, msg.c_str());
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text) {
    int n = -llama_tokenize(vocab, text.data(), (int32_t) text.size(), nullptr, 0, false, true);
    std::vector<llama_token> toks(std::max(n, 0));
    if (n > 0) llama_tokenize(vocab, text.data(), (int32_t) text.size(), toks.data(), n, false, true);
    return toks;
}

/** Decodes tokens in batches. Calls progress(done) after each batch; returns false if cancelled or failed. */
template <typename F>
bool decode_all(Engine *e, std::vector<llama_token> &toks, F &&progress) {
    for (size_t i = 0; i < toks.size(); i += e->n_batch) {
        int n = (int) std::min<size_t>(e->n_batch, toks.size() - i);
        llama_batch b = llama_batch_get_one(toks.data() + i, n);
        if (llama_decode(e->ctx, b) != 0) return false;
        if (!progress((int) (i + n))) return false;
    }
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_meshgen_app_llm_LlamaNative_initBackends(JNIEnv *env, jclass, jstring libDir) {
    std::string dir = jstr(env, libDir);
    ggml_backend_load_all_from_path(dir.c_str());
    llama_backend_init();
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) LOGI("backend: %s", ggml_backend_reg_name(ggml_backend_reg_get(i)));
}

JNIEXPORT jlong JNICALL
Java_com_meshgen_app_llm_LlamaNative_load(JNIEnv *env, jclass, jstring path, jint nCtx, jint nThreads) {
    std::string p = jstr(env, path);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(p.c_str(), mp);
    if (!model) { throwJava(env, "Could not load the model file (it may be damaged; try deleting and downloading it again)."); return 0; }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = nCtx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); throwJava(env, "Not enough memory to start the model."); return 0; }
    auto *e = new Engine();
    e->model = model;
    e->ctx = ctx;
    e->vocab = llama_model_get_vocab(model);
    LOGI("loaded %s ctx=%d threads=%d", p.c_str(), nCtx, nThreads);
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT void JNICALL
Java_com_meshgen_app_llm_LlamaNative_free(JNIEnv *, jclass, jlong handle) {
    auto *e = reinterpret_cast<Engine *>(handle);
    if (!e) return;
    llama_free(e->ctx);
    llama_model_free(e->model);
    delete e;
}

/**
 * Generates a reply to prefix + suffix. Text is passed as UTF-8 bytes. The callback receives
 * (phase, done, total): phase 0 = reading the prompt, 1 = writing; it returns false to cancel.
 * Returns the generated UTF-8 bytes, or null when cancelled.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_meshgen_app_llm_LlamaNative_generate(JNIEnv *env, jclass, jlong handle, jbyteArray prefixBytes, jbyteArray suffixBytes,
                                             jstring grammarStr, jint maxTokens, jfloat temperature, jint seed,
                                             jstring cachePath, jobject callback) {
    auto *e = reinterpret_cast<Engine *>(handle);
    jclass cbCls = env->GetObjectClass(callback);
    jmethodID onProgress = env->GetMethodID(cbCls, "onProgress", "(III)Z");
    auto report = [&](int phase, int done, int total) {
        return env->CallBooleanMethod(callback, onProgress, phase, done, total) == JNI_TRUE;
    };

    std::string prefix = jbytes(env, prefixBytes);
    std::string suffix = jbytes(env, suffixBytes);
    std::string grammar = jstr(env, grammarStr);
    std::string cache = jstr(env, cachePath);
    llama_memory_t mem = llama_get_memory(e->ctx);
    const int n_ctx = (int) llama_n_ctx(e->ctx);

    // 1. Make sure the shared prefix is in the KV cache (from memory, from the disk cache, or computed).
    if (e->prefix_text != prefix || e->n_prefix == 0) {
        llama_memory_clear(mem, true);
        e->n_prefix = 0;
        e->prefix_text.clear();
        std::vector<llama_token> toks = tokenize(e->vocab, prefix);
        bool loaded = false;
        if (!cache.empty()) {
            std::vector<llama_token> saved(toks.size() + 16);
            size_t n_saved = 0;
            if (llama_state_seq_load_file(e->ctx, cache.c_str(), 0, saved.data(), saved.size(), &n_saved) > 0 &&
                n_saved == toks.size() && std::equal(toks.begin(), toks.end(), saved.begin())) {
                loaded = true;
                LOGI("prefix restored from cache (%zu tokens)", n_saved);
            } else {
                llama_memory_clear(mem, true);
            }
        }
        if (!loaded) {
            if ((int) toks.size() >= n_ctx) { throwJava(env, "Instructions are longer than the model's memory."); return nullptr; }
            bool ok = decode_all(e, toks, [&](int done) { return report(0, done, (int) toks.size()); });
            if (env->ExceptionCheck()) return nullptr;
            if (!ok) { llama_memory_clear(mem, true); return nullptr; }
            if (!cache.empty()) llama_state_seq_save_file(e->ctx, cache.c_str(), 0, toks.data(), toks.size());
        }
        e->prefix_text = prefix;
        e->n_prefix = (int) toks.size();
    }

    // 2. Drop the previous request and process this one.
    llama_memory_seq_rm(mem, 0, e->n_prefix, -1);
    std::vector<llama_token> stoks = tokenize(e->vocab, suffix);
    if (e->n_prefix + (int) stoks.size() + maxTokens > n_ctx) {
        throwJava(env, "This request is too long for the model's memory. Try a shorter description.");
        return nullptr;
    }
    if (!decode_all(e, stoks, [&](int done) { return report(0, e->n_prefix + done, e->n_prefix + (int) stoks.size()); })) {
        if (env->ExceptionCheck()) return nullptr;
        return nullptr;
    }

    // 3. Sample with the grammar.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!grammar.empty()) {
        llama_sampler *g = llama_sampler_init_grammar(e->vocab, grammar.c_str(), "root");
        if (!g) { llama_sampler_free(smpl); throwJava(env, "Internal error: the shape grammar did not parse."); return nullptr; }
        llama_sampler_chain_add(smpl, g);
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist((uint32_t) seed));
    }

    std::string out;
    bool cancelled = false;
    char piece[256];
    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(smpl, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, tok)) break;
        int n = llama_token_to_piece(e->vocab, tok, piece, sizeof(piece), 0, false);
        if (n > 0) out.append(piece, n);
        if (!report(1, i + 1, maxTokens)) { cancelled = true; break; }
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(e->ctx, b) != 0) { LOGE("decode failed at token %d", i); break; }
    }
    llama_sampler_free(smpl);
    if (env->ExceptionCheck() || cancelled) return nullptr;

    jbyteArray result = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(result, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

}  // extern "C"
