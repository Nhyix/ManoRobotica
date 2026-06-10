package com.example.manorobotica

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.manorobotica.bluetooth.BluetoothController
import com.example.manorobotica.ui.screens.CameraSignScreen
import com.example.manorobotica.ui.screens.ControlScreen
import com.example.manorobotica.ui.theme.ManoRoboticaTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothController: BluetoothController

    // Callback dinámico para el resultado de voz
    private var onVoiceResultCallback: ((String) -> Unit)? = null

    // Lanzador para solicitar habilitar el Bluetooth
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Bluetooth habilitado correctamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "El Bluetooth es necesario para controlar la mano", Toast.LENGTH_LONG).show()
        }
    }

    // Lanzador para solicitar permisos en tiempo de ejecución
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: true
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: true
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: true

        if (!connectGranted || !scanGranted) {
            Toast.makeText(
                this,
                "Se requieren permisos de Bluetooth para buscar y conectarse al dispositivo.",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!audioGranted) {
            Toast.makeText(
                this,
                "El permiso de micrófono es necesario para la traducción de voz.",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!cameraGranted) {
            Toast.makeText(
                this,
                "El permiso de cámara es necesario para el reconocimiento de señas por video.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Lanzador para el reconocimiento de voz de Google (Speech to Text)
    private val voiceRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val spokenTextList = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenTextList?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                onVoiceResultCallback?.invoke(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothController = BluetoothController(this)

        // Solicitar permisos necesarios al arrancar la app
        checkAndRequestPermissions()

        setContent {
            ManoRoboticaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    var showCameraScreen by remember { mutableStateOf(false) }

                    if (showCameraScreen) {
                        CameraSignScreen(
                            bluetoothController = bluetoothController,
                            onBack = { showCameraScreen = false }
                        )
                    } else {
                        ControlScreen(
                            bluetoothController = bluetoothController,
                            onEnableBluetoothRequested = {
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                enableBluetoothLauncher.launch(intent)
                            },
                            onStartVoiceRecognitionRequested = { onResult ->
                                startVoiceRecognition(onResult)
                            },
                            onOpenCameraRequested = {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    showCameraScreen = true
                                } else {
                                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startVoiceRecognition(onResult: (String) -> Unit) {
        // Verificar si tenemos el permiso de grabación de audio primero
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        onVoiceResultCallback = onResult
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX") // Configurar Español Mexicano por defecto
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para traducir a Señas...")
        }
        try {
            voiceRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible en este dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Solicitar grabación de audio para traducción por voz
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Solicitar cámara para reconocimiento por video
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 y superior requiere permisos específicos de escaneo y conexión
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            // Android 11 y anterior requiere ubicación física para buscar dispositivos
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothController.disconnect()
    }
}
