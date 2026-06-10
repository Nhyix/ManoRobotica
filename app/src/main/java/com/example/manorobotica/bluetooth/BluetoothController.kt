package com.example.manorobotica.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    class Connected(val deviceName: String) : ConnectionState()
    class Error(val message: String) : ConnectionState()
}

class BluetoothController(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Consola de depuración interna para mostrar en UI
    private val _serialLog = MutableStateFlow<List<String>>(emptyList())
    val serialLog: StateFlow<List<String>> = _serialLog

    // BLE Scanning & GATT Client variables
    private var isScanning = false
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // UUIDs estándar del Nordic UART Service (NUS)
    private val nusServiceUuid: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val rxCharUuid: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // RX es para ESCRIBIR desde la app al ESP32

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device != null && !device.name.isNullOrBlank()) {
                val currentList = _scannedDevices.value
                if (!currentList.any { it.address == device.address }) {
                    _scannedDevices.value = currentList + device
                    Log.d("BluetoothController", "Dispositivo BLE encontrado: ${device.name} - ${device.address}")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: List<ScanResult>) {
            val currentList = _scannedDevices.value.toMutableList()
            var updated = false
            for (result in results) {
                val device = result.device
                if (device != null && !device.name.isNullOrBlank() && !currentList.any { it.address == device.address }) {
                    currentList.add(device)
                    updated = true
                }
            }
            if (updated) {
                _scannedDevices.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothController", "Escaneo BLE fallido con código: $errorCode")
            logToConsole("Error: Escaneo BLE fallido ($errorCode)")
        }
    }

    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun startScanning(scope: CoroutineScope) {
        if (!isBluetoothEnabled()) {
            logToConsole("Error: Activa el Bluetooth primero")
            return
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            logToConsole("Error: Scanner BLE no disponible en este dispositivo")
            return
        }
        if (isScanning) return

        _scannedDevices.value = emptyList()
        isScanning = true
        logToConsole("Iniciando escaneo de dispositivos BLE...")
        
        try {
            scanner.startScan(scanCallback)
            
            // Auto detener escaneo después de 10 segundos para cuidar batería
            scope.launch {
                delay(10000)
                if (isScanning) {
                    stopScanning()
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothController", "Fallo al iniciar escaneo", e)
            logToConsole("Error: No se pudo iniciar escaneo BLE")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback)
                logToConsole("Escaneo de dispositivos finalizado.")
            } catch (e: Exception) {
                Log.e("BluetoothController", "Fallo al detener escaneo", e)
            }
        }
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice, scope: CoroutineScope) {
        scope.launch {
            _connectionState.value = ConnectionState.Connecting
            val name = device.name ?: "Dispositivo"
            logToConsole("Conectando a BLE $name (${device.address})...")
            
            stopScanning()
            disconnect()

            withContext(Dispatchers.Main) {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            Log.e("BluetoothController", "Error al desconectar GATT", e)
        }
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
        logToConsole("Bluetooth desconectado.")
    }

    private fun closeGatt() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e("BluetoothController", "Error al cerrar GATT", e)
        }
        bluetoothGatt = null
        rxCharacteristic = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device.name ?: gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    logToConsole("Conexión GATT establecida. Descubriendo servicios...")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = ConnectionState.Disconnected
                    logToConsole("Desconectado de $deviceName")
                    closeGatt()
                }
            } else {
                Log.e("BluetoothController", "GATT Error: Estado de conexión falló con código $status")
                _connectionState.value = ConnectionState.Error("Fallo de conexión (Código $status)")
                logToConsole("Error de Conexión: GATT $status")
                closeGatt()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(nusServiceUuid)
                if (service != null) {
                    val rxChar = service.getCharacteristic(rxCharUuid)
                    if (rxChar != null) {
                        rxCharacteristic = rxChar
                        _connectionState.value = ConnectionState.Connected(gatt.device.name ?: gatt.device.address)
                        logToConsole("¡Conexión BLE exitosa con ${gatt.device.name ?: "Mano Robótica"}!")
                    } else {
                        _connectionState.value = ConnectionState.Error("No se encontró canal de escritura RX")
                        logToConsole("Error: Servicio de escritura (RX) no disponible")
                        disconnect()
                    }
                } else {
                    _connectionState.value = ConnectionState.Error("Servicio UART no soportado")
                    logToConsole("Error: Dispositivo no es compatible con el perfil UART NUS")
                    disconnect()
                }
            } else {
                _connectionState.value = ConnectionState.Error("Fallo al descubrir servicios")
                logToConsole("Error: Descubrimiento de servicios falló")
                disconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String, scope: CoroutineScope) {
        val gatt = bluetoothGatt
        val characteristic = rxCharacteristic
        if (gatt != null && characteristic != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = command.toByteArray(Charsets.UTF_8)
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    
                    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == 0
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = bytes
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(characteristic)
                    }
                    
                    if (success) {
                        logToConsole("Enviado (BLE): ${command.trim()}")
                    } else {
                        Log.e("BluetoothController", "Fallo al iniciar envío de comando BLE")
                        logToConsole("Fallo de envío BLE")
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothController", "Error de comunicación BLE", e)
                    withContext(Dispatchers.Main) {
                        logToConsole("Error al enviar: ${e.localizedMessage}")
                    }
                }
            }
        } else {
            // No loggueamos fallos constantes para no saturar la consola visual en loops
            Log.w("BluetoothController", "Intento de envío fallido: No conectado o canal no listo.")
        }
    }

    private fun logToConsole(message: String) {
        val currentList = _serialLog.value.toMutableList()
        if (currentList.size > 40) {
            currentList.removeAt(0)
        }
        currentList.add(message)
        _serialLog.value = currentList
    }

    fun clearLogs() {
        _serialLog.value = emptyList()
    }
}
