package com.meshgen.app.llm;

/** Desktop stand-in with the same JNI signatures as the app's LlamaNative (Kotlin object with @JvmStatic externals). */
public final class LlamaNative {
    public interface Progress { boolean onProgress(int phase, int done, int total); }
    public static native void initBackends(String libDir);
    public static native long load(String path, int nCtx, int nThreads);
    public static native void free(long handle);
    public static native byte[] generate(long handle, byte[] prefix, byte[] suffix, String grammar, int maxTokens,
                                         float temperature, int seed, String cachePath, Progress callback);
}
