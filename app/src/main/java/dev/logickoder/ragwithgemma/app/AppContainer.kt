package dev.logickoder.ragwithgemma.app

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dev.logickoder.ragwithgemma.data.AppDatabase
import dev.logickoder.ragwithgemma.data.DrugDao
import dev.logickoder.ragwithgemma.data.prefs.UserPrefs
import dev.logickoder.ragwithgemma.data.source.AssetBootstrap
import dev.logickoder.ragwithgemma.domain.InteractionRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val prefs: UserPrefs = UserPrefs(appContext)
    val bootstrap: AssetBootstrap = AssetBootstrap(appContext)
    val db: AppDatabase = AppDatabase.getDatabase(appContext)
    val dao: DrugDao get() = db.drugDao()
    val interactionRepo = InteractionRepository()

    @Volatile
    var embedder: TextEmbedder? = null
        private set

    suspend fun ensureEmbedder(): TextEmbedder {
        embedder?.let { return it }
        val embedderFile = bootstrap.resolveEmbedder()
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(embedderFile.path).build())
            .build()
        val created = TextEmbedder.createFromOptions(appContext, options)
        embedder = created
        return created
    }
}
