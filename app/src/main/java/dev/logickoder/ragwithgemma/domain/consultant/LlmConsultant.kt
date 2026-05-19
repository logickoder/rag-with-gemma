package dev.logickoder.ragwithgemma.domain.consultant

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("DEPRECATION")
class LlmConsultant(
    appContext: Context,
    private val modelFile: File,
) : Consultant {

    private val context = appContext.applicationContext
    private val proxyContext = object : android.content.ContextWrapper(context) {
        override fun getCacheDir(): File = externalCacheDir ?: super.getCacheDir()
    }

    private var llm: LlmInference? = null
    private lateinit var sessionOptions: LlmInferenceSession.LlmInferenceSessionOptions

    override suspend fun warmup() = withContext(Dispatchers.IO) {
        if (llm != null) return@withContext
        if (!modelFile.exists()) {
            throw IllegalStateException("Gemma model missing at ${modelFile.path}")
        }
        val llmOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        llm = LlmInference.createFromOptions(proxyContext, llmOptions)

        val templates = PromptTemplates.builder()
            .setUserPrefix("<start_of_turn>user\n")
            .setUserSuffix("<end_of_turn>\n")
            .setModelPrefix("<start_of_turn>model\n")
            .setModelSuffix("<end_of_turn>\n")
            .build()

        sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.4f)
            .setTopK(40)
            .setTopP(0.95f)
            .setPromptTemplates(templates)
            .build()
        Log.d(TAG, "LlmConsultant warmed.")
    }

    override fun process(request: ConsultantRequest): Flow<ConsultantEvent> = callbackFlow {
        val engine = llm ?: run {
            close(IllegalStateException("LlmConsultant.warmup() not called"))
            return@callbackFlow
        }
        val safeInput = truncate(request.context, MAX_INPUT_CHARS)
        val prompt = buildPrompt(request, safeInput)

        val buffer = StringBuilder()
        var stopped = false
        var session: LlmInferenceSession? = null

        try {
            session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
            session.addQueryChunk(prompt)
            session.generateResponseAsync { partial, done ->
                if (stopped) return@generateResponseAsync
                if (partial != null) {
                    buffer.append(partial)
                    val text = buffer.toString()
                    val stopIdx = STOP_MARKERS.minOfOrNull {
                        text.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
                    } ?: Int.MAX_VALUE
                    if (stopIdx != Int.MAX_VALUE) {
                        val raw = text.substring(0, stopIdx)
                        emitFinal(raw, safeInput, request.mode)
                        stopped = true
                        close()
                        return@generateResponseAsync
                    }
                    val safeLen = text.length - MAX_MARKER_LEN
                    if (safeLen > 0) {
                        val flushable = text.substring(0, safeLen)
                        val cleaned = scrubTokens(flushable)
                        if (cleaned.isNotEmpty()) trySend(ConsultantEvent.Delta(cleaned))
                        buffer.delete(0, safeLen)
                    }
                }
                if (done && !stopped) {
                    emitFinal(buffer.toString(), safeInput, request.mode)
                    close()
                }
            }
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }

        awaitClose {
            runCatching { session?.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun kotlinx.coroutines.channels.ProducerScope<ConsultantEvent>.emitFinal(
        raw: String,
        sourceInput: String,
        mode: AIProcessorMode,
    ) {
        val cleaned = cleanFinal(raw, mode, sourceInput)
        trySend(ConsultantEvent.Final(cleaned))
    }

    private fun buildPrompt(request: ConsultantRequest, safeInput: String): String {
        val populationLabel = request.population.ifBlank { "General" }
        val historyBlock = if (request.history.isEmpty()) "" else buildString {
            appendLine("[CONTEXT]")
            request.history.takeLast(HISTORY_TURNS).forEach { turn ->
                appendLine("User: ${turn.user}")
                appendLine("Assistant: ${turn.assistant}")
            }
            appendLine()
        }
        return buildString {
            append(historyBlock)
            appendLine("[SYSTEM]")
            appendLine("You are a clinical data reformatter.")
            appendLine("TASK: ${if (request.mode == AIProcessorMode.SUMMARIZE) "Summarize" else "Expand and structure"} the [DATA] for the $populationLabel population.")
            appendLine()
            appendLine("STRICT RULES:")
            appendLine("1. Identify the drug name from the DATA.")
            appendLine("2. Start the response with: \"For $populationLabel patients, [Drug Name]...\"")
            appendLine("3. Use ONLY the provided facts. Do not add outside knowledge.")
            appendLine("4. Do not use conversational filler.")
            appendLine()
            appendLine("[DATA]")
            appendLine(safeInput)
            appendLine()
            appendLine("[OUTPUT]")
        }
    }

    private fun truncate(input: String, max: Int): String =
        if (input.length <= max) input else input.take(max) + "…"

    override fun close() {
        llm?.close()
        llm = null
    }

    companion object {
        private const val TAG = "LlmConsultant"
        private const val MAX_TOKENS = 1024
        private const val MAX_INPUT_CHARS = 700
        private const val HISTORY_TURNS = 6
    }
}
