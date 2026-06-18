package dev.botak.core.services

import com.google.auth.oauth2.AccessToken
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant
import java.util.Date

/**
 * Unit tests for [CredentialsService].
 *
 * The HTTP token-issuer interaction is exercised through OkHttp's [MockWebServer], and
 * [ConfigService.getString] is stubbed via `mockkObject` so the service points at the mock
 * server instead of the real cloud function. The `internal` visibility of
 * [CredentialsService.fetchAccessToken] and [CredentialsService.isTokenExpired] lets these
 * tests hit those paths directly.
 *
 * Note on lifecycle: the shared `subprojects` build only places `kotlin("test")` (which resolves
 * to the kotlin-test JUnit4 binding) on the test compile classpath, so JUnit 5 `@BeforeEach` /
 * `@AfterEach` annotations are not resolvable here. Each test therefore boots and shuts down its
 * own [MockWebServer] inline via [withMockServer] and unmocks [ConfigService] in a `finally`
 * block. `kotlin.test.Test` is framework-agnostic and runs on the configured JUnit Platform.
 */
class CredentialsServiceTest {
    /**
     * Boots a fresh [MockWebServer], stubs [ConfigService.getString] for `"tokenIssuerUrl"` to
     * point at it, runs [block], and always shuts the server down and unmocks [ConfigService]
     * afterwards. Use this for tests that drive the HTTP token-issuer path.
     */
    private fun withMockServer(block: (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        mockkObject(ConfigService)
        every { ConfigService.getString("tokenIssuerUrl") } returns server.url("/").toString()
        try {
            block(server)
        } finally {
            unmockkObject(ConfigService)
            server.shutdown()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // isTokenExpired()
    // ---------------------------------------------------------------------------------------------

    /**
     * A token with a `null` expiration time (the default empty token) is treated as expired.
     */
    @Test
    fun `isTokenExpired returns true when expiration time is null`() {
        val service = CredentialsService(AccessToken("", null))
        assertTrue(service.isTokenExpired())
    }

    /**
     * A token whose expiration time lies in the past is expired.
     */
    @Test
    fun `isTokenExpired returns true when expiration time is in the past`() {
        val past = Date.from(Instant.now().minusSeconds(60))
        val service = CredentialsService(AccessToken("token", past))
        assertTrue(service.isTokenExpired())
    }

    /**
     * A token whose expiration time lies in the future is not expired.
     */
    @Test
    fun `isTokenExpired returns false when expiration time is in the future`() {
        val future = Date.from(Instant.now().plusSeconds(3600))
        val service = CredentialsService(AccessToken("token", future))
        assertFalse(service.isTokenExpired())
    }

    // ---------------------------------------------------------------------------------------------
    // isCredentialsValid()
    // ---------------------------------------------------------------------------------------------

    /**
     * The default empty token (no expiration) is not a valid credential.
     */
    @Test
    fun `isCredentialsValid returns false for default empty token`() {
        val service = CredentialsService()
        assertFalse(service.isCredentialsValid())
    }

    /**
     * A future-dated token is reported as a valid credential.
     */
    @Test
    fun `isCredentialsValid returns true for future-dated token`() {
        val future = Date.from(Instant.now().plusSeconds(3600))
        val service = CredentialsService(AccessToken("token", future))
        assertTrue(service.isCredentialsValid())
    }

    /**
     * A past-dated token is reported as an invalid (expired) credential.
     */
    @Test
    fun `isCredentialsValid returns false for past-dated token`() {
        val past = Date.from(Instant.now().minusSeconds(60))
        val service = CredentialsService(AccessToken("token", past))
        assertFalse(service.isCredentialsValid())
    }

    // ---------------------------------------------------------------------------------------------
    // obtainCredentials()
    // ---------------------------------------------------------------------------------------------

    /**
     * When the cached token is non-expired, the `cachedCredentials` getter returns `null`
     * without building credentials (the getter only refreshes when the token is expired), so
     * [CredentialsService.obtainCredentials] throws `RuntimeException("Failed to obtain credentials")`.
     *
     * This reflects current implementation behavior: production code always starts from an
     * empty/expired token which forces a fetch, so this edge case never arises in practice.
     */
    @Test
    fun `obtainCredentials throws when token is non-expired and cache is empty`() {
        val future = Date.from(Instant.now().plusSeconds(3600))
        val service = CredentialsService(AccessToken("token", future))
        val ex = assertFailsWith<RuntimeException> { service.obtainCredentials() }
        assertEquals("Failed to obtain credentials", ex.message)
    }

    // ---------------------------------------------------------------------------------------------
    // fetchAccessToken() — success path
    // ---------------------------------------------------------------------------------------------

    /**
     * A 200 response carrying `accessToken` and `expirationTimestamp` is parsed into an
     * [AccessToken] whose token value and expiration (converted from epoch-seconds to a
     * [Date]) match the JSON body, and the underlying request is a GET to the stubbed URL.
     */
    @Test
    fun `fetchAccessToken returns parsed token on HTTP 200`() {
        withMockServer { server ->
            val service = CredentialsService()

            // expirationTimestamp is interpreted as epoch-seconds, then multiplied by 1000.
            server.enqueue(
                MockResponse().setBody("""{"accessToken":"abc123","expirationTimestamp":1000}"""),
            )

            val token = service.fetchAccessToken()

            assertEquals("abc123", token.tokenValue)
            assertEquals(Date.from(Instant.ofEpochMilli(1000L * 1000L)), token.expirationTime)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals(
                server.url("/").toString().trimEnd('/'),
                recorded.requestUrl.toString().trimEnd('/'),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // fetchAccessToken() — failure paths
    // ---------------------------------------------------------------------------------------------

    /**
     * A non-2xx response surfaces as a `RuntimeException` whose message contains the HTTP code.
     */
    @Test
    fun `fetchAccessToken throws on HTTP 500`() {
        withMockServer { server ->
            val service = CredentialsService()

            server.enqueue(MockResponse().setResponseCode(500))

            val ex = assertFailsWith<RuntimeException> { service.fetchAccessToken() }
            assertTrue(ex.message?.contains("Failed to fetch access token: HTTP 500") == true)
        }
    }

    /**
     * A 200 with an empty body surfaces as a Jackson parse error rather than the
     * `"Empty response body"` guard: OkHttp's `ResponseBody.string()` returns `""` (not `null`)
     * for an empty body, so the `?: throw RuntimeException("Empty response body")` null-check in
     * [CredentialsService.fetchAccessToken] does not fire and Jackson fails to deserialize the
     * empty string. The `"Empty response body"` branch is only reachable when `response.body`
     * itself is `null`, which MockWebServer never produces — so this test pins the observable
     * behavior for an empty body: it throws, but not that specific message.
     */
    @Test
    fun `fetchAccessToken throws on empty response body`() {
        withMockServer { server ->
            val service = CredentialsService()

            server.enqueue(MockResponse().setBody(""))

            val ex = assertFailsWith<Exception> { service.fetchAccessToken() }
            // Not the "Empty response body" message — a Jackson deserialization failure instead.
            assertFalse(ex.message == "Empty response body")
        }
    }

    /**
     * A 200 with a non-JSON body throws due to a Jackson parse error.
     */
    @Test
    fun `fetchAccessToken throws on invalid JSON body`() {
        withMockServer { server ->
            val service = CredentialsService()

            server.enqueue(MockResponse().setBody("not-json"))

            assertFailsWith<Exception> { service.fetchAccessToken() }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Full refresh flow
    // ---------------------------------------------------------------------------------------------

    /**
     * Starting from the default empty token, [CredentialsService.isCredentialsValid] is `false`
     * and a call to [CredentialsService.obtainCredentials] triggers a refresh against the mock
     * token issuer, yielding a non-null [com.google.auth.oauth2.GoogleCredentials] whose
     * access token value matches the mocked response.
     */
    @Test
    fun `obtainCredentials refreshes from empty token via mock token issuer`() {
        withMockServer { server ->
            val service = CredentialsService()

            assertFalse(service.isCredentialsValid())

            server.enqueue(
                MockResponse().setBody("""{"accessToken":"abc123","expirationTimestamp":1000}"""),
            )

            val credentials = service.obtainCredentials()

            assertNotNull(credentials)
            assertNotNull(credentials.accessToken)
            assertEquals("abc123", credentials.accessToken.tokenValue)
        }
    }
}