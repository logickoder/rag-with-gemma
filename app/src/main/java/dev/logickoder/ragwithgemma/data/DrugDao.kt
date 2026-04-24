package dev.logickoder.ragwithgemma.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import dev.logickoder.ragwithgemma.data.model.DrugMetadata

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
        rowIds.forEachIndexed { index, id ->
            insertEmbedding(id, embeddings[index])
        }
    }

    @Query("SELECT COUNT(*) FROM drug_metadata")
    suspend fun getDrugCount(): Int

    @SkipQueryVerification
    @Query("SELECT sqlite_version()")
    suspend fun sqliteVersion(): String

    @SkipQueryVerification
    @Query("SELECT name FROM pragma_table_xinfo('vss_drug_embeddings')")
    suspend fun vecTableColumns(): List<String>

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid FROM vss_drug_embeddings
        WHERE indicationEmbedding MATCH (SELECT indicationEmbedding FROM vss_drug_embeddings LIMIT 1)
        ORDER BY distance LIMIT 3
        """
    )
    suspend fun smokeKnn(): List<Long>

    @SkipQueryVerification
    @Query("SELECT name FROM pragma_module_list WHERE name LIKE 'vec%'")
    suspend fun vecModules(): List<String>

    @SkipQueryVerification
    @Query("SELECT sql FROM sqlite_master WHERE name='vss_drug_embeddings'")
    suspend fun vecTableSql(): String?

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid FROM vss_drug_embeddings
        WHERE indicationEmbedding MATCH vec_f32(:jsonVec)
        ORDER BY distance LIMIT 3
        """
    )
    suspend fun smokeKnnVecF32(jsonVec: String): List<Long>


    @SkipQueryVerification
    @Query(
        """
        SELECT rowid FROM vss_drug_embeddings
        WHERE indicationEmbedding MATCH :queryEmbedding
        ORDER BY distance
        LIMIT :topK
        """
    )
    suspend fun searchVectorIds(queryEmbedding: ByteArray, topK: Int): List<Long>

    @Query("SELECT * FROM drug_metadata WHERE rowid IN (:ids)")
    suspend fun getMetadataByIds(ids: List<Long>): List<DrugMetadata>

    @Transaction
    suspend fun searchSemantically(queryEmbedding: ByteArray, topK: Int): List<DrugMetadata> {
        val ids = searchVectorIds(queryEmbedding, topK)
        if (ids.isEmpty()) return emptyList()
        val byId = getMetadataByIds(ids).associateBy { it.rowid }
        return ids.mapNotNull { byId[it] }
    }
}