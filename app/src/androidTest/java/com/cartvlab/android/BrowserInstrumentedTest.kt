package com.cartvlab.android

import android.content.Intent
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import com.cartvlab.browser.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BrowserInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun launch() = ActivityScenario.launch<BrowserActivity>(Intent(instrumentation.targetContext, BrowserActivity::class.java))
    private fun waitUntil(message: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 12000
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
    private fun js(scenario: ActivityScenario<BrowserActivity>, script: String): String {
        val done = CountDownLatch(1)
        val value = AtomicReference("")
        scenario.onActivity { it.engine.view.evaluateJavascript(script) { result -> value.set(result); done.countDown() } }
        assertTrue("JavaScript callback timed out", done.await(5, TimeUnit.SECONDS))
        return value.get()
    }
    private fun ready(scenario: ActivityScenario<BrowserActivity>) {
        waitUntil("Offline browser page did not load") { js(scenario, "document.readyState") == "\"complete\"" &&
            js(scenario, "document.title") == "\"Offline browser test\"" }
    }
    @Test fun offlinePageJavascriptHistoryAndRotation() {
        launch().use { scenario ->
            scenario.onActivity { it.engine.open(BrowserUrlPolicy.HELP) }
            ready(scenario)
            assertEquals("\"JavaScript works\"", js(scenario, "document.getElementById('check').click(); document.getElementById('result').textContent"))
            scenario.onActivity {
                assertFalse(it.engine.view.settings.allowFileAccess)
                assertFalse(it.engine.view.settings.allowContentAccess)
                it.engine.open("https://appassets.androidplatform.net/assets/browser-about.html")
            }
            waitUntil("Second page did not load") { js(scenario, "document.title") == "\"Browser navigation test\"" }
            scenario.onActivity { assertTrue(it.engine.view.canGoBack()); it.engine.back() }
            ready(scenario)
            scenario.recreate()
            ready(scenario)
            scenario.onActivity { assertTrue("Restored page hidden by home screen", it.engine.state.value.hasPage) }
        }
    }
    @Test fun htmlVideoPlaysFullscreenAndPauses() {
        launch().use { scenario ->
            scenario.onActivity { it.engine.open(BrowserUrlPolicy.HELP) }
            ready(scenario)
            device.waitForIdle()
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Play test video"))
            device.wait(Until.findObject(By.text("Play test video")), 5000).click()
            waitUntil("HTML5 video did not play") { js(scenario, "document.getElementById('sample').currentTime > 0.2") == "true" }
            device.wait(Until.findObject(By.text("Fullscreen test video")), 5000).click()
            waitUntil("Web fullscreen callback did not fire") {
                var full = false
                scenario.onActivity { full = it.engine.fullscreen.value != null }
                full
            }
            scenario.onActivity { it.engine.exitFullscreen(); it.engine.pause() }
            waitUntil("Browser video did not pause") { js(scenario, "document.getElementById('sample').paused") == "true" }
        }
    }
    @Test fun googleLoginLoadsInsideWebviewAndCookiesCanBeCleared() {
        launch().use { scenario ->
            scenario.onActivity { it.engine.open(BrowserUrlPolicy.HELP) }
            ready(scenario)
            var userAgent = ""
            scenario.onActivity { userAgent = it.engine.view.settings.userAgentString }
            assertFalse("WebView marker must be removed so Google sign-in is offered", userAgent.contains("; wv"))
            js(scenario, "document.querySelector('a[href*=\"accounts.google.com\"]').click()")
            waitUntil("Google sign-in did not load inside the embedded browser (shared with the car screen)") {
                var inside = false
                scenario.onActivity { inside = it.engine.state.value.url.contains("accounts.google.com") }
                inside
            }
            scenario.onActivity {
                CookieManager.getInstance().setCookie(BrowserUrlPolicy.HELP, "browser_test=1")
                it.engine.clearData()
            }
            waitUntil("Embedded cookies were not cleared") { CookieManager.getInstance().getCookie(BrowserUrlPolicy.HELP).isNullOrBlank() }
        }
    }
}
