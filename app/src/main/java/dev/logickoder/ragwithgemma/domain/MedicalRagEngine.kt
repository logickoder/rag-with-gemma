package dev.logickoder.ragwithgemma.domain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
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

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val gemmaFile = File("/data/local/tmp/gemma-4-E2B-it.litertlm")
        if (!gemmaFile.exists()) throw IllegalStateException("Model missing at ${gemmaFile.path}. adb push to /data/local/tmp/ first.")

        // ContextWrapper hack: Forces XNNPACK cache into external storage to avoid SIGABRT from internal partition limits
        val proxyContext = object : android.content.ContextWrapper(context) {
            override fun getCacheDir(): File {
                val extCache = externalCacheDir ?: super.getCacheDir()
//                File(extCache, "gemma-4-E2B-it.litertlm.xnnpack_cache").delete()
                return extCache
            }
        }

        val embedderFile = copyAssetToInternal("mobile_bert.tflite")

        val llmOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(gemmaFile.absolutePath)
            .setMaxTokens(1024)
            .build()

        gemmaLm = LlmInference.createFromOptions(proxyContext, llmOptions)

        embedder = TextEmbedder.createFromOptions(
            context,
            TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(embedderFile.path).build())
                .build()
        )
        Log.d(TAG, "Engine initialized successfully.")

        db.createVecTable()

        runCatching {
            Log.d(TAG, "DIAG sqlite_version=${dao.sqliteVersion()}")
            Log.d(TAG, "DIAG vss_drug_embeddings xinfo=${dao.vecTableColumns()}")
        }.onFailure { Log.e(TAG, "DIAG version/xinfo failed", it) }

        runCatching {
            Log.d(TAG, "DIAG vec modules=${dao.vecModules()}")
            Log.d(TAG, "DIAG vss CREATE sql=${dao.vecTableSql()}")
        }.onFailure { Log.e(TAG, "DIAG modules/sql failed", it) }

        runCatching {
            if (dao.getDrugCount() > 0) {
                Log.d(TAG, "DIAG smokeKnn=${dao.smokeKnn()}")
            } else {
                Log.d(TAG, "DIAG smokeKnn skipped (empty table)")
            }
        }.onFailure { Log.e(TAG, "DIAG smokeKnn failed", it) }

        runCatching {
            val json = (0 until 512).joinToString(prefix = "[", postfix = "]") { "0.0" }
            Log.d(TAG, "DIAG smokeKnnVecF32=${dao.smokeKnnVecF32(json)}")
        }.onFailure { Log.e(TAG, "DIAG smokeKnnVecF32 failed", it) }
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
        Log.d(TAG, "Ingested ${metadataList.size} drugs into Vector DB.")
    }

    fun askMedicalQuestion(query: String): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Query received: '$query'")
            val queryVector =
                embedder.embed(query).embeddingResult().embeddings().first().floatEmbedding()

            val relevantDrugs = dao.searchSemantically(floatArrayToByteArray(queryVector), 3)

            if (relevantDrugs.isEmpty()) {
                Log.w(TAG, "No relevant context found in Vector DB.")
                trySend("Insufficient local data.")
                close()
                return@withContext
            }

            val prompt = buildString {
                appendLine("System: Answer using ONLY the YAML context below. Do not infer.")
                appendLine("Context:")
                relevantDrugs.forEach { drug ->
                    appendLine("- Brand: ${drug.brandName}")
                    appendLine("  Generic: ${drug.genericName}")
                    appendLine("  Indications: ${drug.indications.take(300)}...")
                }
                appendLine("User: $query")
                appendLine("Assistant:")
            }

            Log.d(TAG, "Sending hydrated prompt to Gemma...")
            gemmaLm.generateResponseAsync(prompt) { partialResult, done ->
                if (partialResult != null) trySend(partialResult)
                if (done) close()
            }
        }
        awaitClose { Log.d(TAG, "LLM Stream closed.") }
    }.flowOn(Dispatchers.Default)

    private fun floatArrayToByteArray(floats: FloatArray): ByteArray =
        ByteBuffer.allocate(floats.size * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN) // MANDATORY FOR C++ JNI
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

    fun close() {
        if (::gemmaLm.isInitialized) gemmaLm.close()
        if (::embedder.isInitialized) embedder.close()
    }

    companion object {
        private const val TAG = "MedicalRagEngine"
    }
}