package dev.botak.core.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.*

/**
 * Manages OAuth access tokens used to authenticate against the Google Cloud Text-to-Speech API.
 *
 * Tokens are issued by a remote token-issuer function (see `tokenIssuerUrl` in the configuration)
 * and cached until they expire. The underlying [GoogleCredentials] are lazily (re)created whenever
 * the cached token is expired, so callers can always obtain a fresh, valid credential via
 * [obtainCredentials].
 *
 * @param accessToken The initial access token; defaults to an empty token with no expiration,
 *   which forces a fetch on first use.
 */
class CredentialsService(
    private var accessToken: AccessToken = AccessToken("", null),
) {
    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(CredentialsService::class.java)

        /**
         * How long before its true expiration an access token is proactively treated as expired.
         *
         * This safety margin closes a time-of-check/time-of-use race in [isTokenExpired]: without
         * it, a token that reads as "valid" with only seconds left could lapse while an in-flight
         * Text-to-Speech request is still using it. The cached [GoogleCredentials] built via
         * [GoogleCredentials.create] from a bare access token cannot self-refresh, so an expired
         * token surfaces as a failed (`UNAUTHENTICATED`) request rather than a transparent refresh.
         * Five minutes mirrors the conservative refresh lead time used by Google's own auth
         * libraries.
         */
        internal const val TOKEN_SAFETY_MARGIN_MS: Long = 5 * 60 * 1000L
    }

    private var cachedCredentials: GoogleCredentials? = null
        get() {
            if (isTokenExpired()) {
                accessToken = fetchAccessToken()
                field = GoogleCredentials.create(accessToken)
            }
            return field
        }
    private val client by lazy { OkHttpClient() }

    /**
     * Returns valid [GoogleCredentials] for the Google Cloud TTS API, refreshing the access token
     * first if the cached one has expired.
     *
     * @throws RuntimeException if credentials could not be obtained.
     */
    fun obtainCredentials(): GoogleCredentials = cachedCredentials ?: throw RuntimeException("Failed to obtain credentials")

    /** Returns `true` when the currently cached access token is still valid (not expired). */
    fun isCredentialsValid(): Boolean = !isTokenExpired()

    /**
     * Determines whether the cached access token is expired or close enough to expiring that it
     * should be refreshed before use.
     *
     * A token with no [AccessToken.expirationTime] is treated as expired. Otherwise the token is
     * considered expired once it lies within [TOKEN_SAFETY_MARGIN_MS] of its true expiration, so
     * callers never build a request on a credential that might lapse mid-flight.
     *
     * Visibility is `internal` so unit tests can exercise the expiry logic directly without
     * having to drive a full HTTP refresh. Production callers treat this as private.
     *
     * @return `true` if the token has expired (or is about to), or has no expiration time.
     */
    internal fun isTokenExpired(): Boolean {
        val expirationTime = accessToken.expirationTime ?: run {
            LOGGER.debug("Access token has no expiration time; treating as expired")
            return true
        }
        val now = System.currentTimeMillis()
        val msUntilExpiry = expirationTime.time - now
        val isExpired = msUntilExpiry <= TOKEN_SAFETY_MARGIN_MS
        LOGGER.debug(
            "Access token is ${if (isExpired) "expired" else "active"} " +
                "(expires in $msUntilExpiry ms, safety margin is $TOKEN_SAFETY_MARGIN_MS ms)",
        )
        return isExpired
    }

    /**
     * Requests a new access token from the configured token-issuer endpoint.
     *
     * The endpoint is expected to return a JSON body containing the token string and an
     * expiration timestamp (in seconds since the epoch).
     *
     * Visibility is `internal` so unit tests can invoke the HTTP path directly against an
     * OkHttp `MockWebServer` without exercising the caching layer. Production callers treat
     * this as private.
     *
     * @return a freshly issued [AccessToken] with its expiration time.
     * @throws RuntimeException if the request fails or the response body is empty/invalid.
     */
    internal fun fetchAccessToken(): AccessToken {
        data class TokenBody(
            val accessToken: String,
            val expirationTimestamp: Long,
        )

        LOGGER.debug("Fetching new access token...")

        val url = ConfigService.getString("tokenIssuerUrl")
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch access token: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response body")
            val tokenBody = jacksonObjectMapper().readValue<TokenBody>(body)

            val expirationInstant = Instant.ofEpochMilli(tokenBody.expirationTimestamp * 1000)
            val dateExpiration = Date.from(expirationInstant)

            LOGGER.debug("Successfully fetched new access token, expiring at $dateExpiration")

            return AccessToken(tokenBody.accessToken, dateExpiration)
        }
    }
}
