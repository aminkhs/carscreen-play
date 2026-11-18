package com.cartvlab.automotive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cartvlab.core.designsystem.InfoCard

@Composable fun ParkedExperimentScreen(flag: Boolean, testDisplay: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Parked video experiment", style = MaterialTheme.typography.headlineMedium)
        InfoCard("NOT_SUPPORTED_BY_CURRENT_PUBLIC_API", ParkedVideoExperiment.MESSAGE)
        Text("Feature flag: ${if (flag) "enabled; eligibility still required" else "disabled"}. No vehicle activity is exposed.")
        Text("Android Auto runs from your phone. Android Automotive OS runs in a vehicle. Android 17 alone does not make a video app eligible for Android Auto.")
        Text("Announcements and early-access interest do not establish an available API or enrollment for this app. Recheck official documentation before changing the build.")
        Button(onClick = testDisplay) { Text("Open phone test pattern") }
        Text("A phone test pattern measures rendering only. It cannot verify vehicle video playback.")
    }
}
