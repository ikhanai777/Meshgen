Desktop test of the JNI bridge (`app/src/main/cpp/meshllm.cpp`) with a real model.

    cmake -S app/src/main/cpp -B /tmp/jni -G Ninja -DCMAKE_BUILD_TYPE=Release && ninja -C /tmp/jni
    javac -d /tmp/jni/classes tools/jni_test/com/meshgen/app/llm/LlamaNative.java tools/jni_test/JniTest.java
    java -cp /tmp/jni/classes -Djava.library.path=/tmp/jni:/tmp/jni/bin JniTest /tmp/jni MODEL.gguf \
        core/build/llm/plan_system.txt core/build/llm/plan_grammar.gbnf /tmp/jni/prefix.state
