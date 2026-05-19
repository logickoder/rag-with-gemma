package dev.logickoder.ragwithgemma.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drug_chunk",
    foreignKeys = [
        ForeignKey(
            entity = Drug::class,
            parentColumns = ["rowid"],
            childColumns = ["drugRowid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["drugRowid"]),
        Index(value = ["population"]),
        Index(value = ["sectionLabel"]),
    ],
)
data class DrugChunk(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val drugRowid: Long,
    val population: String,
    val sectionLabel: String,
    val subHeader: String?,
    val tag: String,
    val text: String,
)
