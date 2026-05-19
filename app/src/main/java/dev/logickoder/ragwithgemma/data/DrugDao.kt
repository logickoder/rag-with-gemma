package dev.logickoder.ragwithgemma.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import dev.logickoder.ragwithgemma.data.model.ChunkHit
import dev.logickoder.ragwithgemma.data.model.Drug
import dev.logickoder.ragwithgemma.data.model.DrugChunk
import dev.logickoder.ragwithgemma.data.model.Population

@Dao
interface DrugDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrug(drug: Drug): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DrugChunk>): List<Long>

    @SkipQueryVerification
    @Query("INSERT INTO vss_chunks_adult(rowid, embedding) VALUES (:rowId, :embedding)")
    suspend fun insertEmbeddingAdult(rowId: Long, embedding: ByteArray)

    @SkipQueryVerification
    @Query("INSERT INTO vss_chunks_pediatric(rowid, embedding) VALUES (:rowId, :embedding)")
    suspend fun insertEmbeddingPediatric(rowId: Long, embedding: ByteArray)

    @SkipQueryVerification
    @Query("INSERT INTO vss_chunks_geriatric(rowid, embedding) VALUES (:rowId, :embedding)")
    suspend fun insertEmbeddingGeriatric(rowId: Long, embedding: ByteArray)

    @SkipQueryVerification
    @Query("INSERT INTO vss_chunks_general(rowid, embedding) VALUES (:rowId, :embedding)")
    suspend fun insertEmbeddingGeneral(rowId: Long, embedding: ByteArray)

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid, distance FROM vss_chunks_adult
        WHERE embedding MATCH :query
        ORDER BY distance LIMIT :topK
        """
    )
    suspend fun searchAdult(query: ByteArray, topK: Int): List<ChunkHit>

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid, distance FROM vss_chunks_pediatric
        WHERE embedding MATCH :query
        ORDER BY distance LIMIT :topK
        """
    )
    suspend fun searchPediatric(query: ByteArray, topK: Int): List<ChunkHit>

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid, distance FROM vss_chunks_geriatric
        WHERE embedding MATCH :query
        ORDER BY distance LIMIT :topK
        """
    )
    suspend fun searchGeriatric(query: ByteArray, topK: Int): List<ChunkHit>

    @SkipQueryVerification
    @Query(
        """
        SELECT rowid, distance FROM vss_chunks_general
        WHERE embedding MATCH :query
        ORDER BY distance LIMIT :topK
        """
    )
    suspend fun searchGeneral(query: ByteArray, topK: Int): List<ChunkHit>

    @Query("SELECT * FROM drug_chunk WHERE rowid IN (:ids)")
    suspend fun getChunksByIds(ids: List<Long>): List<DrugChunk>

    @Query("SELECT * FROM drug_chunk WHERE drugRowid = :drugRowid")
    suspend fun getAllChunksForDrug(drugRowid: Long): List<DrugChunk>

    @Query("SELECT * FROM drug WHERE rowid IN (:ids)")
    suspend fun getDrugsByIds(ids: List<Long>): List<Drug>

    @Query("SELECT * FROM drug WHERE drugName LIKE :name COLLATE NOCASE LIMIT 1")
    suspend fun getDrugByName(name: String): Drug?

    @Query("SELECT drugName FROM drug")
    suspend fun getAllDrugNames(): List<String>

    @Query("SELECT COUNT(*) FROM drug")
    suspend fun getDrugCount(): Int

    @Transaction
    suspend fun insertDrugWithChunks(
        drug: Drug,
        chunksWithEmbeddings: List<Pair<DrugChunk, ByteArray>>,
    ): Long {
        val drugRowid = insertDrug(drug)
        val chunks = chunksWithEmbeddings.map { it.first.copy(drugRowid = drugRowid) }
        val rowIds = insertChunks(chunks)
        rowIds.forEachIndexed { index, id ->
            val pop = Population.fromLabel(chunks[index].population)
            val emb = chunksWithEmbeddings[index].second
            when (pop) {
                Population.ADULT -> insertEmbeddingAdult(id, emb)
                Population.PEDIATRIC -> insertEmbeddingPediatric(id, emb)
                Population.GERIATRIC -> insertEmbeddingGeriatric(id, emb)
                Population.GENERAL -> insertEmbeddingGeneral(id, emb)
            }
        }
        return drugRowid
    }

    suspend fun searchByPopulation(
        population: Population,
        query: ByteArray,
        topK: Int,
    ): List<ChunkHit> = when (population) {
        Population.ADULT -> searchAdult(query, topK)
        Population.PEDIATRIC -> searchPediatric(query, topK)
        Population.GERIATRIC -> searchGeriatric(query, topK)
        Population.GENERAL -> searchGeneral(query, topK)
    }
}
