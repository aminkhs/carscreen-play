package com.cartvlab.android

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import com.cartvlab.mirroring.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class LabInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    @Test fun dashboardLaunchesAndAndroidAutoEntryPointsAreRegistered() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.text("VIDEO & YOUTUBE\nON YOUR CAR SCREEN")), 10000))
            val pm = context.packageManager
            // Video screen: Car App Library service in the navigation category (gets a car-screen surface).
            val carApp = Intent("androidx.car.app.CarAppService").addCategory("androidx.car.app.category.NAVIGATION").setPackage(context.packageName)
            assertEquals(1, pm.queryIntentServices(carApp, 0).size)
            // Media entry: card next to the map in Android Auto's split screen.
            val media = Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName)
            assertEquals(1, pm.queryIntentServices(media, 0).size)
        }
    }
    @Test fun bundledVideoRendersThenPausesAndSeeks() {
        val intent = Intent(context, MainActivity::class.java).putExtra("screen", "Watch Videos")
        ActivityScenario.launch<MainActivity>(intent).use {
            device.waitForIdle()
            if (!device.hasObject(By.text("Bundled test"))) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Bundled test"))
            val bundled = device.wait(Until.findObject(By.text("Bundled test")), 10000)
            assertNotNull("Bundled test control missing", bundled)
            bundled.click()
            // Observe actual ExoPlayer state via the activity's ViewModel, on its application thread.
            val deadline = SystemClock.elapsedRealtime() + 15000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                it.onActivity { activity ->
                    val vm = androidx.lifecycle.ViewModelProvider(activity)[LabViewModel::class.java]
                    ready = vm.player.state.value.ready && vm.player.player.videoSize.width > 0
                }
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("Media3 did not decode the bundled MP4", ready)
            it.onActivity { activity ->
                val vm = androidx.lifecycle.ViewModelProvider(activity)[LabViewModel::class.java]
                vm.player.player.pause()
                assertFalse(vm.player.player.playWhenReady)
                vm.player.player.seekTo(2000)
                assertEquals(2000L, vm.player.player.currentPosition)
            }
            device.setOrientationLeft()
            SystemClock.sleep(800)
            device.setOrientationNatural()
        }
    }
    @Test fun projectionConsentCaptureAndStopReleaseResources() {
        ActivityScenario.launch(MainActivity::class.java).use {
            device.wait(Until.hasObject(By.text("Watch Videos")), 10000)
            if (!device.hasObject(By.text("Screen Mirroring"))) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Screen Mirroring"))
            val mirror = device.wait(Until.findObject(By.text("Screen Mirroring")), 10000)
            assertNotNull(mirror); mirror.click()
            device.wait(Until.findObject(By.text("Start capture")), 5000).click()
            // Notification grant is independent of MediaProjection consent.
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))), 2000)?.click()
            val selector = device.wait(Until.findObject(By.text(Pattern.compile("(?i)(a single app|one app|share one app)"))), 2500)
            selector?.click()
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)(entire screen|your entire screen|share entire screen|share your entire screen)"))), 2500)?.click()
            val start = device.wait(Until.findObject(By.text(Pattern.compile("(?i)(start now|start|share screen|share)"))), 5000)
            if (start == null) device.findObjects(By.text(Pattern.compile(".+"))).forEach { node -> android.util.Log.e("ConsentUI", node.text) }
            assertNotNull("System capture consent button not found; manual verification required", start)
            start.click()
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (MirrorSessionManager.state.value.frames == 0L && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            assertTrue("No captured frames", MirrorSessionManager.state.value.frames > 0)
            val previousFrames = MirrorSessionManager.state.value.frames
            device.setOrientationLeft(); SystemClock.sleep(1000); device.setOrientationNatural()
            assertTrue("Capture stopped during rotation", MirrorSessionManager.state.value.active)
            assertTrue(MirrorSessionManager.state.value.frames >= previousFrames)
            AndroidMediaProjectionController(context).stop()
            val stopDeadline = SystemClock.elapsedRealtime() + 5000
            while (MirrorSessionManager.state.value.active && SystemClock.elapsedRealtime() < stopDeadline) SystemClock.sleep(100)
            assertFalse(MirrorSessionManager.state.value.active)
            assertNull(MirrorSessionManager.preview.value)
            assertFalse(context.getSystemService(android.hardware.display.DisplayManager::class.java).displays.any { display -> display.name == "CarScreen Play capture" })
        }
    }
    @Test fun exportsStructuredReportsWithoutMediaIdentifiers() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val jsonFile = java.io.File(context.cacheDir, "test-report.json")
            val txtFile = java.io.File(context.cacheDir, "test-report.txt")
            jsonFile.delete(); txtFile.delete()
            scenario.onActivity { activity ->
                val vm = androidx.lifecycle.ViewModelProvider(activity)[LabViewModel::class.java]
                vm.export(android.net.Uri.fromFile(jsonFile), true)
                vm.export(android.net.Uri.fromFile(txtFile), false)
            }
            val deadline = SystemClock.elapsedRealtime() + 8000
            while ((!jsonFile.exists() || !txtFile.exists() || txtFile.length() == 0L) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            val report = org.json.JSONObject(jsonFile.readText())
            assertEquals(37, report.getJSONObject("confirmedFacts").getInt("apiLevel"))
            assertEquals("UNKNOWN", report.getString("observedConnectionState"))
            assertTrue(report.has("actualTestResults"))
            assertFalse(jsonFile.readText().contains("test-pattern.mp4"))
            assertFalse(jsonFile.readText().contains("storage.googleapis.com"))
            assertTrue(txtFile.readText().contains("UNKNOWN"))
        }
    }
    @After fun cleanup() { AndroidMediaProjectionController(context).stop(); device.setOrientationNatural() }
}
