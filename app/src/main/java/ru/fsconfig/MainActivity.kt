package ru.fsconfig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import ru.fsconfig.model.ConfigurationDocument
import ru.fsconfig.model.ConfigurationField
import ru.fsconfig.model.ConfigurationJson
import ru.fsconfig.model.ExchangeEvent
import ru.fsconfig.model.FieldValue
import ru.fsconfig.transport.SimulatorTransport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FsConfigApp() }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FsConfigApp() {
    val transport = remember { SimulatorTransport() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events = remember { mutableStateListOf<ExchangeEvent>() }
    val snackbar = remember { SnackbarHostState() }
    var document by remember { mutableStateOf<ConfigurationDocument?>(null) }
    var status by remember { mutableStateOf("Подключите прибор или откройте файл конфигурации") }
    var tab by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var uiError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transport) {
        transport.events.collect { event ->
            events.add(event)
            if (events.size > 100) events.removeFirst()
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                uiError = null
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: error("Пустой файл")
                    ConfigurationJson.decode(json)
                }.onSuccess { loaded ->
                    document = loaded
                    status = "Открыта конфигурация из файла"
                }.onFailure { failure ->
                    uiError = "Ошибка открытия: ${failure.message ?: "неизвестная ошибка"}"
                    snackbar.showSnackbar(uiError!!)
                }
                busy = false
            }
        }
    }
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && document != null) {
            scope.launch {
                busy = true
                uiError = null
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(ConfigurationJson.encode(document!!))
                    } ?: error("Не удалось открыть файл")
                }
                    .onSuccess { status = "Конфигурация сохранена" }
                    .onFailure { failure ->
                        uiError = "Ошибка сохранения: ${failure.message ?: "неизвестная ошибка"}"
                        snackbar.showSnackbar(uiError!!)
                    }
                busy = false
            }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("FS Config") }) },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    listOf("Подключение", "Конфигурация", "Журнал", "Файлы").forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) }
                        )
                    }
                }
                if (busy) {
                    LoadingState()
                } else when (tab) {
                    0 -> ConnectTab(
                        status = status,
                        error = uiError,
                        onDiscover = {
                            scope.launch {
                                busy = true
                                uiError = null
                                runCatching {
                                    val device = transport.discover().firstOrNull()
                                        ?: error("Приборы не найдены")
                                    transport.readConfiguration(device).getOrThrow()
                                }.onSuccess {
                                    document = it
                                    status = "Прибор найден, конфигурация прочитана из simulator"
                                    tab = 1
                                }.onFailure { failure ->
                                    uiError = failure.message ?: "Не удалось подключиться"
                                }
                                busy = false
                            }
                        },
                        onRealDevice = {
                            uiError = "Реальный протокол заблокирован: нужна официальная спецификация или SDK"
                        }
                    )
                    1 -> ConfigurationTab(
                        document = document,
                        status = status,
                        onChange = { document = it },
                        onWrite = {
                            document?.let { current ->
                                scope.launch {
                                    busy = true
                                    transport.writeConfiguration(current.device, current)
                                        .onSuccess { status = "Изменения записаны в simulator" }
                                        .onFailure { failure -> uiError = failure.message }
                                    busy = false
                                }
                            }
                        },
                        onGoToFiles = { tab = 3 }
                    )
                    2 -> LogTab(events)
                    else -> FilesTab(
                        hasDocument = document != null,
                        onOpen = { openFile.launch(arrayOf("application/json", "text/plain")) },
                        onSave = { saveFile.launch("fs-config.json") }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text("Выполняется операция…")
    }
}

@Composable
private fun ConnectTab(
    status: String,
    error: String?,
    onDiscover: () -> Unit,
    onRealDevice: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Подключение", style = MaterialTheme.typography.headlineSmall)
        Text(status)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onDiscover) { Text("Найти simulator") }
        OutlinedButton(onClick = onRealDevice) { Text("Подключить реальный прибор") }
        Text(
            "USB OTG/RS-485 и Wi‑Fi появятся после подключения официального протокола.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ConfigurationTab(
    document: ConfigurationDocument?,
    status: String,
    onChange: (ConfigurationDocument) -> Unit,
    onWrite: () -> Unit,
    onGoToFiles: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Конфигуратор приборов", style = MaterialTheme.typography.headlineSmall)
            Text(status, modifier = Modifier.padding(top = 4.dp))
        }
        if (document == null) item {
            Text("Сначала найдите прибор на экране подключения или откройте JSON-файл.")
            OutlinedButton(onClick = onGoToFiles) { Text("Перейти к файлам") }
        }
        document?.let { current ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(current.device.model ?: "Неизвестная модель", style = MaterialTheme.typography.titleLarge)
                        Text("Серийный номер: ${current.device.serialNumber ?: "—"}")
                        Text("Прошивка: ${current.device.firmwareVersion ?: "—"}")
                    }
                }
            }
            current.sections.forEach { section ->
                item {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                }
                items(section.fields) { field ->
                    FieldEditor(field) { changed ->
                        onChange(current.replaceField(changed))
                    }
                }
            }
            item {
                Button(onClick = onWrite, modifier = Modifier.fillMaxWidth()) {
                    Text("Записать изменения в simulator")
                }
            }
        }
    }
}

@Composable
private fun FilesTab(hasDocument: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Файлы конфигурации", style = MaterialTheme.typography.headlineSmall)
        Text("JSON-файлы можно открывать и сохранять без подключённого прибора.")
        Button(onClick = onOpen) { Text("Открыть JSON") }
        OutlinedButton(onClick = onSave, enabled = hasDocument) {
            Text("Сохранить текущую конфигурацию")
        }
        if (!hasDocument) Text("Нет конфигурации для сохранения")
    }
}

@Composable
private fun FieldEditor(field: ConfigurationField, onChange: (ConfigurationField) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val value = field.value) {
            is FieldValue.Text -> OutlinedTextField(
                value = value.value,
                onValueChange = { onChange(field.copy(value = FieldValue.Text(it))) },
                label = { Text(field.label) },
                enabled = !field.readOnly,
                modifier = Modifier.fillMaxWidth()
            )
            is FieldValue.Number -> {
                Text("${field.label}: ${value.value} ${field.unit.orEmpty()}")
                Slider(
                    value = value.value.toFloat().coerceIn(0f, 60f),
                    onValueChange = { onChange(field.copy(value = FieldValue.Number(it.toDouble()))) },
                    valueRange = 0f..60f,
                    enabled = !field.readOnly
                )
            }
            is FieldValue.Flag -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(field.label)
                Switch(
                    checked = value.value,
                    onCheckedChange = { onChange(field.copy(value = FieldValue.Flag(it))) },
                    enabled = !field.readOnly
                )
            }
        }
    }
}

@Composable
private fun LogTab(events: List<ExchangeEvent>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Журнал обмена", style = MaterialTheme.typography.headlineSmall) }
        if (events.isEmpty()) item { Text("Событий пока нет") }
        items(events) { event ->
            Text("${event.direction}: ${event.message ?: event.payloadHex}")
            HorizontalDivider()
        }
    }
}

private fun ConfigurationDocument.replaceField(changed: ConfigurationField): ConfigurationDocument =
    copy(sections = sections.map { section ->
        section.copy(fields = section.fields.map { if (it.id == changed.id) changed else it })
    })
