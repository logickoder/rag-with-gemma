package dev.logickoder.ragwithgemma.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.logickoder.ragwithgemma.data.model.ChatMessage
import dev.logickoder.ragwithgemma.data.model.ChatRole

@Composable
fun ChatBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == ChatRole.USER
    val container = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(container)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val lines = message.content.split('\n')
            for ((index, raw) in lines.withIndex()) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> Spacer(Modifier.height(4.dp))
                    line.startsWith("#### ") -> Text(
                        text = line.removePrefix("#### "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    line.startsWith("### ") -> Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    line.startsWith("- ") -> Text(
                        text = "• ${line.removePrefix("- ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    line.startsWith("**") && line.endsWith("**") -> Text(
                        text = line.removeSurrounding("**"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    else -> Text(line, style = MaterialTheme.typography.bodyMedium)
                }
                if (index != lines.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
    }
}
