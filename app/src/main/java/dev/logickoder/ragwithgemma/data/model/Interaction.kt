package dev.logickoder.ragwithgemma.data.model

enum class Severity(val title: String, val bucketOrder: Int) {
    CONTRAINDICATED("Contraindicated", 3),
    SERIOUS("Serious – Use Alternative", 2),
    MONITOR("Monitor Closely", 1),
    MINOR("Minor", 0);

    companion object {
        fun fromStrengthId(strength: Int?): Severity = when (strength) {
            5, 6, 21, 33 -> CONTRAINDICATED
            4, 12, 20, 22 -> SERIOUS
            2, 3, 10, 11 -> MONITOR
            else -> MINOR
        }
    }
}

data class Interaction(
    val interactionId: Int,
    val subjectName: String?,
    val objectName: String?,
    val direction: String?,
    val effect: String?,
    val strength: Int?,
    val comment: String?,
    val mechScript: String?,
    val aiText: String = "",
) {
    val severity: Severity get() = Severity.fromStrengthId(strength)
}
