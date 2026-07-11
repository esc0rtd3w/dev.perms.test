#include <jni.h>
#include <android/log.h>
#include <capstone.h>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

namespace {

constexpr const char *kLogTag = "PermsTestCapstone";

std::string bytes_to_hex(const uint8_t *bytes, size_t size) {
    std::ostringstream out;
    out << std::hex << std::setfill('0');
    for (size_t i = 0; i < size; ++i) {
        if (i != 0) out << ' ';
        out << std::setw(2) << static_cast<unsigned int>(bytes[i]);
    }
    return out.str();
}

std::string make_record(const cs_insn &insn) {
    std::ostringstream out;
    out << std::hex << insn.address
        << '\t' << std::dec << insn.size
        << '\t' << bytes_to_hex(insn.bytes, insn.size)
        << '\t' << (insn.mnemonic == nullptr ? "" : insn.mnemonic)
        << '\t' << (insn.op_str == nullptr ? "" : insn.op_str);
    return out.str();
}

jobjectArray make_string_array(JNIEnv *env, const std::vector<std::string> &records) {
    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) return nullptr;
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(records.size()), string_class, nullptr);
    if (array == nullptr) return nullptr;
    for (jsize i = 0; i < static_cast<jsize>(records.size()); ++i) {
        jstring value = env->NewStringUTF(records[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(array, i, value);
        env->DeleteLocalRef(value);
    }
    return array;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_perms_test_MemoryCapstoneNative_nativeIsSupported(JNIEnv *, jclass) {
    csh handle = 0;
    cs_err err = cs_open(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN, &handle);
    if (err != CS_ERR_OK) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "cs_open failed: %d", static_cast<int>(err));
        return JNI_FALSE;
    }
    cs_close(&handle);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_perms_test_MemoryCapstoneNative_nativeDisassemble(JNIEnv *env,
                                                           jclass,
                                                           jbyteArray code,
                                                           jlong base_address,
                                                           jint max_instructions) {
    if (code == nullptr) return make_string_array(env, {});
    jsize code_size = env->GetArrayLength(code);
    if (code_size <= 0) return make_string_array(env, {});

    jbyte *raw = env->GetByteArrayElements(code, nullptr);
    if (raw == nullptr) return make_string_array(env, {});

    csh handle = 0;
    cs_err err = cs_open(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN, &handle);
    if (err != CS_ERR_OK) {
        env->ReleaseByteArrayElements(code, raw, JNI_ABORT);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "cs_open failed: %d", static_cast<int>(err));
        return make_string_array(env, {});
    }

    cs_option(handle, CS_OPT_DETAIL, CS_OPT_OFF);

    cs_insn *insn = nullptr;
    size_t count = cs_disasm(handle,
                             reinterpret_cast<const uint8_t *>(raw),
                             static_cast<size_t>(code_size),
                             static_cast<uint64_t>(base_address),
                             max_instructions <= 0 ? 0 : static_cast<size_t>(max_instructions),
                             &insn);

    std::vector<std::string> records;
    records.reserve(count);
    for (size_t i = 0; i < count; ++i) {
        records.emplace_back(make_record(insn[i]));
    }

    if (insn != nullptr) cs_free(insn, count);
    cs_close(&handle);
    env->ReleaseByteArrayElements(code, raw, JNI_ABORT);

    return make_string_array(env, records);
}
