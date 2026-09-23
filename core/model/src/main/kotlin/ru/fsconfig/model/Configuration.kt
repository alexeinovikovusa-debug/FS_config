package ru.fsconfig.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceDescriptor(
    val transport: TransportKind,
    val address: String,
    val model: String? = null,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val protocolRevision: String? = null
)

@Serializable
enum class TransportKind { USB_RS485, WIFI, SIMULATOR }

@Serializable
data class ConfigurationDocument(
    val schemaVersion: Int = 1,
    val device: DeviceDescriptor,
    val capturedAtEpochMillis: Long,
    val sections: List<ConfigurationSection> = emptyList(),
    val rawExtensions: Map<String, String> = emptyMap()
)

@Serializable
data class ConfigurationSection(
    val id: String,
    val title: String,
    val fields: List<ConfigurationField>
)

@Serializable
data class ConfigurationField(
    val id: String,
    val label: String,
    val value: FieldValue,
    val unit: String? = null,
    val readOnly: Boolean = false
)

@Serializable
sealed class FieldValue {
    @Serializable data class Text(val value: String) : FieldValue()
    @Serializable data class Number(val value: Double) : FieldValue()
    @Serializable data class Flag(val value: Boolean) : FieldValue()
}

@Serializable
data class ExchangeEvent(
    val timestampEpochMillis: Long,
    val direction: Direction,
    val payloadHex: String,
    val message: String? = null
) {
    @Serializable
    enum class Direction { TX, RX, INFO, ERROR }
}
