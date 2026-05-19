package dev.logickoder.ragwithgemma.data.ingestion

import android.util.Log
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dev.logickoder.ragwithgemma.data.DrugDao
import dev.logickoder.ragwithgemma.data.model.Drug
import dev.logickoder.ragwithgemma.data.model.DrugChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class IngestProgress(val processed: Int, val total: Int, val drugsInserted: Int, val chunksInserted: Int)

class MedscapeIngestor(
    private val dao: DrugDao,
    private val embedder: TextEmbedder,
) {

    fun ingestAll(dir: File): Flow<IngestProgress> = flow {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()
        val total = files.size
        val alreadyIngested = dao.getAllMedscapeIds().toHashSet()
        Log.d(TAG, "Starting ingestion of $total files (resume: ${alreadyIngested.size} already done)")

        var drugsInserted = alreadyIngested.size
        var chunksInserted = 0

        files.forEachIndexed { idx, file ->
            if (file.nameWithoutExtension in alreadyIngested) {
                if (idx % PROGRESS_EVERY == 0 || idx == total - 1) {
                    emit(IngestProgress(idx + 1, total, drugsInserted, chunksInserted))
                }
                return@forEachIndexed
            }
            try {
                val (drug, chunks) = parseFile(file) ?: return@forEachIndexed
                if (chunks.isEmpty()) return@forEachIndexed

                val withEmbeddings = chunks.map { chunk ->
                    val vector = embedder.embed(chunk.text)
                        .embeddingResult().embeddings().first().floatEmbedding()
                    chunk to floatsToBytes(vector)
                }

                dao.insertDrugWithChunks(drug, withEmbeddings)
                drugsInserted += 1
                chunksInserted += withEmbeddings.size
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to ingest ${file.name}", t)
            }

            if (idx % PROGRESS_EVERY == 0 || idx == total - 1) {
                emit(IngestProgress(idx + 1, total, drugsInserted, chunksInserted))
            }
        }
        Log.d(TAG, "Ingest complete: $drugsInserted drugs, $chunksInserted chunks")
    }.flowOn(Dispatchers.Default)

    private fun parseFile(file: File): Pair<Drug, List<DrugChunk>>? {
        val json = JSONObject(file.readText())
        val header = json.optJSONObject("Header") ?: return null

        val drugName = header.optString("DrugName").takeIf { it.isNotBlank() } ?: return null
        val brandName = header.optString("Brand", "").ifBlank { null }
        val drugClass = header.optJSONArray("ClassList")
            ?.optJSONObject(0)?.optString("Value")
            ?.ifBlank { null }
        val availability = header.optString("Av", "").ifBlank { null }
        val type = header.optString("Ty", "").ifBlank { null }

        val drug = Drug(
            medscapeId = file.nameWithoutExtension,
            drugName = drugName,
            brandName = brandName,
            drugClass = drugClass,
            availability = availability,
            type = type,
        )

        val chunks = mutableListOf<DrugChunk>()
        val sections = json.optJSONArray("Sections") ?: return drug to emptyList()
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            val sectionNumber = section.optString("SectionNumber").toIntOrNull() ?: continue
            val info = SectionMap.lookup(sectionNumber)
            val items = section.optJSONArray("sectionItems") ?: continue
            chunks += walkSectionItems(info, items)
        }
        return drug to chunks
    }

    private fun walkSectionItems(info: SectionInfo, items: JSONArray): List<DrugChunk> {
        val out = mutableListOf<DrugChunk>()
        var subHeader: String? = null
        var body = StringBuilder()

        fun flush() {
            val text = body.toString().trim()
            if (text.length >= MIN_CHUNK_CHARS) {
                val display = buildString {
                    append(info.label)
                    if (!subHeader.isNullOrBlank()) {
                        append(" — ")
                        append(subHeader)
                    }
                    append('\n')
                    append(text)
                }
                out += DrugChunk(
                    drugRowid = 0,
                    population = info.population.label,
                    sectionLabel = info.label,
                    subHeader = subHeader,
                    tag = "p",
                    text = display,
                )
            }
            body = StringBuilder()
        }

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val tag = item.optString("Tag")
            when (tag) {
                "h2", "h3", "h4" -> {
                    flush()
                    subHeader = item.optString("Value", "").ifBlank { null }
                }
                "p" -> {
                    val value = item.optString("Value", "")
                    if (value.isNotBlank()) body.appendLine(value)
                }
                "ul", "ol" -> {
                    val arr = item.optJSONArray("Value") ?: continue
                    for (j in 0 until arr.length()) {
                        val li = arr.optJSONObject(j) ?: continue
                        val text = li.optString("Text", "")
                        if (text.isNotBlank()) body.append("• ").appendLine(text)
                    }
                }
            }
        }
        flush()
        return out
    }

    private fun floatsToBytes(floats: FloatArray): ByteArray =
        ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { floats.forEach(::putFloat) }
            .array()

    companion object {
        private const val TAG = "MedscapeIngestor"
        private const val MIN_CHUNK_CHARS = 20
        private const val PROGRESS_EVERY = 25
    }
}
