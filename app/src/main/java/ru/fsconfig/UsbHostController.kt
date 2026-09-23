package ru.fsconfig

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable

data class UsbDeviceInfo(
    val device: UsbDevice,
    val hasPermission: Boolean
) {
    val adapterFamily: String
        get() = if (device.vendorId == CP210X_VENDOR_ID) {
            "Silicon Labs CP210x USB-UART (Android driver не встроен)"
        } else {
            "Неизвестный USB-адаптер (драйвер не выбран)"
        }

    val displayName: String
        get() = device.productName?.takeIf { it.isNotBlank() }
            ?: device.deviceName

    private companion object {
        const val CP210X_VENDOR_ID = 0x10C4
    }
}

class UsbHostController(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val permissionAction = "${appContext.packageName}.USB_PERMISSION"
    private var connection: UsbDeviceConnection? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permissionAction) {
                refresh()
                val device = intent.usbDevice()
                if (device != null && !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    status = "Доступ к USB-адаптеру отклонён"
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED
            ) {
                refresh()
            }
        }
    }

    var devices by mutableStateOf<List<UsbDeviceInfo>>(emptyList())
        private set
    var selectedDeviceName by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf("USB Host ещё не проверялся")
        private set

    init {
        val filter = IntentFilter().apply {
            addAction(permissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
        refresh()
    }

    fun refresh() {
        devices = usbManager.deviceList.values
            .sortedBy { it.deviceName }
            .map { UsbDeviceInfo(it, usbManager.hasPermission(it)) }
        if (selectedDeviceName == null || devices.none { it.device.deviceName == selectedDeviceName }) {
            selectedDeviceName = devices.firstOrNull()?.device?.deviceName
        }
        status = if (devices.isEmpty()) {
            "USB-адаптер не обнаружен"
        } else {
            "Обнаружено USB-устройств: ${devices.size}"
        }
    }

    fun requestPermission(info: UsbDeviceInfo) {
        if (usbManager.hasPermission(info.device)) {
            refresh()
            status = "Доступ к ${info.displayName} уже предоставлен"
            return
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(permissionAction).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(info.device, pendingIntent)
        status = "Ожидание разрешения Android для ${info.displayName}"
    }

    fun testOpenClose(info: UsbDeviceInfo): Boolean {
        if (!usbManager.hasPermission(info.device)) {
            status = "Сначала предоставьте разрешение USB для ${info.displayName}"
            return false
        }
        connection?.close()
        connection = usbManager.openDevice(info.device)
        val opened = connection != null
        connection?.close()
        connection = null
        status = if (opened) {
            "USB-канал открыт и закрыт: protocol exchange заблокирован"
        } else {
            "Не удалось открыть USB-адаптер без драйвера"
        }
        return opened
    }

    fun select(info: UsbDeviceInfo) {
        selectedDeviceName = info.device.deviceName
    }

    fun selected(): UsbDeviceInfo? =
        devices.firstOrNull { it.device.deviceName == selectedDeviceName }

    override fun close() {
        connection?.close()
        connection = null
        appContext.unregisterReceiver(receiver)
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
