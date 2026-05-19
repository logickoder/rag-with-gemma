package dev.logickoder.ragwithgemma.data.source

import android.content.Context
import android.util.Log
import dev.logickoder.ragwithgemma.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

class AssetBootstrap(private val context: Context) {

    suspend fun resolveGemmaModel(): File = withContext(Dispatchers.IO) {
        val tmpModel = File(DEV_LOCAL_TMP, GEMMA_FILENAME)
        if (tmpModel.exists()) return@withContext tmpModel

        val internalModel = File(context.filesDir, GEMMA_FILENAME)
        if (internalModel.exists()) return@withContext internalModel

        throw IllegalStateException(
            "Gemma model not found at ${tmpModel.path} or ${internalModel.path}. " +
                "Run scripts/setup.sh to sideload, or switch to Semantic mode in Settings."
        )
    }

    fun gemmaModelExists(): Boolean {
        return File(DEV_LOCAL_TMP, GEMMA_FILENAME).exists() ||
            File(context.filesDir, GEMMA_FILENAME).exists()
    }

    suspend fun resolveEmbedder(): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, EMBEDDER_FILENAME)
        if (!file.exists()) {
            context.assets.open(EMBEDDER_FILENAME).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        file
    }

    suspend fun resolveDrugJsons(progress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            val devDir = File(DEV_LOCAL_TMP, DRUG_JSONS_DIRNAME)
            if (BuildConfig.IS_DEV && devDir.exists() && devDir.list()?.isNotEmpty() == true) {
                Log.d(TAG, "Using sideloaded drug JSONs at ${devDir.path}")
                return@withContext devDir
            }

            val internalDir = File(context.filesDir, DRUG_JSONS_DIRNAME)
            if (internalDir.exists() && internalDir.list()?.isNotEmpty() == true) {
                Log.d(TAG, "Using cached drug JSONs at ${internalDir.path}")
                return@withContext internalDir
            }

            internalDir.mkdirs()
            downloadAndUnzip(DRUGS_ZIP_URL, internalDir, progress)
            internalDir
        }

    suspend fun resolveInteractionsDb(): File? = null

    private fun downloadAndUnzip(
        url: String,
        destDir: File,
        progress: (Long, Long) -> Unit,
    ) {
        Log.d(TAG, "Downloading $url ...")
        val conn = URL(url).openConnection()
        conn.connect()
        val total = conn.contentLengthLong
        var downloaded = 0L

        ZipInputStream(conn.getInputStream()).use { zip ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val safeName = File(entry.name).name
                if (safeName.isEmpty() || !safeName.endsWith(".json", ignoreCase = true)) {
                    zip.closeEntry()
                    continue
                }
                FileOutputStream(File(destDir, safeName)).use { out ->
                    while (true) {
                        val read = zip.read(buf)
                        if (read < 0) break
                        out.write(buf, 0, read)
                        downloaded += read
                        progress(downloaded, total)
                    }
                }
                zip.closeEntry()
            }
        }
        Log.d(TAG, "Unzipped ${destDir.list()?.size ?: 0} files to ${destDir.path}")
    }

    companion object {
        private const val TAG = "AssetBootstrap"
        private const val DEV_LOCAL_TMP = "/data/local/tmp"
        const val GEMMA_FILENAME = "gemma-4-E2B-it.litertlm"
        const val EMBEDDER_FILENAME = "mobile_bert.tflite"
        const val DRUG_JSONS_DIRNAME = "medscape_drug_jsons"
        const val DRUGS_ZIP_URL =
            "https://img.staging.medscape.com/pi/iphone/medscapeapp/UE/u-drugcontent-json-template-139.zip"
    }
}
