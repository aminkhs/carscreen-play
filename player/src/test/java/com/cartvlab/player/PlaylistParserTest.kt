package com.cartvlab.player
import org.junit.Assert.*
import org.junit.Test
class PlaylistParserTest {
    @Test fun parsesBomTitlesAndIgnoresUnsupportedSchemes() {
        val parsed = PlaylistParser.parse("\uFEFF#EXTM3U\n#EXTINF:-1,نمونه\nhttps://example.org/video.mp4\nfile:///private/video.mp4\njavascript:alert(1)\ncontent://test/2")
        assertEquals(2, parsed.size)
        assertEquals("نمونه", parsed[0].title)
        assertEquals("content://test/2", parsed[1].uri)
    }
    @Test fun resolvesRemoteRelativeEntriesOnlyWithBase() {
        assertTrue(PlaylistParser.parse("video.mp4").isEmpty())
        assertEquals("https://example.org/media/video.mp4", PlaylistParser.parse("video.mp4", "https://example.org/media/list.m3u").single().uri)
    }
    @Test(expected = IllegalArgumentException::class) fun refusesHlsAsPlaylist() {
        PlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\nsegment.ts")
    }
    @Test fun ignoresEmptyAndComments() { assertTrue(PlaylistParser.parse("\n# test\n").isEmpty()) }
}
