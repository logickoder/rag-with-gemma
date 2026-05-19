package dev.logickoder.ragwithgemma.data.model

data class ChunkHit(val rowid: Long, val distance: Float)

data class RankedChunk(
    val chunk: DrugChunk,
    val drug: Drug,
    val distance: Float,
)
