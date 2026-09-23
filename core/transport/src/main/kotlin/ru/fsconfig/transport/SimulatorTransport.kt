package ru.fsconfig.transport

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import ru.fsconfig.model.ConfigurationDocument
import ru.fsconfig.model.ConfigurationField
import ru.fsconfig.model.ConfigurationSection
import ru.fsconfig.model.DeviceDescriptor
import ru.fsconfig.model.ExchangeEvent
import ru.fsconfig.model.FieldValue
import ru.fsconfig.model.TransportKind
import ru.fsconfig.model.ConnectionProfile
import ru.fsconfig.model.ConnectionProfileValidator

class SimulatorTransport : DeviceTransport, ChannelTester {
    private val eventStream = MutableSharedFlow<ExchangeEvent>(extraBufferCapacity = 32)
    private val stored = AtomicReference(defaultConfiguration())
    override val events: SharedFlow<ExchangeEvent> = eventStream

    override suspend fun discover(): List<DeviceDescriptor> {
        info("Найден тестовый прибор")
        return listOf(stored.get().device)
    }

    override suspend fun readConfiguration(device: DeviceDescriptor): Result<ConfigurationDocument> {
        info("Конфигурация прочитана из simulator")
        return Result.success(stored.get())
    }

    override suspend fun writeConfiguration(
        device: DeviceDescriptor,
        configuration: ConfigurationDocument
    ): Result<Unit> {
        stored.set(configuration)
        info("Конфигурация записана в simulator")
        return Result.success(Unit)
    }

    override suspend fun testChannel(profile: ConnectionProfile): Result<ChannelTestResult> {
        val validation = ConnectionProfileValidator.validate(profile)
        if (!validation.isValid) return Result.failure(IllegalArgumentException(validation.errors.joinToString("; ")))
        info("Проверен профиль канала: ${profile.name}")
        return Result.success(ChannelTestResult(true, "Simulator: канал доступен, protocol exchange заблокирован", false))
    }

    private fun info(message: String) {
        eventStream.tryEmit(
            ExchangeEvent(System.currentTimeMillis(), ExchangeEvent.Direction.INFO, "", message)
        )
    }

    private fun defaultConfiguration() = ConfigurationDocument(
        device = DeviceDescriptor(
            transport = TransportKind.SIMULATOR,
            address = "simulator-01",
            model = "FS Demo",
            serialNumber = "SIM-0001",
            firmwareVersion = "0.1"
        ),
        capturedAtEpochMillis = System.currentTimeMillis(),
        sections = listOf(
            ConfigurationSection(
                id = "general",
                title = "Основные параметры",
                fields = listOf(
                    ConfigurationField("name", "Имя прибора", FieldValue.Text("Демо")),
                    ConfigurationField("enabled", "Активен", FieldValue.Flag(true)),
                    ConfigurationField("timeout", "Тайм-аут", FieldValue.Number(10.0), "с")
                )
            )
        )
    )
}
