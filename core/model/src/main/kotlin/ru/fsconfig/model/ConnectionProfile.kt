package ru.fsconfig.model

import kotlinx.serialization.Serializable

@Serializable
sealed class ConnectionProfile {
    abstract val id: String
    abstract val name: String

    @Serializable
    data class UsbSerial(
        override val id: String,
        override val name: String,
        val portName: String = "",
        val baudRate: Int = 115200,
        val dataBits: Int = 8,
        val parity: Parity = Parity.NONE,
        val stopBits: StopBits = StopBits.ONE,
        val flowControl: FlowControl = FlowControl.NONE,
        val rs485Address: Int = 1
    ) : ConnectionProfile()

    @Serializable
    data class ConfigTcp(
        override val id: String,
        override val name: String,
        val host: String = "",
        val port: Int = 8100,
        val connectTimeoutMillis: Long = 5000,
        val keepAlive: Boolean = true
    ) : ConnectionProfile()
}

@Serializable
enum class Parity { NONE, EVEN, ODD }

@Serializable
enum class StopBits { ONE, TWO }

@Serializable
enum class FlowControl { NONE, RTS_CTS, XON_XOFF }

data class ProfileValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ConnectionProfileValidator {
    fun validate(profile: ConnectionProfile): ProfileValidation = ProfileValidation(
        when (profile) {
            is ConnectionProfile.UsbSerial -> buildList {
                if (profile.portName.isBlank()) add("Укажите USB serial-порт")
                if (profile.baudRate !in setOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200)) {
                    add("Неподдерживаемая скорость передачи")
                }
                if (profile.dataBits !in 5..8) add("Data bits должны быть от 5 до 8")
                if (profile.rs485Address !in 1..247) add("Адрес RS-485 должен быть от 1 до 247")
            }
            is ConnectionProfile.ConfigTcp -> buildList {
                if (profile.host.isBlank()) add("Укажите IP-адрес или имя шлюза")
                if (profile.port !in 1..65535) add("TCP-порт должен быть от 1 до 65535")
                if (profile.connectTimeoutMillis !in 500..60000) add("Тайм-аут должен быть от 0,5 до 60 секунд")
            }
        }
    )
}
