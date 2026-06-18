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
     * Determines whether the cached access token has expired.
     *
     * A token with no [AccessToken.expirationTime] is treated as expired.
     *
     * @return `true` if the token is expired or has no expiration time.
     */
    private fun isTokenExpired(): Boolean {
        val isExpired = accessToken.expirationTime?.before(Date()) ?: true
        LOGGER.debug("Access token is ${if (isExpired) "expired" else "active"}")
        return isExpired
    }

    /**
     * Requests a new access token from the configured token-issuer endpoint.
     *
     * The endpoint is expected to return a JSON body containing the token string and an
     * expiration timestamp (in seconds since the epoch).
     *
     * @return a freshly issued [AccessToken] with its expiration time.
     * @throws RuntimeException if the request fails or the response body is empty/invalid.
     */
    private fun fetchAccessToken(): AccessToken {
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
