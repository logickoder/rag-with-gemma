package dev.logickoder.ragwithgemma.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import dev.logickoder.ragwithgemma.data.model.DrugMetadata
import dev.logickoder.ragwithgemma.data.model.DrugWithDistance
import dev.logickoder.ragwithgemma.data.model.VectorHit

@Dao
interface DrugDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: List<DrugMetadata>): List<Long>

    @SkipQueryVerification
    @Query("INSERT INTO vss_drug_embeddings(rowid, indicationEmbedding) VALUES (:rowId, :embedding)")
    suspend fun insertEmbedding(rowId: Long, embedding: ByteArray)

    @Transaction
    suspend fun insertHybridData(metadata: List<DrugMetadata>, embeddings: List<ByteArray>) {
        val rowIds = insertMetadata(metadata)
        rowIds.forEachIndexed { index, id -> insertEmbedding(id, embeddings[index]) }
    }

    @Query("SELECT COUNT(*) FROM drug_metadata")
    suspend fun getDrugCount(): Int

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid, distance FROM vss_drug_embeddings
        WHERE indicationEmbedding MATCH :queryEmbedding
        ORDER BY distance LIMIT :topK
        """
    )
    suspend fun searchVectorHits(queryEmbedding: ByteArray, topK: Int): List<VectorHit>

    @Query("SELECT * FROM drug_metadata WHERE rowid IN (:ids)")
    suspend fun getMetadataByIds(ids: List<Long>): List<DrugMetadata>

    @Transaction
    suspend fun searchWithDistance(queryEmbedding: ByteArray, topK: Int): List<DrugWithDistance> {
        val hits = searchVectorHits(queryEmbedding, topK)
        if (hits.isEmpty()) return emptyList()
        val byId = getMetadataByIds(hits.map { it.rowid }).associateBy { it.rowid }
        return hits.mapNotNull { hit ->
            byId[hit.rowid]?.let {
                DrugWithDistance(
                    hit.rowid,
                    hit.distance,
                    it.brandName,
                    it.genericName,
                    it.indications
                )
            }
        }
    }
}
