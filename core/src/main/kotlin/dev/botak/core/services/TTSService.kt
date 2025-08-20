package dev.botak.core.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.auth.oauth2.AccessToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.*

class TTSService(
    accessToken: AccessToken = AccessToken("", null),
) {
    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(TTSService::class.java)
    }

    private var _accessToken: AccessToken = accessToken
    val accessToken: AccessToken
        get() {
            if (_accessToken.tokenValue == "" || isTokenExpired()) {
                _accessToken = fetchAccessToken()
            }
            return _accessToken
        }
    private val client by lazy { OkHttpClient() }

    private fun isTokenExpired(): Boolean {
        val isExpired = _accessToken.expirationTime?.before(Date()) ?: false
        LOGGER.debug("Access token is ${if (isExpired) "expired" else "active"}")
        return isExpired
    }

    private fun fetchAccessToken(): AccessToken {
        data class TokenBody(
            val accessToken: String,
            val expirationTimestamp: Long,
        )

        LOGGER.debug("Fetching new access token...")

        val url = ConfigService.getString("tokenIssuerUrl")
        val request = Request.Builder().url(url).get().build()

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
