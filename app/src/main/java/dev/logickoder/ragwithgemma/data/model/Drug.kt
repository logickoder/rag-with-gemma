package dev.logickoder.ragwithgemma.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drug",
    indices = [Index(value = ["drugName"], unique = false), Index(value = ["medscapeId"], unique = true)],
)
data class Drug(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val medscapeId: String,
    val drugName: String,
    val brandName: String?,
    val drugClass: String?,
    val availability: String?,
    @ColumnInfo(name = "type") val type: String?,
)
