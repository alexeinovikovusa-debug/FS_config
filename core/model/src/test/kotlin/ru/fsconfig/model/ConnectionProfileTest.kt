package ru.fsconfig.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionProfileTest {
    @Test
    fun validatesUsbSerialAndTcpProfiles() {
        assertFalse(
            ConnectionProfileValidator.validate(
                ConnectionProfile.UsbSerial("usb", "USB", portName = "", rs485Address = 0)
            ).isValid
        )
        assertTrue(
            ConnectionProfileValidator.validate(
                ConnectionProfile.ConfigTcp("tcp", "Config по Wi-Fi", host = "192.0.2.10", port = 8100)
            ).isValid
        )
    }
}
