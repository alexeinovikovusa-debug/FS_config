package ru.fsconfig.transport

import kotlinx.coroutines.flow.Flow
import ru.fsconfig.model.ConfigurationDocument
import ru.fsconfig.model.DeviceDescriptor
import ru.fsconfig.model.ExchangeEvent
import ru.fsconfig.model.ConnectionProfile
import ru.fsconfig.model.ConnectionProfileValidator

interface DeviceTransport {
    val events: Flow<ExchangeEvent>
    suspend fun discover(): List<DeviceDescriptor>
    suspend fun readConfiguration(device: DeviceDescriptor): Result<ConfigurationDocument>
    suspend fun writeConfiguration(device: DeviceDescriptor, configuration: ConfigurationDocument): Result<Unit>
}

data class ChannelTestResult(
    val connected: Boolean,
    val message: String,
    val protocolAvailable: Boolean = false
)

interface ChannelTester {
    suspend fun testChannel(profile: ConnectionProfile): Result<ChannelTestResult>
}

class ProtocolUnavailableTransport : DeviceTransport, ChannelTester {
    override val events: Flow<ExchangeEvent> = kotlinx.coroutines.flow.emptyFlow()
    override suspend fun discover(): List<DeviceDescriptor> = emptyList()

    override suspend fun readConfiguration(device: DeviceDescriptor): Result<ConfigurationDocument> =
        Result.failure(UnsupportedOperationException(
            "Протокол прибора не подключён: требуется официальная спецификация или SDK."
        ))

    override suspend fun writeConfiguration(
        device: DeviceDescriptor,
        configuration: ConfigurationDocument
    ): Result<Unit> = Result.failure(UnsupportedOperationException(
        "Запись заблокирована до подтверждения протокола."
    ))

    override suspend fun testChannel(profile: ConnectionProfile): Result<ChannelTestResult> {
        val validation = ConnectionProfileValidator.validate(profile)
        if (!validation.isValid) return Result.failure(IllegalArgumentException(validation.errors.joinToString("; ")))
        return Result.success(ChannelTestResult(false, "Канал валиден, но реальное подключение не активировано", false))
    }
}
