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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import ru.fsconfig.model.ConfigurationDocument
import ru.fsconfig.model.ConfigurationField
import ru.fsconfig.model.ConfigurationJson
import ru.fsconfig.model.CnuCodec
import ru.fsconfig.model.CnuDocument
import ru.fsconfig.model.ConnectionProfile
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
    val usbHost = remember { UsbHostController(context) }
    val scope = rememberCoroutineScope()
    val events = remember { mutableStateListOf<ExchangeEvent>() }
    val snackbar = remember { SnackbarHostState() }
    var document by remember { mutableStateOf<ConfigurationDocument?>(null) }
    var cnuDocument by remember { mutableStateOf<CnuDocument?>(null) }
    var cnuWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("Подключите прибор или откройте файл конфигурации") }
    var tab by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var uiError by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ConnectionProfile>(ConnectionProfile.UsbSerial("usb", "USB RS-485")) }
    val savedProfiles = remember { mutableStateListOf<ConnectionProfile>() }
    var profileSaved by remember { mutableStateOf(false) }
    var channelStatus by remember { mutableStateOf("Канал ещё не проверялся") }

    LaunchedEffect(transport) {
        transport.events.collect { event ->
            events.add(event)
            if (events.size > 100) events.removeFirst()
        }
        DisposableEffect(usbHost) {
            onDispose { usbHost.close() }
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
                }.onSuccess {
                    status = "Конфигурация сохранена"
                }.onFailure { failure ->
                    uiError = "Ошибка сохранения: ${failure.message ?: "неизвестная ошибка"}"
                    snackbar.showSnackbar(uiError!!)
                }
                busy = false
            }
        }
    }
    val openCnu = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                uiError = null
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: error("Пустой CNU-файл")
                    CnuCodec.parse(text)
                }.onSuccess { parsed ->
                    cnuDocument = parsed.document
                    cnuWarnings = parsed.warnings
                    status = "Открыт CNU: ${parsed.document.rows.size} строк raw-конфигурации"
                    tab = 3
                }.onFailure { failure ->
                    uiError = "Ошибка CNU: ${failure.message ?: "неизвестная ошибка"}"
                    snackbar.showSnackbar(uiError!!)
                }
                busy = false
            }
        }
    }
    val saveCnu = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && cnuDocument != null) {
            scope.launch {
                busy = true
                uiError = null
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(CnuCodec.encode(cnuDocument!!))
                    } ?: error("Не удалось открыть CNU-файл")
                }.onSuccess {
                    status = "CNU сохранён без изменения семантики raw-данных"
                }.onFailure { failure ->
                    uiError = "Ошибка сохранения CNU: ${failure.message ?: "неизвестная ошибка"}"
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
                        document = document,
                        profile = profile,
                        profileSaved = profileSaved,
                        channelStatus = channelStatus,
                        usbHost = usbHost,
                        onProfileChange = { profile = it; profileSaved = false },
                        onSaveProfile = {
                            savedProfiles.removeAll { it.id == profile.id }
                            savedProfiles.add(profile)
                            profileSaved = true
                        },
                        savedProfileCount = savedProfiles.size,
                        onTestChannel = {
                            scope.launch {
                                busy = true
                                transport.testChannel(profile)
                                    .onSuccess { channelStatus = it.message }
                                    .onFailure { uiError = it.message }
                                busy = false
                            }
                        },
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
                        cnuDocument = cnuDocument,
                        cnuWarnings = cnuWarnings,
                        onOpen = { openFile.launch(arrayOf("application/json", "text/plain")) },
                        onSave = { saveFile.launch("fs-config.json") },
                        onOpenCnu = { openCnu.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                        onSaveCnu = { saveCnu.launch("configuration.cnu") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionProfileEditor(
    profile: ConnectionProfile,
    saved: Boolean,
    savedProfileCount: Int,
    channelStatus: String,
    onChange: (ConnectionProfile) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Профиль подключения", style = MaterialTheme.typography.titleMedium)
            Text("Сохранённых профилей: $savedProfileCount")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onChange(ConnectionProfile.UsbSerial("usb", "USB RS-485")) }) {
                    Text("USB Serial")
                }
                OutlinedButton(onClick = { onChange(ConnectionProfile.ConfigTcp("tcp", "Config по Wi-Fi")) }) {
                    Text("Config по Wi-Fi")
                }
            }
            when (profile) {
                is ConnectionProfile.UsbSerial -> {
                    OutlinedTextField(
                        value = profile.portName,
                        onValueChange = { onChange(profile.copy(portName = it)) },
                        label = { Text("USB serial-порт") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profile.baudRate.toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> onChange(profile.copy(baudRate = value)) } },
                        label = { Text("Baud rate") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Data bits: ${profile.dataBits}; parity: ${profile.parity}; stop bits: ${profile.stopBits}")
                    OutlinedTextField(
                        value = profile.rs485Address.toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> onChange(profile.copy(rs485Address = value)) } },
                        label = { Text("Адрес RS-485 (1–247)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Flow control: ${profile.flowControl}")
                }
                is ConnectionProfile.ConfigTcp -> {
                    OutlinedTextField(
                        value = profile.host,
                        onValueChange = { onChange(profile.copy(host = it)) },
                        label = { Text("IP-адрес или имя шлюза") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profile.port.toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> onChange(profile.copy(port = value)) } },
                        label = { Text("TCP-порт") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Config по Wi-Fi — только TCP/IP канал; обмен протоколом заблокирован")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(if (saved) "Профиль сохранён" else "Сохранить профиль") }
                OutlinedButton(onClick = onTest) { Text("Проверить канал") }
            }
            Text(channelStatus, style = MaterialTheme.typography.bodySmall)
            Text(
                "Проверка канала не означает поддержку протокола: framing и команды появятся только по официальной спецификации.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
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
    document: ConfigurationDocument?,
    profile: ConnectionProfile,
    profileSaved: Boolean,
    savedProfileCount: Int,
    channelStatus: String,
    usbHost: UsbHostController,
    onProfileChange: (ConnectionProfile) -> Unit,
    onSaveProfile: () -> Unit,
    onTestChannel: () -> Unit,
    onDiscover: () -> Unit,
    onRealDevice: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Подключение", style = MaterialTheme.typography.headlineSmall) }
        item { Text(status) }
        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text("Доступные каналы", style = MaterialTheme.typography.titleMedium)
            Text("USB-переходники и сетевые точки появятся после реализации официального транспорта.")
        }
        item {
            UsbHostPanel(usbHost)
        }
        item {
            ConnectionProfileEditor(profile, profileSaved, savedProfileCount, channelStatus, onProfileChange, onSaveProfile, onTestChannel)
        }
        item {
            Text("USB / RS-485", style = MaterialTheme.typography.titleMedium)
            Text("Проверка ограничена USB Host: обнаружение, разрешение и открытие/закрытие канала.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wi‑Fi", style = MaterialTheme.typography.titleMedium)
                    Text("Состояние: ожидает официального endpoint")
                    Text("Сетевые параметры не угадываются и не отправляются.")
                }
            }
        }
        item {
            Text("Идентифицированный прибор", style = MaterialTheme.typography.titleMedium)
            if (document == null) {
                Text("Прибор ещё не идентифицирован")
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(document.device.model ?: "Модель не указана")
                        Text("Адрес: ${document.device.address}")
                        Text("Версия: ${document.device.firmwareVersion ?: "—"}")
                        Text("Серийный номер: ${document.device.serialNumber ?: "—"}")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDiscover) { Text("Найти simulator") }
                OutlinedButton(onClick = onRealDevice) { Text("Реальный канал") }
            }
        }
    }
}

