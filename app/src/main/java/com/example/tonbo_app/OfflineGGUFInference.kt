package com.example.tonbo_app

import android.content.Context
import android.util.Log
import com.suhel.llamabro.sdk.chat.ChatEvent
import com.suhel.llamabro.sdk.chat.CompletionResult
import com.suhel.llamabro.sdk.chat.LlamaChatSession
import com.suhel.llamabro.sdk.config.LoadableModel
import com.suhel.llamabro.sdk.config.ModelLoadConfig
import com.suhel.llamabro.sdk.config.ModelProfiles
import com.suhel.llamabro.sdk.config.OverflowStrategy
import com.suhel.llamabro.sdk.config.SessionConfig
import com.suhel.llamabro.sdk.engine.LlamaEngine
import com.suhel.llamabro.sdk.engine.LlamaSession
import kotlinx.coroutines.runBlocking

/**
 * 使用 llama.cpp 加载用户自训练的 qwen-tonbo-q8_0.gguf
 */
class OfflineGGUFInference(private val context: Context) {

    companion object {
        private const val TAG = "OfflineGGUFInference"
        private const val DEFAULT_SYSTEM_PROMPT =
            "你是一個名叫瞳伴的語音助手，專為視障人士設計。" +
            "請用廣東話回應，回答要簡潔自然，適合語音播報。"
    }

    private var engine: LlamaEngine? = null
    private var session: LlamaSession? = null
    private var chatSession: LlamaChatSession? = null
    private var systemPrompt: String = DEFAULT_SYSTEM_PROMPT

    fun loadModel(modelPath: String) = runBlocking {
        Log.i(TAG, "Loading GGUF: $modelPath")
        releaseInternal()

        val loadable = LoadableModel(
            loadConfig = ModelLoadConfig(
                path = modelPath,
                threads = 4,
                useMMap = true,
                useMLock = false
            ),
            profile = ModelProfiles.QWEN_2_5
        )
        engine = LlamaEngine.create(loadable) { true }
        session = engine!!.createSession(
            SessionConfig(
                contextSize = 2048,
                overflowStrategy = OverflowStrategy.RollingWindow(dropTokens = 256)
            )
        )
        recreateChatSession()
        Log.i(TAG, "GGUF model ready")
    }

    fun setSystemPrompt(prompt: String) {
        if (prompt != systemPrompt) {
            systemPrompt = prompt
            runBlocking { recreateChatSession() }
        }
    }

    fun generate(userMessage: String): String = runBlocking {
        val chat = chatSession ?: throw IllegalStateException("GGUF model not loaded")
        val sb = StringBuilder()
        chat.completion(
            ChatEvent.UserEvent(content = userMessage, think = false)
        ).collect { result ->
            when (result) {
                is CompletionResult.Streaming -> appendText(sb, result.events)
                is CompletionResult.Complete -> appendText(sb, result.events)
                is CompletionResult.Error -> throw RuntimeException(result.error.toString())
            }
        }
        postProcess(sb.toString().trim(), userMessage)
    }

    private fun appendText(sb: StringBuilder, parts: List<ChatEvent.AssistantEvent.Part>) {
        sb.clear()
        parts.filterIsInstance<ChatEvent.AssistantEvent.Part.TextPart>()
            .forEach { sb.append(it.content) }
    }

    fun release() = runBlocking {
        releaseInternal()
    }

    private suspend fun recreateChatSession() {
        val eng = session ?: return
        chatSession = eng.createChatSession(systemPrompt)
        chatSession?.initialize()
    }

    private suspend fun releaseInternal() {
        chatSession = null
        session?.close()
        session = null
        engine?.close()
        engine = null
    }

    private fun postProcess(response: String, userInput: String): String {
        var text = response
            .replace("我叫Qwen", "我叫瞳伴")
            .replace("我是Qwen", "我是瞳伴")
            .replace("由阿里云開發", "係你嘅語音助手")
            .replace("由阿里云开发", "是你的语音助手")

        if (userInput.contains("你叫咩名") || userInput.contains("你叫什麼名字")
            || userInput.lowercase().contains("what's your name")
        ) {
            return "我叫瞳伴，係你嘅語音助手。"
        }
        return text
    }
}
