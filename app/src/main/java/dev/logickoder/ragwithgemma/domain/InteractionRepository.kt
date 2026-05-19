package dev.logickoder.ragwithgemma.domain

import dev.logickoder.ragwithgemma.data.model.Interaction

class InteractionRepository {
    suspend fun findInteractions(drugA: String, drugB: String): Result<List<Interaction>> {
        return Result.failure(
            NotImplementedError(
                "Drug-drug interaction module is pending a separate dataset. " +
                    "Switch to AI Chat to query single-drug clinical info."
            )
        )
    }
}
