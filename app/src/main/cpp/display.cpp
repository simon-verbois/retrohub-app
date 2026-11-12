#include <jni.h>
#include <dlfcn.h>

// Adapter for the pinned LibretroDroid 0.14.0 host. Its Kotlin API does not expose
// libretro rotation. Use its exported environment callback, without object layouts
// or private field offsets. The real-core display test verifies this ABI in both builds.
struct Geometry {
    unsigned width, height, max_width, max_height;
    float aspect;
};
struct AvInfo {
    Geometry geometry;
    double fps, sample_rate;
};

extern "C" JNIEXPORT jboolean JNICALL
Java_ovh_verbois_retrohub_game_NativeDisplay_configure(
        JNIEnv* env, jobject, jstring core_path, jint clockwise_degrees, jfloat aspect) {
    void* host = dlopen("liblibretrodroid.so", RTLD_NOW | RTLD_NOLOAD);
    if (!host) return JNI_FALSE;
    auto callback = reinterpret_cast<bool (*)(unsigned, void*)>(
        dlsym(host, "_ZN11Environment20callback_environmentEjPv"));
    void* instance = dlsym(host, "_ZZN11Environment11getInstanceEvE8instance");
    auto width = reinterpret_cast<unsigned (*)(const void*)>(
        dlsym(host, "_ZNK11Environment20getGameGeometryWidthEv"));
    auto height = reinterpret_cast<unsigned (*)(const void*)>(
        dlsym(host, "_ZNK11Environment21getGameGeometryHeightEv"));
    const char* path = env->GetStringUTFChars(core_path, nullptr);
    void* core = path ? dlopen(path, RTLD_NOW | RTLD_NOLOAD) : nullptr;
    if (path) env->ReleaseStringUTFChars(core_path, path);
    auto av_info = core ? reinterpret_cast<void (*)(AvInfo*)>(dlsym(core, "retro_get_system_av_info")) : nullptr;
    bool applied = false;
    if (callback && instance && width && height && av_info && aspect > 0) {
        AvInfo info{};
        av_info(&info);
        // Preserve dynamic core dimensions and internal resolution; only change aspect.
        if (width(instance) && height(instance)) {
            info.geometry.width = width(instance);
            info.geometry.height = height(instance);
        }
        info.geometry.aspect = aspect;
        unsigned turns = (4 - clockwise_degrees / 90) % 4;
        applied = callback(37 /* RETRO_ENVIRONMENT_SET_GEOMETRY */, &info.geometry)
            && callback(1 /* RETRO_ENVIRONMENT_SET_ROTATION */, &turns);
    }
    if (core) dlclose(core);
    dlclose(host);
    return applied ? JNI_TRUE : JNI_FALSE;
}
