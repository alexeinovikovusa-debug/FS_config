package ru.fsconfig.model

import kotlinx.serialization.json.Json
object ConfigurationJson {
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(document: ConfigurationDocument): String =
        json.encodeToString(ConfigurationDocument.serializer(), document)

    fun decode(value: String): ConfigurationDocument =
        json.decodeFromString(ConfigurationDocument.serializer(), value)
}
