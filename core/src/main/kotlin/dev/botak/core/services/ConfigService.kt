package dev.botak.core.services

import com.typesafe.config.ConfigFactory

object ConfigService {
    val config = ConfigFactory.load()

    fun getString(key: String): String = config.getString(key)

    fun getDouble(key: String): Double = config.getDouble(key)

    fun getInt(key: String): Int = config.getInt(key)
}
