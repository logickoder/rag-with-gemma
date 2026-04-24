package dev.logickoder.ragwithgemma.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drug_metadata")
data class DrugMetadata(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val brandName: String,
    val genericName: String,
    val indications: String
)