@Composable
private fun UsbHostPanel(usbHost: UsbHostController) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("USB Host / OTG", style = MaterialTheme.typography.titleMedium)
            Text(usbHost.status)
            if (usbHost.devices.isEmpty()) {
                Text("Подключите USB-RS-485 адаптер через OTG и обновите список.")
            } else {
                usbHost.devices.forEach { info ->
                    val device = info.device
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(info.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("Vendor ID: ${device.vendorId}; Product ID: ${device.productId}")
                            Text("Производитель: ${device.manufacturerName ?: "не предоставлен адаптером"}")
                            Text("Product: ${device.productName ?: "не предоставлен адаптером"}")
                            Text("USB permission: ${if (info.hasPermission) "предоставлено" else "не предоставлено"}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!info.hasPermission) {
                                    OutlinedButton(onClick = { usbHost.requestPermission(info) }) {
                                        Text("Разрешить доступ")
                                    }
                                }
                                Button(onClick = { usbHost.testOpenClose(info) }) {
                                    Text("Открыть/закрыть")
                                }
                            }
                            Text(
                                "Драйвер serial и протокол не выбираются автоматически; frames/read/write заблокированы.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = usbHost::refresh) { Text("Обновить USB-список") }
        }
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
    var selectedAddress by remember { mutableStateOf(1) }
    var selectedSection by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf("1-10") }
    var showValveInfo by remember { mutableStateOf(false) }
    val sectionNames = listOf("Параметры", "Зоны", "Прибор", "Выходы", "Клапаны", "Доступ", "Ключи")
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
            item {
                AddressSelector(selectedAddress) { selectedAddress = it }
            }
            item {
                TabRow(selectedTabIndex = selectedSection) {
                    sectionNames.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSection == index,
                            onClick = { selectedSection = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
            item {
                Text(
                    "Редактор показывает только структуру интерфейса. Семантика CNU-полей требует официальной карты.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (selectedSection == 4) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Раздел «Клапаны» пока пуст", style = MaterialTheme.typography.titleMedium)
                            Text("Для выбранного simulator-контроллера клапаны не заданы. Это информационное состояние, а не ошибка.")
                            OutlinedButton(onClick = { showValveInfo = true }) {
                                Text("Подробнее")
                            }
                        }
                    }
                }
            }
            item {
                ParameterMatrix(
                    address = selectedAddress,
                    fields = current.sections.flatMap { it.fields },
                    onChange = onChange,
                    current = current
                )
            }
            if (selectedSection == 6) {
                item {
                    KeysPanel(address = selectedAddress)
                }
            }
            item {
                BulkActions(range = range, onRangeChange = { range = it })
            }
            current.sections.forEach { section ->
                item {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                }

                /*
                @Composable
                private fun AddressSelector(selected: Int, onSelected: (Int) -> Unit) {
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        OutlinedTextField(
                            value = "Адрес $selected",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Выбор адреса") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Скрыть адреса" else "Выбрать адрес")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            (1..16).forEach { address ->
                                DropdownMenuItem(
                                    text = { Text("Адрес $address") },
                                    onClick = {
                                        onSelected(address)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (showValveInfo) {
                        AlertDialog(
                            onDismissRequest = { showValveInfo = false },
                            title = { Text("Клапаны не настроены") },
                            text = { Text("В этом демонстрационном профиле нет заданных клапанов. После получения официальной карты полей раздел позволит просматривать их связи.") },
                            confirmButton = {
                                TextButton(onClick = { showValveInfo = false }) { Text("Понятно") }
                            }
                        )
                    }
                }

                @Composable
                private fun ParameterMatrix(
                    address: Int,
                    fields: List<ConfigurationField>,
                    current: ConfigurationDocument,
                    onChange: (ConfigurationDocument) -> Unit
                ) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Параметры адреса $address", style = MaterialTheme.typography.titleMedium)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Параметр", modifier = Modifier.padding(end = 48.dp))
                                Text("Текущее значение")
                                Text("Единицы")
                            }
                            HorizontalDivider()
                            fields.take(8).forEach { field ->
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(field.label, modifier = Modifier.padding(end = 24.dp))
                                    Text(field.value.display())
                                    Text(field.unit ?: "—")
                                }
                            }
                        }
                    }
                }

                @Composable
                private fun BulkActions(range: String, onRangeChange: (String) -> Unit) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Групповая обработка", style = MaterialTheme.typography.titleMedium)
                            Text("Диапазон адресов/зон")
                            OutlinedTextField(
                                value = range,
                                onValueChange = onRangeChange,
                                label = { Text("Например, 1-10") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {}, enabled = false) { Text("Копировать параметр") }
                                OutlinedButton(onClick = {}, enabled = false) { Text("Связать входы/выходы") }
                            }
                            Text(
                                "Групповые операции включатся после подтверждения карты полей и протокола.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                @Composable
                private fun KeysPanel(address: Int) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ключи доступа", style = MaterialTheme.typography.titleMedium)
                            Text("0 / 512 ключей")
                            Text("Прибор: адрес $address")
                            OutlinedTextField(
                                value = "Считыватель 1",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Считыватель для чтения") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Выбранный ключ: нет")
                                    Text("Основной код: ••••••••")
                                    Text("Уровень доступа: —")
                                    Text("Состояние: —")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {}, enabled = false) { Text("Добавить") }
                                OutlinedButton(onClick = {}, enabled = false) { Text("Изменить") }
                                OutlinedButton(onClick = {}, enabled = false) { Text("Удалить") }
                            }
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Поиск и массовая работа")
                            }
                            Text("Импорт и экспорт", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {}, enabled = false) { Text("Импорт") }
                                OutlinedButton(onClick = {}, enabled = false) { Text("Экспорт CSV") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {}, enabled = false) { Text("Экспорт ;") }
                                OutlinedButton(onClick = {}, enabled = false) { Text("Печать кодов") }
                            }
                            Text(
                                "Коды всегда маскируются. Импорт, экспорт и печать требуют подтверждения " +
                                    "формата и контроля доступа; форматы без спецификации не реализуются.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                private fun FieldValue.display(): String = when (this) {
                    is FieldValue.Text -> value
                    is FieldValue.Number -> value.toString()
                    is FieldValue.Flag -> if (value) "Вкл." else "Выкл."
                }
                */
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
    if (showValveInfo) {
        AlertDialog(
            onDismissRequest = { showValveInfo = false },
            title = { Text("Клапаны не настроены") },
            text = { Text("В этом демонстрационном профиле нет заданных клапанов. После получения официальной карты полей раздел позволит просматривать их связи.") },
            confirmButton = {
                TextButton(onClick = { showValveInfo = false }) { Text("Понятно") }
            }
        )
    }
}

@Composable
private fun AddressSelector(selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = "Адрес $selected",
            onValueChange = {},
            readOnly = true,
            label = { Text("Выбор адреса") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Скрыть адреса" else "Выбрать адрес")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..16).forEach { address ->
                DropdownMenuItem(
                    text = { Text("Адрес $address") },
                    onClick = {
                        onSelected(address)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ParameterMatrix(
    address: Int,
    fields: List<ConfigurationField>,
    current: ConfigurationDocument,
    onChange: (ConfigurationDocument) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Параметры адреса $address", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Параметр", modifier = Modifier.padding(end = 48.dp))
                Text("Текущее значение")
                Text("Единицы")
            }
            HorizontalDivider()
            fields.take(8).forEach { field ->
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(field.label, modifier = Modifier.padding(end = 24.dp))
                    Text(field.value.display())
                    Text(field.unit ?: "—")
                }
            }
        }
    }
}

