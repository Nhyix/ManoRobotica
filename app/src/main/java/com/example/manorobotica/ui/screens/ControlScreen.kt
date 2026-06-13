package com.example.manorobotica.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manorobotica.bluetooth.BluetoothController
import com.example.manorobotica.bluetooth.ConnectionState
import com.example.manorobotica.ui.theme.AccentPink
import com.example.manorobotica.ui.theme.CardBackgroundGlass
import com.example.manorobotica.ui.theme.CardBorderGlass
import com.example.manorobotica.ui.theme.DeepDarkNavy
import com.example.manorobotica.ui.theme.ErrorRed
import com.example.manorobotica.ui.theme.NeonCyan
import com.example.manorobotica.ui.theme.NeonCyanLight
import com.example.manorobotica.ui.theme.NeonPurple
import com.example.manorobotica.ui.theme.NeonPurpleLight
import com.example.manorobotica.ui.theme.SuccessGreen
import com.example.manorobotica.ui.theme.TextGray
import com.example.manorobotica.ui.theme.TextWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ControlScreen(
    bluetoothController: BluetoothController,
    onEnableBluetoothRequested: () -> Unit,
    onStartVoiceRecognitionRequested: ((String) -> Unit) -> Unit,
    onOpenCameraRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by bluetoothController.connectionState.collectAsState()
    val serialLog by bluetoothController.serialLog.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Estado local para los ángulos de los dedos
    var thumbVal by remember { mutableFloatStateOf(0f) }
    var indexVal by remember { mutableFloatStateOf(0f) }
    var middleVal by remember { mutableFloatStateOf(0f) }
    var ringVal by remember { mutableFloatStateOf(0f) }
    var pinkyVal by remember { mutableFloatStateOf(0f) }

    // Estados para la traducción de voz
    var spokenText by remember { mutableStateOf("") }
    var activeSpellingLetter by remember { mutableStateOf("") }
    var activeSpellingProgress by remember { mutableFloatStateOf(0f) }
    var voiceTranslationJob by remember { mutableStateOf<Job?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    // Animación de latido para el micrófono
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Función auxiliar para enviar ángulos al bluetooth
    val sendAngles = { t: Float, i: Float, m: Float, a: Float, p: Float ->
        val command = "S:${t.roundToInt()},${i.roundToInt()},${m.roundToInt()},${a.roundToInt()},${p.roundToInt()}\n"
        bluetoothController.sendCommand(command, scope)
    }

    // Lista de Gestos de Lenguaje de Señas Predefinidos
    val gestures = remember {
        listOf(
            GestureItem("Mano Abierta", "Posición inicial / Relax", 0f, 0f, 0f, 0f, 0f),
            GestureItem("Puño (Cerrado)", "Mano completamente cerrada", 90f, 90f, 90f, 90f, 90f),
            GestureItem("Seña A (LSM)", "Puño cerrado, pulgar al lado", 0f, 90f, 90f, 90f, 90f),
            GestureItem("Seña B (LSM)", "Mano plana, pulgar doblado", 90f, 0f, 0f, 0f, 0f),
            GestureItem("Seña C (LSM)", "Forma de garra / C curvada", 45f, 45f, 45f, 45f, 45f),
            GestureItem("Seña L (LSM)", "Letra L con índice y pulgar", 0f, 0f, 90f, 90f, 90f),
            GestureItem("Seña V (Victoria)", "Símbolo de paz", 90f, 0f, 0f, 90f, 90f),
            GestureItem("Te Amo (Love)", "Seña universal ILY", 0f, 0f, 90f, 90f, 0f),
            GestureItem("Seña OK", "Pulgar e índice tocándose", 90f, 90f, 0f, 0f, 0f)
        )
    }

    val alphabetSignAngles = remember {
        mapOf(
            'a' to floatArrayOf(0f, 90f, 90f, 90f, 90f),         // Seña A: Puño cerrado, pulgar al lado
            'b' to floatArrayOf(90f, 0f, 0f, 0f, 0f),           // Seña B: Mano plana extendida
            'c' to floatArrayOf(45f, 45f, 45f, 45f, 45f),       // Seña C: Mano en forma de "C"
            'd' to floatArrayOf(90f, 0f, 90f, 90f, 90f),        // Seña D: Solo índice levantado
            'e' to floatArrayOf(70f, 70f, 70f, 70f, 70f),       // Seña E: Dedos semi-doblados hacia la palma
            'f' to floatArrayOf(90f, 90f, 0f, 0f, 0f),          // Seña F: Índice y pulgar tocándose, otros extendidos
            'g' to floatArrayOf(0f, 0f, 90f, 90f, 90f),         // Seña G: Índice y pulgar extendidos horizontalmente
            'h' to floatArrayOf(90f, 0f, 0f, 90f, 90f),         // Seña H: Índice y medio extendidos lateralmente
            'i' to floatArrayOf(90f, 90f, 90f, 90f, 0f),        // Seña I: Solo meñique levantado
            'j' to floatArrayOf(90f, 90f, 90f, 90f, 15f),       // Seña J: Meñique levantado dibujando curva (15°)
            'k' to floatArrayOf(45f, 0f, 0f, 90f, 90f),         // Seña K: Índice y medio levantados con pulgar en medio
            'l' to floatArrayOf(0f, 0f, 90f, 90f, 90f),         // Seña L: Índice y pulgar formando una "L"
            'm' to floatArrayOf(80f, 80f, 80f, 90f, 90f),       // Seña M: Tres dedos replegados sobre pulgar
            'n' to floatArrayOf(80f, 80f, 90f, 90f, 90f),       // Seña N: Dos dedos replegados sobre pulgar
            'ñ' to floatArrayOf(75f, 75f, 90f, 90f, 90f),       // Seña Ñ: Igual que N con inclinación/oscilación
            'o' to floatArrayOf(65f, 65f, 65f, 65f, 65f),       // Seña O: Todos los dedos formando un círculo
            'p' to floatArrayOf(45f, 0f, 45f, 90f, 90f),        // Seña P: Índice extendido, medio y pulgar en ángulo
            'q' to floatArrayOf(45f, 45f, 90f, 90f, 90f),       // Seña Q: Garra hacia abajo con índice y pulgar
            'r' to floatArrayOf(90f, 5f, 5f, 90f, 90f),         // Seña R: Índice y medio cruzados
            's' to floatArrayOf(90f, 90f, 90f, 90f, 90f),       // Seña S: Puño cerrado con pulgar al frente
            't' to floatArrayOf(90f, 60f, 90f, 90f, 90f),       // Seña T: Pulgar entre índice y medio doblado
            'u' to floatArrayOf(90f, 0f, 0f, 90f, 90f),         // Seña U: Índice y medio levantados pegados
            'v' to floatArrayOf(90f, 0f, 0f, 90f, 90f),         // Seña V: Índice y medio levantados separados
            'w' to floatArrayOf(90f, 0f, 0f, 0f, 90f),          // Seña W: Índice, medio y anular extendidos
            'x' to floatArrayOf(90f, 45f, 90f, 90f, 90f),       // Seña X: Índice encorvado en forma de gancho
            'y' to floatArrayOf(0f, 90f, 90f, 90f, 0f),         // Seña Y: Pulgar y meñique extendidos (forma de fono)
            'z' to floatArrayOf(90f, 0f, 90f, 90f, 90f)         // Seña Z: Índice levantado para trazar la letra
        )
    }

    // Función que procesa y traduce la voz capturada
    val processVoiceTranslation = { text: String ->
        spokenText = text
        val cleanedText = text.lowercase(Locale.ROOT).trim()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')

        // Detener traducción anterior si existía una ejecutándose
        voiceTranslationJob?.cancel()
        isTranslating = false
        activeSpellingLetter = ""
        activeSpellingProgress = 0f

        // 1. EVALUAR SI COINCIDE CON UN GESTO COMPLETO (PALABRA CLAVE)
        var matchedGesture: GestureItem? = null
        if (cleanedText.contains("te amo") || cleanedText.contains("love") || cleanedText.contains("amor")) {
            matchedGesture = gestures.find { it.name.contains("Te Amo") }
        } else if (cleanedText.contains("hola") || cleanedText.contains("saludo") || cleanedText.contains("abrir") || cleanedText.contains("abre")) {
            matchedGesture = gestures.find { it.name.contains("Abierta") }
        } else if (cleanedText.contains("cerrar") || cleanedText.contains("cierra") || cleanedText.contains("puño") || cleanedText.contains("puño")) {
            matchedGesture = gestures.find { it.name.contains("Puño") }
        } else if (cleanedText.contains("victoria") || cleanedText.contains("paz") || cleanedText.contains("dos")) {
            matchedGesture = gestures.find { it.name.contains("Victoria") }
        } else if (cleanedText.contains("ok") || cleanedText.contains("bien") || cleanedText.contains("entendido")) {
            matchedGesture = gestures.find { it.name.contains("OK") }
        }

        if (matchedGesture != null) {
            // Coincidencia completa: Mover mano instantáneamente
            thumbVal = matchedGesture.thumb
            indexVal = matchedGesture.index
            middleVal = matchedGesture.middle
            ringVal = matchedGesture.ring
            pinkyVal = matchedGesture.pinky
            sendAngles(thumbVal, indexVal, middleVal, ringVal, pinkyVal)
            Toast.makeText(context, "Traducido gesto completo: ${matchedGesture.name}", Toast.LENGTH_SHORT).show()
        } else {
            // 2. MODO DELETREO SECUENCIAL (LETRA POR LETRA)
            // Filtrar y extraer letras válidas que tengan correspondencia de señas
            val lettersToSpell = cleanedText.filter { it in alphabetSignAngles.keys }

            if (lettersToSpell.isNotEmpty()) {
                isTranslating = true
                voiceTranslationJob = scope.launch {
                    Toast.makeText(context, "Deletreando secuencia de ${lettersToSpell.length} letras...", Toast.LENGTH_SHORT).show()
                    
                    for (index in lettersToSpell.indices) {
                        val char = lettersToSpell[index]
                        activeSpellingLetter = char.uppercase()
                        activeSpellingProgress = (index + 1).toFloat() / lettersToSpell.length

                        // Obtener los ángulos específicos para esa letra
                        val angles = alphabetSignAngles[char]
                        if (angles != null) {
                            thumbVal = angles[0]
                            indexVal = angles[1]
                            middleVal = angles[2]
                            ringVal = angles[3]
                            pinkyVal = angles[4]
                            
                            // Enviar comando Bluetooth
                            sendAngles(thumbVal, indexVal, middleVal, ringVal, pinkyVal)
                        }
                        
                        // Retardo de 1.7 segundos para darle tiempo a la mano robótica de realizar la seña antes de pasar a la siguiente letra
                        delay(1700)
                    }
                    
                    // Fin de la secuencia: Regresar a mano abierta suavemente
                    activeSpellingLetter = "✓ Hecho"
                    delay(1000)
                    isTranslating = false
                    activeSpellingLetter = ""
                }
            } else {
                Toast.makeText(context, "No se encontraron señas compatibles en: '$text'", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Menú de dispositivos Bluetooth BLE descubiertos
    var isDeviceMenuExpanded by remember { mutableStateOf(false) }
    val scannedDevices by bluetoothController.scannedDevices.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepDarkNavy, Color(0xFF131525), DeepDarkNavy)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. TÍTULO DE LA APLICACIÓN Y SUBTÍTULO
            Text(
                text = "CYBER HAND",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonCyan,
                letterSpacing = 3.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Controlador de Mano Robótica - LSM",
                fontSize = 12.sp,
                color = TextGray,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. TARJETA DE CONEXIÓN BLUETOOTH
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, CardBorderGlass), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundGlass),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (statusText, statusColor, statusIcon) = when (connectionState) {
                                is ConnectionState.Disconnected -> Triple("Desconectado", TextGray, Icons.Default.BluetoothDisabled)
                                is ConnectionState.Connecting -> Triple("Conectando...", NeonPurple, Icons.Default.Bluetooth)
                                is ConnectionState.Connected -> Triple("Conectado", SuccessGreen, Icons.Default.BluetoothConnected)
                                is ConnectionState.Error -> Triple("Error de conexión", ErrorRed, Icons.Default.BluetoothDisabled)
                            }
                            
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Estado del Módulo",
                                    fontSize = 11.sp,
                                    color = TextGray
                                )
                                Text(
                                    text = statusText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        Row {
                            if (connectionState is ConnectionState.Connected) {
                                Button(
                                    onClick = { bluetoothController.disconnect() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Desconectar", fontSize = 12.sp, color = TextWhite)
                                }
                            } else {
                                Box {
                                    Button(
                                        onClick = {
                                            if (!bluetoothController.isBluetoothEnabled()) {
                                                onEnableBluetoothRequested()
                                            } else {
                                                bluetoothController.startScanning(scope)
                                                isDeviceMenuExpanded = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan.copy(alpha = 0.2f),
                                            contentColor = NeonCyan
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                                    ) {
                                        Text("Conectar", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isDeviceMenuExpanded,
                                        onDismissRequest = {
                                            isDeviceMenuExpanded = false
                                            bluetoothController.stopScanning()
                                        },
                                        modifier = Modifier
                                            .background(DeepDarkNavy)
                                            .border(BorderStroke(1.dp, CardBorderGlass), RoundedCornerShape(8.dp))
                                    ) {
                                        if (scannedDevices.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Buscando dispositivos...", color = TextGray) },
                                                onClick = { }
                                            )
                                        } else {
                                            scannedDevices.forEach { device ->
                                                val name = device.name ?: "Dispositivo sin nombre"
                                                val address = device.address
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(name, color = TextWhite, fontWeight = FontWeight.SemiBold)
                                                            Text(address, color = TextGray, fontSize = 10.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        bluetoothController.connectToDevice(device, scope)
                                                        isDeviceMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (connectionState is ConnectionState.Connected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dispositivo: ${(connectionState as ConnectionState.Connected).deviceName}",
                            color = NeonCyanLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. SECCIÓN DE RECONOCIMIENTO DE VOZ E INTELIGENCIA
            Text(
                text = "Traductor de Voz a Señas",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, AccentPink.copy(alpha = 0.4f)), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundGlass),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón circular flotante de Micrófono con pulsación animada
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(if (isTranslating) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = if (isTranslating) listOf(AccentPink, NeonPurple) else listOf(NeonCyan, NeonPurple)
                                )
                            )
                            .clickable {
                                onStartVoiceRecognitionRequested { voiceResult ->
                                    processVoiceTranslation(voiceResult)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Activar Micrófono",
                            tint = TextWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Toca para Hablar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Prueba diciendo: 'Te Amo' o deletrea letras como 'A B C L L'",
                            fontSize = 10.sp,
                            color = TextGray
                        )

                        if (spokenText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"$spokenText\"",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyanLight,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Visualizador del estado de deletreo dinámico
            AnimatedVisibility(
                visible = isTranslating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .border(BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = NeonPurpleLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Deletreando Secuencia...",
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Text(
                                text = "Letra: $activeSpellingLetter",
                                color = AccentPink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { activeSpellingProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonPurple,
                            trackColor = Color.DarkGray,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. RECONOCIMIENTO POR CÁMARA
            Text(
                text = "Reconocimiento por Cámara",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundGlass),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(NeonCyan, NeonPurple)
                                )
                            )
                            .clickable { onOpenCameraRequested() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Abrir Cámara",
                            tint = TextWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cámara en Vivo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Detecta tu mano con la cámara y replica los movimientos en la mano robótica en tiempo real",
                            fontSize = 10.sp,
                            color = TextGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. SECCIÓN DEL VISUALIZADOR DE MANO ROBÓTICA (CANVAS)
            Text(
                text = "Holograma de Monitoreo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HandVisualizer(
                thumbAngle = thumbVal,
                indexAngle = indexVal,
                middleAngle = middleVal,
                ringAngle = ringVal,
                pinkyAngle = pinkyVal,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 6. CONTROL DE SERVOS INDIVIDUALES (SLIDERS)
            Text(
                text = "Ajuste de Servomotores (Dedos)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, CardBorderGlass), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundGlass),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val fingers = listOf(
                        Triple("Pulgar", thumbVal, { v: Float -> thumbVal = v }),
                        Triple("Índice", indexVal, { v: Float -> indexVal = v }),
                        Triple("Medio", middleVal, { v: Float -> middleVal = v }),
                        Triple("Anular", ringVal, { v: Float -> ringVal = v }),
                        Triple("Meñique", pinkyVal, { v: Float -> pinkyVal = v })
                    )

                    fingers.forEachIndexed { idx, (label, value, onValChange) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                                Text(
                                    text = "${value.roundToInt()}°",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (idx == 0) NeonCyanLight else NeonPurpleLight
                                )
                            }
                            Slider(
                                value = value,
                                onValueChange = {
                                    onValChange(it)
                                    sendAngles(thumbVal, indexVal, middleVal, ringVal, pinkyVal)
                                },
                                valueRange = 0f..90f,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (idx == 0) NeonCyan else NeonPurple,
                                    activeTrackColor = if (idx == 0) NeonCyan.copy(alpha = 0.8f) else NeonPurple.copy(alpha = 0.8f),
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 7. GESTOS PREDEFINIDOS DE LENGUAJE DE SEÑAS
            Text(
                text = "Biblioteca de Lenguaje de Señas (Gestos)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gestures.forEach { gesture ->
                    Surface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)), RoundedCornerShape(16.dp))
                            .clickable {
                                thumbVal = gesture.thumb
                                indexVal = gesture.index
                                middleVal = gesture.middle
                                ringVal = gesture.ring
                                pinkyVal = gesture.pinky
                                sendAngles(gesture.thumb, gesture.index, gesture.middle, gesture.ring, gesture.pinky)
                            },
                        color = CardBackgroundGlass,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = gesture.name,
                                color = NeonCyanLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = gesture.description,
                                color = TextGray,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 8. CONSOLA DE DEPURE / LOGS EN TIEMPO REAL
            var isConsoleExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, CardBorderGlass.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isConsoleExpanded = !isConsoleExpanded }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (connectionState is ConnectionState.Connected) SuccessGreen else ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Consola Serial Bluetooth",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isConsoleExpanded) {
                                IconButton(
                                    onClick = { bluetoothController.clearLogs() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Limpiar Consola",
                                        tint = TextGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(
                                imageVector = if (isConsoleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isConsoleExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(12.dp)
                        ) {
                            if (serialLog.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Consola vacía. Envía comandos para ver datos.",
                                        color = TextGray,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    reverseLayout = true
                                ) {
                                    items(serialLog.reversed()) { logLine ->
                                        Text(
                                            text = logLine,
                                            color = if (logLine.startsWith("Error")) ErrorRed else if (logLine.startsWith("Enviado")) NeonCyanLight else SuccessGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

data class GestureItem(
    val name: String,
    val description: String,
    val thumb: Float,
    val index: Float,
    val middle: Float,
    val ring: Float,
    val pinky: Float
)
