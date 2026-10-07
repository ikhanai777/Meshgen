import com.meshgen.app.llm.LlamaNative;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Exercises the real JNI bridge (app/src/main/cpp/meshllm.cpp) built for the desktop:
 * java -Djava.library.path=BUILD JniTest BUILD MODEL.gguf PLAN_SYSTEM.txt PLAN_GRAMMAR.gbnf CACHE_FILE
 */
public class JniTest {
    static int[] phaseCalls = new int[2];
    static int firstPhase0Done = -1;

    static String run(long h, String prefix, String request, String grammar, String cache, int cancelAfter) {
        phaseCalls[0] = phaseCalls[1] = 0;
        firstPhase0Done = -1;
        String suffix = "<|im_start|>user\nRequest: " + request + "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
        long t = System.nanoTime();
        byte[] out = LlamaNative.generate(h, prefix.getBytes(StandardCharsets.UTF_8), suffix.getBytes(StandardCharsets.UTF_8),
            grammar, 200, 0.1f, 1234, cache, (phase, done, total) -> {
                phaseCalls[phase]++;
                if (phase == 0 && firstPhase0Done < 0) firstPhase0Done = done;
                return !(phase == 1 && cancelAfter > 0 && done >= cancelAfter);
            });
        double secs = (System.nanoTime() - t) / 1e9;
        String text = out == null ? null : new String(out, StandardCharsets.UTF_8);
        System.out.printf("  %.1fs  prompt-batches=%d (first done=%d)  tokens=%d  -> %s%n", secs, phaseCalls[0], firstPhase0Done, phaseCalls[1], text);
        return text;
    }

    public static void main(String[] a) throws Exception {
        String libDir = a[0], model = a[1];
        String system = Files.readString(Path.of(a[2])), grammar = Files.readString(Path.of(a[3]));
        String cache = a[4];
        Files.deleteIfExists(Path.of(cache));
        System.loadLibrary("meshllm");
        LlamaNative.initBackends(libDir + "/bin");
        String prefix = "<|im_start|>system\n" + system + "<|im_end|>\n";

        long h = LlamaNative.load(model, 4096, 4);
        System.out.println("1. first request (reads the instructions, saves the cache):");
        String r1 = run(h, prefix, "a 10cm hexagonal pen holder with 3mm walls", grammar, cache, 0);
        check(r1 != null && r1.contains("\"template\""), "valid reply");
        check(Files.size(Path.of(cache)) > 1_000_000, "prefix cache saved to disk");

        System.out.println("2. second request (prefix already in memory):");
        String r2 = run(h, prefix, "a round planter 15 cm wide", grammar, cache, 0);
        check(r2 != null && r2.contains("planter"), "planter chosen");
        check(firstPhase0Done > 1000, "only the request was read (started at token " + firstPhase0Done + ")");

        System.out.println("3. cancel after 5 tokens:");
        String r3 = run(h, prefix, "a coaster", grammar, cache, 5);
        check(r3 == null, "returns null when cancelled");

        LlamaNative.free(h);
        System.out.println("4. reload model, prefix restored from disk:");
        h = LlamaNative.load(model, 4096, 4);
        String r4 = run(h, prefix, "a small bowl", grammar, cache, 0);
        check(r4 != null && r4.contains("bowl"), "bowl chosen");
        check(firstPhase0Done > 1000, "instructions not re-read (started at token " + firstPhase0Done + ")");

        System.out.println("5. no grammar, plain text:");
        String r5 = run(h, prefix, "say ok", "", cache, 0);
        check(r5 != null, "works without grammar");
        LlamaNative.free(h);

        System.out.println("6. bad model path:");
        try { LlamaNative.load("/nonexistent.gguf", 4096, 4); check(false, "should throw"); }
        catch (IllegalStateException e) { check(e.getMessage().contains("Could not load"), "clear error: " + e.getMessage()); }
        System.out.println("ALL JNI CHECKS PASSED");
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "   ok   " : "   FAIL ") + what);
        if (!ok) System.exit(1);
    }
}
