package dev.botak.core.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.*

class CredentialsService(
    private var accessToken: AccessToken = AccessToken("", null),
) {
    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(CredentialsService::class.java)
    }

    private var cachedCredentials: GoogleCredentials? = null
        get() {
            if (accessToken.tokenValue == "" || isTokenExpired()) {
                accessToken = fetchAccessToken()
                field = GoogleCredentials.create(accessToken)
            }
            return field
        }
    private val client by lazy { OkHttpClient() }

    fun obtainCredentials(): GoogleCredentials = cachedCredentials ?: throw RuntimeException("Failed to obtain credentials")

    private fun isTokenExpired(): Boolean {
        val isExpired = accessToken.expirationTime?.before(Date()) ?: false
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