@Composable
private fun BulkActions(range: String, onRangeChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Групповая обработка", style = MaterialTheme.typography.titleMedium)
            Text("Диапазон адресов/зон")
            OutlinedTextField(
                value = range,
                onValueChange = onRangeChange,
                label = { Text("Например, 1-10") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, enabled = false) { Text("Копировать параметр") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Связать входы/выходы") }
            }
            Text(
                "Групповые операции включатся после подтверждения карты полей и протокола.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun KeysPanel(address: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ключи доступа", style = MaterialTheme.typography.titleMedium)
            Text("0 / 512 ключей")
            Text("Прибор: адрес $address")
            OutlinedTextField(
                value = "Считыватель 1",
                onValueChange = {},
                readOnly = true,
                label = { Text("Считыватель для чтения") },
                modifier = Modifier.fillMaxWidth()
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Выбранный ключ: нет")
                    Text("Основной код: ••••••••")
                    Text("Уровень доступа: —")
                    Text("Состояние: —")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, enabled = false) { Text("Добавить") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Изменить") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Удалить") }
            }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Поиск и массовая работа")
            }
            Text(
                "Коды всегда маскируются. Изменение и удаление потребуют подтверждения после подключения официальной карты полей.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun FieldValue.display(): String = when (this) {
    is FieldValue.Text -> value
    is FieldValue.Number -> value.toString()
    is FieldValue.Flag -> if (value) "Вкл." else "Выкл."
}

