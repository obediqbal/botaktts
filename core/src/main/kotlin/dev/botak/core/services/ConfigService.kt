package dev.botak.core.services

import com.typesafe.config.ConfigFactory

object ConfigService {
    val config = ConfigFactory.load()

    fun getString(key: String): String {
        return config.getString(key)
    }
}
