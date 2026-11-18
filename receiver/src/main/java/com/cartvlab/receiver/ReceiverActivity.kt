package com.cartvlab.receiver

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cartvlab.core.designsystem.LabTheme
import java.net.NetworkInterface
import java.security.SecureRandom

class ReceiverActivity : ComponentActivity() {
    private var server: ReceiverServer? = null
    private var surface: Surface? = null
    private var message by mutableStateOf("Preparing receiver…")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startReceiver() else message = "Local-network access denied. Grant permission to accept a sender."
    }
    private val code = "%08d".format(SecureRandom().nextInt(100_000_000))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val addresses = runCatching { NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filter { it.isSiteLocalAddress && it.hostAddress?.contains(':') == false }.joinToString { it.hostAddress.orEmpty() } }.getOrDefault("UNKNOWN")
        setContent { LabTheme {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LAB RECEIVER", style = MaterialTheme.typography.headlineMedium)
                Text("IP: $addresses · TCP 52744\nPairing code: $code", style = MaterialTheme.typography.titleLarge)
                Text(message)
                if (Build.VERSION.SDK_INT >= 37) OutlinedButton(onClick = { permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) { Text("Allow local network") }
                Text("Separate Android receiver, not Android Auto. Video only, unencrypted trusted LAN. Leaving this screen stops reception.")
                AndroidView(factory = { SurfaceView(it).apply {
                    keepScreenOn = true
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface; startReceiver() }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) { stopReceiver(); surface = null }
                    })
                } }, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        } }
    }
    private fun startReceiver() {
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            message = "Allow local-network access before connecting a sender."; return
        }
        val target = surface ?: return
        if (server == null && target.isValid) server = ReceiverServer(target, code) { value -> runOnUiThread { message = value } }
    }
    private fun stopReceiver() { server?.close(); server = null }
    override fun onStart() { super.onStart(); startReceiver() }
    override fun onStop() { stopReceiver(); super.onStop() }
}
