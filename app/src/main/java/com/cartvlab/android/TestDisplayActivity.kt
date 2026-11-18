package com.cartvlab.android

import android.content.Intent
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.cartvlab.core.common.EvidenceJournal
import com.cartvlab.core.designsystem.LabTheme
import com.cartvlab.core.model.RenderStats
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint class TestDisplayActivity : ComponentActivity() {
    @Inject lateinit var journal: EvidenceJournal
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val id = windowManager.defaultDisplay.displayId
        if (id != Display.DEFAULT_DISPLAY) { finish(); return }
        setContent { LabTheme {
            var frames by remember { mutableLongStateOf(0) }
            var fps by remember { mutableDoubleStateOf(0.0) }
            var width by remember { mutableIntStateOf(0) }
            var height by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                val start = withFrameNanos { it }
                while (true) withFrameNanos { now ->
                    frames++
                    fps = frames * 1_000_000_000.0 / (now - start).coerceAtLeast(1)
                    if (frames % 30L == 0L) journal.renderStats.value = RenderStats(frames, fps, width, height)
                }
            }
            Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PHONE DISPLAY TEST", color = Color(0xFF1FD8D9), style = MaterialTheme.typography.headlineSmall)
                Text("Display $id · ${width} × ${height} px test surface\nFrame $frames · %.1f fps".format(fps), color = Color.White)
                Text("Connection: UNKNOWN. This is not proof of Android Auto video support.", color = Color.White)
                Canvas(Modifier.fillMaxWidth().weight(1f).onSizeChanged { width = it.width; height = it.height }) {
                    val colors = listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Blue)
                    colors.forEachIndexed { i, color -> drawRect(color, Offset(i * size.width / 7, 0f), Size(size.width / 7, size.height)) }
                    val x = (frames % 180) / 180f * size.width
                    drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), 6f)
                }
                Button(onClick = {
                    setResult(android.app.Activity.RESULT_OK)
                    finish()
                }) { Text("Open video output test") }
                TextButton(onClick = { finish() }) { Text("Back") }
            }
        } }
    }
}
