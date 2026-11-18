package com.cartvlab.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cartvlab.mirroring.MirrorSessionManager
import org.json.JSONObject

interface PermissionInspector { fun inspect(): JSONObject }
class AndroidPermissionInspector(private val context: Context) : PermissionInspector {
    override fun inspect() = JSONObject()
        .put("notifications", if (Build.VERSION.SDK_INT < 33) "NOT_REQUIRED"
            else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "NOT_GRANTED")
        .put("localNetwork", if (Build.VERSION.SDK_INT < 37) "NOT_REQUIRED" else if (context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "NOT_GRANTED")
        .put("mediaProjection", if (MirrorSessionManager.state.value.active) "ACTIVE_SESSION" else "FRESH_CONSENT_REQUIRED")
        .put("foregroundServiceMediaProjection", "DECLARED")
        .put("internet", "DECLARED")
        .put("storage", "SAF_DOCUMENT_GRANTS_ONLY")
        .put("broadStorage", "NOT_REQUESTED")
        .put("microphone", "NOT_REQUESTED")
}
