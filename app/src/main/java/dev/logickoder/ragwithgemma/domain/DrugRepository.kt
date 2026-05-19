package dev.logickoder.ragwithgemma.domain

import android.util.Log
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dev.logickoder.ragwithgemma.data.DrugDao
import dev.logickoder.ragwithgemma.data.model.Drug
import dev.logickoder.ragwithgemma.data.model.DrugChunk
import dev.logickoder.ragwithgemma.data.model.Population
import dev.logickoder.ragwithgemma.data.model.RankedChunk
import dev.logickoder.ragwithgemma.domain.consultant.AIProcessorMode
import dev.logickoder.ragwithgemma.domain.consultant.Consultant
import dev.logickoder.ragwithgemma.domain.consultant.ConsultantEvent
import dev.logickoder.ragwithgemma.domain.consultant.ConsultantRequest
import dev.logickoder.ragwithgemma.domain.consultant.HistoryTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DrugRepository(
    private val dao: DrugDao,
    private val embedder: TextEmbedder,
    @Volatile var consultant: Consultant,
) {
    private val embedderLock = Mutex()
    private var cachedDrugNames: List<String> = emptyList()

    suspend fun refreshDrugCache() = withContext(Dispatchers.IO) {
        cachedDrugNames = dao.getAllDrugNames().sortedByDescending { it.length }
        Log.d(TAG, "Cached ${cachedDrugNames.size} drug names")
    }

    fun extractDrugNames(query: String): List<String> {
        val lower = query.lowercase()
        return cachedDrugNames.filter { name -> lower.contains(name.lowercase()) }
            .sortedByDescending { it.length }
    }

    fun detectPopulation(query: String): Population {
        val q = query.lowercase()
        return when {
            listOf("pediatric", "child", "infant", "baby").any { q.contains(it) } -> Population.PEDIATRIC
            listOf("geriatric", "elderly", "senior", "older adult").any { q.contains(it) } -> Population.GERIATRIC
            q.contains("general") -> Population.GENERAL
            else -> Population.ADULT
        }
    }

    fun getSemanticAnswer(
        query: String,
        drug: String,
        history: List<HistoryTurn> = emptyList(),
    ): Flow<ConsultantEvent> {
        return kotlinx.coroutines.flow.flow {
            val population = detectPopulation(query)
            val cleanQuery = query.lowercase()
                .replace(drug.lowercase(), "")
                .trim()
                .ifBlank { "Main indications, dosage forms, and primary strengths" }

            val ranked = retrieveTopChunks(cleanQuery, drug, population)
            if (ranked.isEmpty()) {
                emit(ConsultantEvent.Final("No data found for $drug."))
                return@flow
            }

            val grouped = ranked.groupBy { it.chunk.sectionLabel }
            val sortedSections = grouped.entries.sortedBy { entry ->
                entry.value.minOf { it.distance }
            }
            val context = buildString {
                append("DRUG: ")
                appendLine(drug)
                appendLine()
                for ((section, hits) in sortedSections) {
                    append(section.uppercase())
                    appendLine()
                    hits.take(2).forEach { rc ->
                        appendLine(rc.chunk.text.trim())
                    }
                    appendLine()
                }
            }

            val req = ConsultantRequest(
                context = context,
                mode = AIProcessorMode.EXPAND,
                population = population.label,
                drug = drug,
                history = history,
            )
            consultant.process(req).collect { emit(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun getSatisfactorySummary(drug: String): Flow<ConsultantEvent> {
        return kotlinx.coroutines.flow.flow {
            val drugRow = dao.getDrugByName(drug)
            if (drugRow == null) {
                emit(ConsultantEvent.Final("No data found for $drug."))
                return@flow
            }
            val chunks = dao.getAllChunksForDrug(drugRow.rowid).filter { it.tag == "p" }
            if (chunks.isEmpty()) {
                emit(ConsultantEvent.Final("No content available for $drug."))
                return@flow
            }
            val priorityVec = embedderLock.withLock { embed("Main clinical indications and primary dosage") }
            val sections = chunks.groupBy { it.sectionLabel }
            val rankedSections = sections.entries
                .map { (label, sectionChunks) ->
                    val labelVec = runCatching { embedderLock.withLock { embed(label) } }.getOrNull()
                    val score = labelVec?.let { cosine(priorityVec, it) } ?: 0f
                    Triple(label, sectionChunks, score)
                }
                .sortedByDescending { it.third }
                .take(10)

            val context = buildString {
                appendLine("DRUG: $drug")
                appendLine()
                for ((label, sectionChunks, _) in rankedSections) {
                    appendLine(label.uppercase())
                    sectionChunks.take(3).forEach { c -> appendLine(c.text.trim()) }
                    appendLine()
                }
            }

            val req = ConsultantRequest(
                context = context,
                mode = AIProcessorMode.EXPAND,
                population = "",
                drug = drug,
            )
            consultant.process(req).collect { emit(it) }
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun retrieveTopChunks(
        cleanQuery: String,
        drug: String,
        population: Population,
    ): List<RankedChunk> = withContext(Dispatchers.Default) {
        val queryVec = embedderLock.withLock { embed(cleanQuery) }
        val bytes = floatsToBytes(queryVec)

        val primaryHits = dao.searchByPopulation(population, bytes, TOP_K)
        val populationHits = if (primaryHits.isEmpty() && population != Population.GENERAL) {
            dao.searchByPopulation(Population.GENERAL, bytes, TOP_K)
        } else {
            primaryHits
        }
        val generalHits = dao.searchByPopulation(Population.GENERAL, bytes, TOP_K)

        val merged = (populationHits + generalHits)
            .distinctBy { it.rowid }
            .sortedBy { it.distance }
            .take(TOP_K)

        if (merged.isEmpty()) return@withContext emptyList()
        val chunksById = dao.getChunksByIds(merged.map { it.rowid }).associateBy { it.rowid }
        val drugById = dao.getDrugsByIds(merged.mapNotNull { chunksById[it.rowid]?.drugRowid }.distinct())
            .associateBy { it.rowid }

        val drugLower = drug.lowercase()
        merged.mapNotNull { hit ->
            val chunk = chunksById[hit.rowid] ?: return@mapNotNull null
            val owner = drugById[chunk.drugRowid] ?: return@mapNotNull null
            val nameMatch = owner.drugName.equals(drug, ignoreCase = true)
                || owner.drugName.lowercase().contains(drugLower)
            val adjusted = if (nameMatch) hit.distance / HYBRID_BOOST else hit.distance
            RankedChunk(chunk = chunk, drug = owner, distance = adjusted)
        }
            .sortedBy { it.distance }
            .filter { it.drug.drugName.equals(drug, ignoreCase = true) || cachedDrugNames.size <= 1 }
            .ifEmpty {
                merged.mapNotNull { hit ->
                    val chunk = chunksById[hit.rowid] ?: return@mapNotNull null
                    val owner = drugById[chunk.drugRowid] ?: return@mapNotNull null
                    RankedChunk(chunk = chunk, drug = owner, distance = hit.distance)
                }
            }
    }

    private fun embed(text: String): FloatArray =
        embedder.embed(text).embeddingResult().embeddings().first().floatEmbedding()

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun floatsToBytes(floats: FloatArray): ByteArray =
        ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { floats.forEach(::putFloat) }
            .array()

    companion object {
        private const val TAG = "DrugRepository"
        private const val TOP_K = 10
        private const val HYBRID_BOOST = 1.25f
    }
}
