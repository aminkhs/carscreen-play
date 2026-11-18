package com.cartvlab.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Cyan = Color(0xFF1FD8D9)
@Composable fun LabTheme(theme: String = "Dark", content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Cyan, onPrimary = Color(0xFF00292A),
            background = Color(0xFF101418), surface = Color(0xFF191F25), surfaceVariant = Color(0xFF242E36), surfaceContainer = Color(0xFF191F25),
            surfaceContainerHigh = Color(0xFF222A32), surfaceContainerHighest = Color(0xFF28333D))
        else lightColorScheme(primary = Color(0xFF006B6C)),
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}
@Composable fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.headlineSmall) }
