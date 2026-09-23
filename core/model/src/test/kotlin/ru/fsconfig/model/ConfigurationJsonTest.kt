package ru.fsconfig.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigurationJsonTest {
    @Test
    fun roundTripPreservesUnknownExtensionsAndFields() {
        val source = ConfigurationDocument(
            device = DeviceDescriptor(TransportKind.SIMULATOR, "test"),
            capturedAtEpochMillis = 123L,
            sections = listOf(
                ConfigurationSection(
                    "general",
                    "Основные",
                    listOf(ConfigurationField("enabled", "Активен", FieldValue.Flag(true)))
                )
            ),
            rawExtensions = mapOf("vendorField" to "kept")
        )

        assertEquals(source, ConfigurationJson.decode(ConfigurationJson.encode(source)))
    }
}
