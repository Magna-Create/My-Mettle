// MNN's public 3.6.1 Llm API and Android stepping lifecycle are used here.
// See THIRD_PARTY_NOTICE.md for source references and Apache-2.0 attribution.
#include <jni.h>
#include <llm/llm.hpp>
#include <atomic>
#include <memory>
#include <stdexcept>

using MNN::Transformer::Llm;
using MNN::Transformer::LlmStatus;
struct Owner {
    std::unique_ptr<Llm> llm;
    std::atomic<bool> cancelled{false};
};
static std::string bytes(JNIEnv* env, jbyteArray value) {
    if (!value) return {};
    std::string result(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, result.size(), reinterpret_cast<jbyte*>(result.data()));
    return result;
}
static jbyteArray array(JNIEnv* env, const std::string& value) {
    auto result = env->NewByteArray(value.size());
    env->SetByteArrayRegion(result, 0, value.size(), reinterpret_cast<const jbyte*>(value.data()));
    return result;
}
static void fail(JNIEnv* env, const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
#define JNI_METHOD(name) Java_dev_kian_lab2b_vlm_MnnNative_##name

extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(load)(JNIEnv* env, jobject, jbyteArray path, jbyteArray config) {
    try {
        auto owner = std::make_unique<Owner>();
        owner->llm.reset(Llm::createLLM(bytes(env, path)));
        if (!owner->llm) throw std::runtime_error("MNN createLLM failed");
        if (!owner->llm->set_config(bytes(env, config))) throw std::runtime_error("MNN set_config failed");
        if (!owner->llm->load()) throw std::runtime_error("MNN model/vision load failed; try CPU if GPU was requested");
        return reinterpret_cast<jlong>(owner.release());
    } catch (const std::exception& e) { fail(env, e); return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL JNI_METHOD(config)(JNIEnv* env, jobject, jlong handle) {
    try { return array(env, reinterpret_cast<Owner*>(handle)->llm->dump_config()); }
    catch (const std::exception& e) { fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(stop)(JNIEnv*, jobject, jlong handle) {
    reinterpret_cast<Owner*>(handle)->cancelled.store(true);
}

extern "C" JNIEXPORT jlongArray JNICALL JNI_METHOD(generate)(JNIEnv* env, jobject, jlong handle,
        jbyteArray system, jbyteArray user, jbyteArray image, jint maxTokens, jobject callback) {
    try {
        auto* owner = reinterpret_cast<Owner*>(handle);
        auto* llm = owner->llm.get();
        // Native cancellation is reset by arm(), before Kotlin publishes GENERATING.
        // No second thread mutates LlmContext; stop() only changes the atomic flag.
        llm->reset();
        auto* context = const_cast<MNN::Transformer::LlmContext*>(llm->getContext());
        context->status = LlmStatus::RUNNING;
        MNN::Transformer::ChatMessages messages;
        auto systemText = bytes(env, system);
        if (!systemText.empty()) messages.emplace_back("system", systemText);
        auto content = bytes(env, user);
        auto imagePath = bytes(env, image);
        if (!imagePath.empty()) content += "\n<img>" + imagePath + "</img>";
        messages.emplace_back("user", content);
        std::ostringstream output;
        auto callbackClass = env->GetObjectClass(callback);
        auto onOutput = env->GetMethodID(callbackClass, "onOutput", "([B)V");
        if (!onOutput) return nullptr;
        if (!owner->cancelled.load()) {
            // ChatMessages applies the exact installed model template once; Omni then
            // resolves the one current <img> path. Empty terminator prevents <eop> UI noise.
            // Equivalent to the no-cache ChatMessages path, with an explicit budget
            // check before decoder prefill. Omni tokenization prepares image embeddings
            // once; its vision methods establish their own ExecutorScope.
            const auto tokens = llm->tokenizer_encode(llm->apply_chat_template(messages));
            if (tokens.empty() || tokens.size() + maxTokens > 8192)
                throw std::runtime_error("Current system/instruction/OCR/image exceeds the 8192-token harness budget; shorten the prompt or OCR evidence");
            if (!owner->cancelled.load()) llm->response(tokens, &output, "", 0);
        }
        for (int step = 0; step < maxTokens && !owner->cancelled.load(); ++step) {
            // Upstream Android 3.6.1 restores MAX_TOKENS_FINISHED between generate(1) calls.
            if (context->status == LlmStatus::MAX_TOKENS_FINISHED) context->status = LlmStatus::RUNNING;
            if (context->status == LlmStatus::NORMAL_FINISHED) break;
            if (context->status != LlmStatus::RUNNING) throw std::runtime_error("MNN prefill/decode failed; status=" + std::to_string(static_cast<int>(context->status)));
            llm->generate(1);
            auto chunk = array(env, output.str());
            env->CallVoidMethod(callback, onOutput, chunk);
            env->DeleteLocalRef(chunk);
            if (env->ExceptionCheck()) return nullptr;
        }
        if (!owner->cancelled.load() && (context->status == LlmStatus::INTERNAL_ERROR || context->status == LlmStatus::TIMEOUT))
            throw std::runtime_error("MNN generation ended with an error");
        jlong values[] = {context->prompt_len, context->gen_seq_len, context->vision_us,
                         context->prefill_us, context->decode_us, owner->cancelled.load() ? 1L : 0L};
        auto result = env->NewLongArray(6);
        env->SetLongArrayRegion(result, 0, 6, values);
        env->DeleteLocalRef(callbackClass);
        return result;
    } catch (const std::exception& e) { fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(arm)(JNIEnv*, jobject, jlong handle) {
    reinterpret_cast<Owner*>(handle)->cancelled.store(false);
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(unload)(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Owner*>(handle);
}
