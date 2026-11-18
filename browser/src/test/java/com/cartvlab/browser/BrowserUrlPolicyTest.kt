package com.cartvlab.browser
import org.junit.Assert.*
import org.junit.Test
class BrowserUrlPolicyTest {
    @Test fun normalizesWebsiteAndSearch() {
        assertEquals("https://youtube.com", BrowserUrlPolicy.normalize(" youtube.com "))
        assertTrue(BrowserUrlPolicy.normalize("Persian music").startsWith("https://www.google.com/search?q="))
    }
    @Test fun blocksScriptFileDataAndEmbeddedCredentials() {
        listOf("javascript:alert(1)", "file:///sdcard/x", "content://x/a", "data:text/html,test",
            "https://user:password@example.org/", "intent://youtube.com").forEach {
            assertFalse(it, BrowserUrlPolicy.isWebUrl(it))
        }
    }
    @Test fun accountRoutingUsesExactHost() {
        assertTrue(BrowserUrlPolicy.requiresAccountBrowser("https://accounts.google.com/signin"))
        assertFalse(BrowserUrlPolicy.requiresAccountBrowser("https://accounts.google.com.evil.example/signin"))
        assertFalse(BrowserUrlPolicy.requiresAccountBrowser("https://example.org/?next=accounts.google.com"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsScriptFromAddressBar() { BrowserUrlPolicy.normalize("javascript:alert(1)") }
}
