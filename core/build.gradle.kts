import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin: mesh data, cleanup, decimation, exporters. No Android dependencies, so tests run fast on the JVM.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// The model evaluation (LlmEval) depends on which model server it talks to; never reuse a cached result for another setup.
tasks.test {
    inputs.property("llmUrl", System.getenv("MESHGEN_LLM_URL") ?: "")
    inputs.property("llmLabel", System.getenv("MESHGEN_LLM_LABEL") ?: "")
    if (System.getenv("MESHGEN_LLM_URL") != null) outputs.upToDateWhen { false }
}
