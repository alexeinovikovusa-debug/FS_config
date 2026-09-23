package ru.fsconfig.transport

import kotlinx.coroutines.flow.Flow
import ru.fsconfig.model.ConfigurationDocument
import ru.fsconfig.model.DeviceDescriptor
import ru.fsconfig.model.ExchangeEvent

interface DeviceTransport {
    val events: Flow<ExchangeEvent>
    suspend fun discover(): List<DeviceDescriptor>
    suspend fun readConfiguration(device: DeviceDescriptor): Result<ConfigurationDocument>
    suspend fun writeConfiguration(device: DeviceDescriptor, configuration: ConfigurationDocument): Result<Unit>
}

class ProtocolUnavailableTransport : DeviceTransport {
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
}
