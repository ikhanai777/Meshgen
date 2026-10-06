# Keep rules are added per phase as native/ML libraries are integrated.

# JNI: native methods and the progress callback called from C++.
-keepclasseswithmembernames class com.meshgen.app.llm.LlamaNative { native <methods>; }
-keep interface com.meshgen.app.llm.LlamaNative$Progress { *; }
-keep class * implements com.meshgen.app.llm.LlamaNative$Progress { *; }
