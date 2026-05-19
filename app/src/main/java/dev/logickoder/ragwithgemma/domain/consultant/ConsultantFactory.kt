package dev.logickoder.ragwithgemma.domain.consultant

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dev.logickoder.ragwithgemma.data.prefs.ConsultantMode
import dev.logickoder.ragwithgemma.data.source.AssetBootstrap

data class ConsultantBuildResult(
    val consultant: Consultant,
    val effectiveMode: ConsultantMode,
    val fellBackToSemantic: Boolean,
)

object ConsultantFactory {

    suspend fun create(
        context: Context,
        bootstrap: AssetBootstrap,
        embedder: TextEmbedder,
        requested: ConsultantMode,
    ): ConsultantBuildResult {
        if (requested == ConsultantMode.LLM) {
            return if (bootstrap.gemmaModelExists()) {
                val modelFile = bootstrap.resolveGemmaModel()
                val llm = LlmConsultant(context, modelFile)
                llm.warmup()
                ConsultantBuildResult(llm, ConsultantMode.LLM, fellBackToSemantic = false)
            } else {
                Log.w(TAG, "LLM requested but Gemma model missing — falling back to semantic")
                ConsultantBuildResult(
                    SemanticConsultant(embedder),
                    ConsultantMode.SEMANTIC,
                    fellBackToSemantic = true,
                )
            }
        }
        return ConsultantBuildResult(
            SemanticConsultant(embedder),
            ConsultantMode.SEMANTIC,
            fellBackToSemantic = false,
        )
    }

    private const val TAG = "ConsultantFactory"
}
