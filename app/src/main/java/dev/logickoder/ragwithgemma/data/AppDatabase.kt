package dev.logickoder.ragwithgemma.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.logickoder.ragwithgemma.data.model.Drug
import dev.logickoder.ragwithgemma.data.model.DrugChunk
import dev.logickoder.ragwithgemma.data.model.Population


@Database(entities = [Drug::class, DrugChunk::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao

    suspend fun createVecTables() = useWriterConnection { conn ->
        for (pop in Population.entries) {
            conn.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS ${vecTableName(pop)} " +
                    "USING vec0(embedding float[$EMBEDDING_DIM])"
            )
        }
    }

    companion object {
        const val EMBEDDING_DIM = 512

        fun vecTableName(population: Population): String = "vss_chunks_${population.tableSuffix}"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val driver = BundledSQLiteDriver()
                driver.addExtension("libsqlite_vec.so", "sqlite3_vec_init")

                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medical_rag.db"
                )
                    .setDriver(driver)
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
