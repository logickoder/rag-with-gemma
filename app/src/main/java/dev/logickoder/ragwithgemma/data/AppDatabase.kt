package dev.logickoder.ragwithgemma.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.logickoder.ragwithgemma.data.model.DrugMetadata


@Database(entities = [DrugMetadata::class], version = 9, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao

    suspend fun createVecTable() = useWriterConnection { conn ->
        conn.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS vss_drug_embeddings USING vec0(indicationEmbedding float[512])")
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
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