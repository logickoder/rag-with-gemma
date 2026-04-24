package dev.logickoder.ragwithgemma.domain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dev.logickoder.ragwithgemma.data.AppDatabase
import dev.logickoder.ragwithgemma.data.model.DrugMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@Suppress("DEPRECATION")
class MedicalRagEngine(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.drugDao()

    private lateinit var gemmaLm: LlmInference
    private lateinit var embedder: TextEmbedder
    private lateinit var sessionOptions: LlmInferenceSession.LlmInferenceSessionOptions

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val gemmaFile = File("/data/local/tmp/gemma-4-E2B-it.litertlm")
        if (!gemmaFile.exists()) throw IllegalStateException("Model missing at ${gemmaFile.path}. adb push to /data/local/tmp/ first.")

        val proxyContext = object : android.content.ContextWrapper(context) {
            override fun getCacheDir(): File = externalCacheDir ?: super.getCacheDir()
        }

        val embedderFile = copyAssetToInternal("mobile_bert.tflite")

        val llmOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(gemmaFile.absolutePath)
            .setMaxTokens(1024)
            .build()

        gemmaLm = LlmInference.createFromOptions(proxyContext, llmOptions)

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

        embedder = TextEmbedder.createFromOptions(
            context,
            TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(embedderFile.path).build())
                .build()
        )

        db.createVecTable()
        Log.d(TAG, "Engine initialized.")
    }

    suspend fun ingestFdaDataIfEmpty() = withContext(Dispatchers.Default) {
        if (dao.getDrugCount() > 0) return@withContext

        Log.d(TAG, "Starting FDA data ingestion...")
        val jsonString = context.assets.open("drugs.json").bufferedReader().use { it.readText() }
        val results = JSONObject(jsonString).getJSONArray("results")

        val metadataList = mutableListOf<DrugMetadata>()
        val embeddingList = mutableListOf<ByteArray>()

        for (i in 0 until results.length()) {
            val drug = results.getJSONObject(i)
            val openfda = drug.optJSONObject("openfda")

            val brandName = openfda?.optJSONArray("brand_name")?.optString(0) ?: "Unknown"
            val genericName = openfda?.optJSONArray("generic_name")?.optString(0) ?: "Unknown"
            val indications = drug.optJSONArray("indications_and_usage")?.optString(0) ?: continue

            metadataList.add(
                DrugMetadata(
                    brandName = brandName,
                    genericName = genericName,
                    indications = indications
                )
            )

            val vector =
                embedder.embed(indications).embeddingResult().embeddings().first().floatEmbedding()
            embeddingList.add(floatArrayToByteArray(vector))
        }

        dao.insertHybridData(metadataList, embeddingList)
        Log.d(TAG, "Ingested ${metadataList.size} drugs.")
    }

    fun askMedicalQuestion(query: String): Flow<String> = callbackFlow {
        var session: LlmInferenceSession? = null
        val buffer = StringBuilder()
        var stopped = false
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Query: '$query'")
            val queryVector =
                embedder.embed(query).embeddingResult().embeddings().first().floatEmbedding()
            val hits = dao.searchWithDistance(floatArrayToByteArray(queryVector), 3)

            Log.d(TAG, "RAG distances=${hits.map { it.distance }}")

            if (hits.isEmpty()) {
                trySend("No drug data available.")
                close()
                return@withContext
            }

            val prompt = buildString {
                appendLine("Answer using only the context below. If the context is insufficient, say so.")
                appendLine()
                appendLine("Context:")
                hits.forEach { d ->
                    appendLine("- ${d.brandName} (${d.genericName}): ${d.indications.take(400)}")
                }
                appendLine()
                appendLine("Question: $query")
            }

            Log.d(TAG, "Sending prompt to Gemma.")
            session = LlmInferenceSession.createFromOptions(gemmaLm, sessionOptions).also { s ->
                s.addQueryChunk(prompt)
                s.generateResponseAsync { partial, done ->
                    if (stopped) return@generateResponseAsync
                    if (partial != null) {
                        buffer.append(partial)
                        val text = buffer.toString()
                        val stopIdx = STOP_MARKERS.minOfOrNull {
                            text.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
                        } ?: Int.MAX_VALUE
                        if (stopIdx != Int.MAX_VALUE) {
                            val clean = scrubTokens(text.substring(0, stopIdx))
                            if (clean.isNotEmpty()) trySend(clean)
                            stopped = true
                            close()
                            return@generateResponseAsync
                        }
                        val safeLen = text.length - MAX_MARKER_LEN
                        if (safeLen > 0) {
                            val flushable = text.substring(0, safeLen)
                            val cleaned = scrubTokens(flushable)
                            if (cleaned.isNotEmpty()) trySend(cleaned)
                            buffer.delete(0, safeLen)
                        }
                    }
                    if (done && !stopped) {
                        val clean = scrubTokens(buffer.toString())
                        if (clean.isNotEmpty()) trySend(clean)
                        close()
                    }
                }
            }
        }
        awaitClose {
            runCatching { session?.close() }
            Log.d(TAG, "Stream closed.")
        }
    }.flowOn(Dispatchers.Default)

    private fun floatArrayToByteArray(floats: FloatArray): ByteArray =
        ByteBuffer.allocate(floats.size * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .apply { for (f in floats) putFloat(f) }
            .array()

    private fun copyAssetToInternal(filename: String): File {
        val file = File(context.filesDir, filename)
        if (!file.exists()) {
            context.assets.open(filename).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        return file
    }

    private fun scrubTokens(s: String): String = SCRUB_RE.replace(s, "")

    fun close() {
        if (::gemmaLm.isInitialized) gemmaLm.close()
        if (::embedder.isInitialized) embedder.close()
    }

    companion object {
        private const val TAG = "MedicalRagEngine"
        private val SCRUB_RE = Regex("""<(?:start_of_turn|end_of_turn|eos|channel\|?|turn\|?)>""")
        private val STOP_MARKERS = listOf("<end_of_turn>", "<eos>")
        private const val MAX_MARKER_LEN = 16
    }
}
