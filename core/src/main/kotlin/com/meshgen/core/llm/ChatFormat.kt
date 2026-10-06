package com.meshgen.core.llm

/** Turns chat messages into the exact text a model family was trained on. */
enum class ChatFormat {
    /** Qwen (ChatML). Thinking is switched off: the reply starts with an empty think block. */
    CHATML_NO_THINK;

    fun render(messages: List<ChatMessage>): String = when (this) {
        CHATML_NO_THINK -> buildString {
            for (m in messages) {
                val role = when (m.role) { ChatMessage.Role.SYSTEM -> "system"; ChatMessage.Role.USER -> "user"; ChatMessage.Role.ASSISTANT -> "assistant" }
                val content = if (m.role == ChatMessage.Role.ASSISTANT) "<think>\n\n</think>\n\n${m.content}" else m.content
                append("<|im_start|>").append(role).append('\n').append(content).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n<think>\n\n</think>\n\n")
        }
    }

    /** The system message alone, rendered — the reusable prefix of every prompt. */
    fun prefix(system: String): String = when (this) {
        CHATML_NO_THINK -> "<|im_start|>system\n$system<|im_end|>\n"
    }
}
