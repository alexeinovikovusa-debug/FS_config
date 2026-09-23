package ru.fsconfig.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object ConfigurationJson {
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(document: ConfigurationDocument): String = json.encodeToString(document)

    fun decode(value: String): ConfigurationDocument = json.decodeFromString(value)
}
