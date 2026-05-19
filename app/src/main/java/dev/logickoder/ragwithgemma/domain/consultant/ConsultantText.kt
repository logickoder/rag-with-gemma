package dev.logickoder.ragwithgemma.domain.consultant

private val SCRUB_RE = Regex("""<(?:start_of_turn|end_of_turn|eos|channel\|?|turn\|?)>""")

internal val STOP_MARKERS = listOf("<end_of_turn>", "<eos>")
internal const val MAX_MARKER_LEN = 16

internal val STRUCTURAL_MARKERS = listOf(
    "[OUTPUT]",
    "SUMMARY:",
    "Instruction:",
    "[DATA]",
    "###",
    "Task:",
)

internal val FILLER_PREFIXES = listOf(
    "Sure, here is",
    "Based on the",
    "The data indicates",
    "Certainly",
    "Summary:",
)

internal val CRITICAL_HEADERS = listOf(
    "DOSAGE",
    "STRENGTHS",
    "CONTRAINDICATIONS",
    "WARNINGS",
    "RENAL",
    "PEDIATRIC",
    "BLACK BOX",
)

internal fun scrubTokens(s: String): String = SCRUB_RE.replace(s, "")

internal fun stripStructuralMarkers(input: String): String {
    var s = input
    for (marker in STRUCTURAL_MARKERS) {
        val idx = s.lastIndexOf(marker)
        if (idx >= 0) s = s.substring(idx + marker.length)
    }
    return s
}

internal fun stripFillerPrefixes(input: String): String {
    var s = input.trim()
    for (filler in FILLER_PREFIXES) {
        if (s.regionMatches(0, filler, 0, filler.length, ignoreCase = true)) {
            s = s.substring(filler.length).trimStart(',', ':', ' ', '\t')
        }
    }
    return s
}

internal fun repetitionGuard(input: String): String {
    val idx = input.indexOf("DOSAGE FORMS", ignoreCase = false)
    if (idx > 100) return input.substring(0, idx)
    return input
}

internal data class StructuredSection(val key: String, val value: String)

internal fun parseStructuredSections(lines: List<String>): List<StructuredSection> {
    val sections = mutableListOf<StructuredSection>()
    var currentHeader: String? = null
    val buffer = StringBuilder()

    fun flush() {
        val header = currentHeader ?: return
        val value = buffer.toString().trim()
        if (value.isNotEmpty()) sections += StructuredSection(header, value)
        buffer.clear()
    }

    for (raw in lines) {
        val line = raw.trim()
        if (isLikelySectionHeader(line)) {
            flush()
            currentHeader = line
        } else if (line.isNotEmpty()) {
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(line)
        }
    }
    flush()
    return sections
}

internal fun isLikelySectionHeader(line: String): Boolean {
    if (line.length !in 4..49) return false
    return line.uppercase() == line
}

internal fun appendMissingCriticalSections(
    sections: List<StructuredSection>,
    generated: String,
    mode: AIProcessorMode,
): String {
    val generatedUpper = generated.uppercase()
    val missing = sections.filter { section ->
        val header = section.key.uppercase()
        val isCritical = CRITICAL_HEADERS.any { header.contains(it) }
        isCritical && !generatedUpper.contains(header)
    }
    if (missing.isEmpty()) return generated

    val separator = if (mode == AIProcessorMode.SUMMARIZE) "\n\n---\n" else "\n\n### TECHNICAL DETAILS\n"
    val appendix = missing.joinToString("\n") { "**${it.key}**: ${it.value}" }
    return generated + separator + appendix
}

internal fun deterministicLabelFormat(input: String, mode: AIProcessorMode): String {
    val lines = input.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return input
    val sections = parseStructuredSections(lines)

    if (sections.isEmpty()) {
        return if (mode == AIProcessorMode.SUMMARIZE) {
            lines.take(2).joinToString(". ")
        } else {
            lines.joinToString("\n")
        }
    }

    return when (mode) {
        AIProcessorMode.SUMMARIZE -> sections.take(2).joinToString(". ") { "${it.key}: ${it.value}" }
        AIProcessorMode.EXPAND -> sections.joinToString("\n\n") { "**${it.key}**\n${it.value}" }
    }
}

internal fun cleanFinal(raw: String, mode: AIProcessorMode, sourceInput: String): String {
    var s = scrubTokens(raw)
    s = stripStructuralMarkers(s)
    s = stripFillerPrefixes(s)
    s = repetitionGuard(s).trim().trim(':', ',', ' ', '\n')

    if (s.length < 15) return deterministicLabelFormat(sourceInput, mode)

    val sourceSections = parseStructuredSections(sourceInput.lines().filter { it.isNotBlank() })
    return appendMissingCriticalSections(sourceSections, s, mode)
}
