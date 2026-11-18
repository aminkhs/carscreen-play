package com.cartvlab.diagnostics

import android.content.Context
import android.os.Build
import com.cartvlab.automotive.VehicleDisplayManager
import org.json.JSONArray
import org.json.JSONObject

interface DeviceInfoRepository { fun facts(displayId: Int): JSONObject }
class AndroidDeviceInfoRepository(private val context: Context, private val displays: VehicleDisplayManager) : DeviceInfoRepository {
    private fun version(packageName: String): String = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (_: Exception) { "UNKNOWN: package not installed or not visible" }
    override fun facts(displayId: Int): JSONObject = JSONObject()
        .put("androidVersion", Build.VERSION.RELEASE).put("apiLevel", Build.VERSION.SDK_INT)
        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        .put("androidAutoVersion", version("com.google.android.projection.gearhead"))
        .put("googlePlayServicesVersion", version("com.google.android.gms"))
        .put("currentActivityDisplayId", displayId)
        .put("displays", JSONArray().apply { displays.displays().forEach { d -> put(JSONObject()
            .put("displayId", d.id).put("width", d.width).put("height", d.height).put("refreshHz", d.refresh)
            .put("state", d.state).put("external", d.external).put("supportedModes", JSONArray(d.modes))) } })
        .put("externalDisplayAvailable", displays.displays().any { it.external })
        .put("appBuild", version(context.packageName))
}