@Composable
private fun FilesTab(
    hasDocument: Boolean,
    cnuDocument: CnuDocument?,
    cnuWarnings: List<String>,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onOpenCnu: () -> Unit,
    onSaveCnu: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Файлы конфигурации", style = MaterialTheme.typography.headlineSmall)
            Text("JSON хранит модель приложения; CNU отображается как raw-текстовый контейнер.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Открыть JSON") }
                OutlinedButton(onClick = onSave, enabled = hasDocument) { Text("Сохранить JSON") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenCnu) { Text("Открыть CNU") }
                OutlinedButton(onClick = onSaveCnu, enabled = cnuDocument != null) { Text("Сохранить CNU") }
            }
        }
        item {
            Text(
                "Предупреждение: байты CNU не получают смыслов и не отправляются в прибор. " +
                    "Редактирование протокола и запись в устройство пока недоступны.",
                color = MaterialTheme.colorScheme.error
            )
        }
        cnuDocument?.let { cnu ->
            item { Text("Метаданные CNU", style = MaterialTheme.typography.titleMedium) }
            items(cnu.header) { entry -> Text("${entry.key} = ${entry.value}") }
            item { Text("Raw-строки: ${cnu.rows.size}; значений: ${cnu.rows.sumOf { it.size }}") }
            if (cnuWarnings.isNotEmpty()) {
                item { Text("Предупреждения", style = MaterialTheme.typography.titleMedium) }
                items(cnuWarnings) { warning -> Text("• $warning", color = MaterialTheme.colorScheme.error) }
            }
            item { Text("Первые raw-строки", style = MaterialTheme.typography.titleMedium) }
            items(cnu.originalRowLines.take(5)) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
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
