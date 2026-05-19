package dev.logickoder.ragwithgemma.domain.consultant

import android.util.Log
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SemanticConsultant(
    private val embedder: TextEmbedder,
) : Consultant {

    override fun process(request: ConsultantRequest): Flow<ConsultantEvent> = flow {
        val rendered = render(request)
        emit(ConsultantEvent.Final(rendered))
    }.flowOn(Dispatchers.Default)

    private fun render(request: ConsultantRequest): String {
        val sections = buildSectionMap(request.context)
        if (sections.isEmpty()) {
            return deterministicLabelFormat(request.context, request.mode)
                .let { applyAppendix(it, request) }
        }

        val intent = buildIntent(request)
        val intentVec = runCatching { embed(intent) }.getOrNull()

        val perSectionK = if (request.mode == AIProcessorMode.SUMMARIZE) K_SUMMARIZE else K_EXPAND
        val populationLine = request.population.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""

        val rendered = buildString {
            append("### ")
            append(request.drug.ifBlank { "Drug" })
            append(populationLine)
            append('\n')
            for ((label, sentences) in sections) {
                val ranked = if (intentVec != null) rank(sentences, intentVec, perSectionK) else sentences.take(perSectionK)
                if (ranked.isEmpty()) continue
                append("\n#### ")
                appendLine(label)
                for (s in ranked) {
                    append("- ")
                    appendLine(s.trim().trim('.', ',', ' '))
                }
            }
        }
        return applyAppendix(rendered.trim(), request)
    }

    private fun applyAppendix(text: String, request: ConsultantRequest): String {
        val sourceSections = parseStructuredSections(request.context.lines().filter { it.isNotBlank() })
        return appendMissingCriticalSections(sourceSections, text, request.mode)
    }

    private fun buildSectionMap(input: String): LinkedHashMap<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        var currentLabel = "Details"
        val sentenceSplit = Regex("(?<=[.!?])\\s+(?=[A-Z0-9])")

        for (raw in input.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (isSectionHeaderLine(line)) {
                currentLabel = line.trimStart('#', ' ').trim()
                out.getOrPut(currentLabel) { mutableListOf() }
                continue
            }
            val bucket = out.getOrPut(currentLabel) { mutableListOf() }
            line.split(sentenceSplit)
                .map { it.trim() }
                .filter { it.length >= MIN_SENT_LEN }
                .forEach { bucket += it }
        }
        return LinkedHashMap(out.mapValues { it.value.toList() })
    }

    private fun isSectionHeaderLine(line: String): Boolean {
        if (line.startsWith("DRUG:")) return false
        if (line.length in 4..49 && line.uppercase() == line) return true
        if (line.contains(" — ") && line.length < 80) return true
        return false
    }

    private fun buildIntent(request: ConsultantRequest): String {
        val drug = request.drug.ifBlank { "this drug" }
        val population = request.population.ifBlank { "general" }
        return when (request.mode) {
            AIProcessorMode.SUMMARIZE ->
                "Key clinical facts, dosage, and contraindications for $drug in $population patients"
            AIProcessorMode.EXPAND ->
                "Main clinical indications, dosage forms, and primary strengths for $drug in $population patients"
        }
    }

    private fun rank(sentences: List<String>, intentVec: FloatArray, k: Int): List<String> {
        if (sentences.size <= k) return sentences
        val scored = sentences.map { s ->
            val v = runCatching { embed(s) }.getOrNull()
            val sim = if (v != null) cosine(intentVec, v) else 0f
            s to sim
        }
        Log.d(TAG, "Top sim: ${scored.maxByOrNull { it.second }?.second}")
        return scored.sortedByDescending { it.second }.take(k).map { it.first }
    }

    private fun embed(text: String): FloatArray =
        embedder.embed(text).embeddingResult().embeddings().first().floatEmbedding()

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }

    companion object {
        private const val TAG = "SemanticConsultant"
        private const val MIN_SENT_LEN = 12
        private const val K_SUMMARIZE = 2
        private const val K_EXPAND = 4
    }
}
