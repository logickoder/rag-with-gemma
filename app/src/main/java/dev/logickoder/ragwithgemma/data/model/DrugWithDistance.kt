package dev.logickoder.ragwithgemma.data.model

data class DrugWithDistance(
    val rowid: Long,
    val distance: Float,
    val brandName: String,
    val genericName: String,
    val indications: String,
)