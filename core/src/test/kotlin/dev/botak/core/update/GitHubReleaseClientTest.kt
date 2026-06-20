package dev.botak.core.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Unit tests for [GitHubReleaseClient].
 *
 * Each test boots its own [MockWebServer] inline and shuts it down in a `finally` block, matching
 * the project convention (kotlin-test JUnit4 binding, no `@BeforeEach`/`@AfterEach`).
 */
class GitHubReleaseClientTest {
    private fun withServer(block: (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            block(server)
        } finally {
            server.shutdown()
        }
    }

    private fun clientFor(server: MockWebServer) =
        GitHubReleaseClient(
            client = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            owner = "obediqbal",
            repo = "botaktts",
        )

    @Test
    fun `fetchLatest parses version changelog and setup asset url`() {
        withServer { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "v1.2.0",
                      "body": "Bug fixes",
                      "html_url": "https://github.com/obediqbal/botaktts/releases/tag/v1.2.0",
                      "assets": [
                        {"name": "BotakTTS-1.2.0-Portable.exe", "browser_download_url": "https://example/portable"},
                        {"name": "BotakTTS-1.2.0-Setup.exe", "browser_download_url": "https://example/setup"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val info = clientFor(server).fetchLatest()

            assertEquals(SemVer(1, 2, 0), info.version)
            assertEquals("Bug fixes", info.changelog)
            assertEquals("https://example/setup", info.setupAssetUrl)
            assertEquals("https://github.com/obediqbal/botaktts/releases/tag/v1.2.0", info.htmlUrl)
        }
    }

    @Test
    fun `fetchLatest returns null setup url when asset missing`() {
        withServer { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "v1.2.0",
                      "body": "",
                      "html_url": "https://github.com/obediqbal/botaktts/releases/tag/v1.2.0",
                      "assets": [
                        {"name": "BotakTTS-1.2.0-Portable.exe", "browser_download_url": "https://example/portable"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val info = clientFor(server).fetchLatest()

            assertNull(info.setupAssetUrl)
        }
    }

    @Test
    fun `fetchLatest throws on non-2xx`() {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            assertFailsWith<RuntimeException> { clientFor(server).fetchLatest() }
        }
    }

    @Test
    fun `fetchLatest throws on unparseable tag`() {
        withServer { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"tag_name": "garbage", "body": "", "html_url": "x", "assets": []}""",
                ),
            )
            assertFailsWith<RuntimeException> { clientFor(server).fetchLatest() }
        }
    }
}
