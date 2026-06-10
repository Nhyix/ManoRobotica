package com.example.manorobotica.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.manorobotica.bluetooth.BluetoothController
import com.example.manorobotica.bluetooth.ConnectionState
import com.example.manorobotica.camera.HandLandmarkProcessor
import com.example.manorobotica.ui.theme.AccentPink
import com.example.manorobotica.ui.theme.CardBackgroundGlass
import com.example.manorobotica.ui.theme.CardBorderGlass
import com.example.manorobotica.ui.theme.DeepDarkNavy
import com.example.manorobotica.ui.theme.ErrorRed
import com.example.manorobotica.ui.theme.NeonCyan
import com.example.manorobotica.ui.theme.SuccessGreen
import com.example.manorobotica.ui.theme.TextGray
import com.example.manorobotica.ui.theme.TextWhite
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

enum class CameraSource { PHONE_CAMERA, ESP32_CAM }

class CameraSharedState {
    @Volatile var enabled: Boolean = false
    @Volatile var sendToBle: Boolean = true
    @Volatile var handDetected: Boolean = false
    @Volatile var fps: Float = 0f
    val landmarks = mutableListOf<Pair<Float, Float>>()
    @Volatile var imgWidth: Int = 0
    @Volatile var imgHeight: Int = 0
    @Volatile var frameCount: Int = 0
    @Volatile var lastFpsTimeMs: Long = 0L
    var smoothingBuffer = FloatArray(5)

    val proxyQueue = ConcurrentLinkedDeque<ImageProxy>()
    @Volatile var rotationDegrees: Int = 0

    fun enqueueProxy(proxy: ImageProxy) {
        proxyQueue.addLast(proxy)
    }

    fun closeNextProxy() {
        proxyQueue.pollFirst()?.close()
    }
}

@Composable
fun CameraSignScreen(
    modifier: Modifier = Modifier,
    bluetoothController: BluetoothController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTracking = true
    val isSendingToBLE = true
    val connectionState by bluetoothController.connectionState.collectAsState()

    var smoothedAngles by remember { mutableStateOf(FloatArray(5)) }
    var isHandDetected by remember { mutableStateOf(false) }
    var trackingFps by remember { mutableStateOf(0f) }
    var landmarkPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var imageDimensions by remember { mutableStateOf(Pair(0, 0)) }

    val shared = remember { CameraSharedState() }

    var cameraFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // --- ESP32-CAM state ---
    var cameraSource by remember { mutableStateOf(CameraSource.PHONE_CAMERA) }
    var espCamIp by remember { mutableStateOf("192.168.4.1") }
    var isEspCamConnected by remember { mutableStateOf(false) }
    var espCamFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isEspCamLoading by remember { mutableStateOf(false) }

    val handLandmarker = remember {
        val display = (context.getSystemService(WindowManager::class.java)).defaultDisplay
        val displayDeg = when (display.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorRot = 90
        shared.rotationDegrees = (sensorRot + displayDeg) % 360
        Log.d("CameraSign", "rotationDegrees init = ${shared.rotationDegrees}")

        try {
            HandLandmarker.createFromOptions(
                context,
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath("hand_landmarker.task")
                            .build()
                    )
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setResultListener { result: HandLandmarkerResult, _: MPImage ->
                        try {
                            if (result.landmarks().isNotEmpty()) {
                                val lms = result.landmarks()[0]
                                val angles = HandLandmarkProcessor.computeAngles(result)
                                if (angles != null) {
                                    val smooth = 0.4f
                                    for (i in 0..4) {
                                        shared.smoothingBuffer[i] += (angles[i] - shared.smoothingBuffer[i]) * smooth
                                    }
                                }
                                shared.handDetected = true

                                synchronized(shared.landmarks) {
                                    shared.landmarks.clear()
                                    lms.forEach { lm ->
                                        shared.landmarks.add(Pair(lm.x(), lm.y()))
                                    }
                                }
                            } else {
                                shared.handDetected = false
                                synchronized(shared.landmarks) { shared.landmarks.clear() }
                            }
                        } finally {
                            shared.closeNextProxy()
                        }
                    }
                    .setErrorListener { e ->
                        Log.e("HandLandmarker", "Error: ${e.message}")
                    }
                    .build()
            )
        } catch (e: Exception) {
            Log.e("HandLandmarker", "Failed to create", e)
            null
        }
    }

    val timestampGen = remember { AtomicLong(0) }

    // Sync UI state -> shared state
    LaunchedEffect(isTracking) { shared.enabled = isTracking }
    LaunchedEffect(isSendingToBLE) { shared.sendToBle = isSendingToBLE }

    // Poll shared state -> UI state
    LaunchedEffect(Unit) {
        while (isActive) {
            smoothedAngles = shared.smoothingBuffer.copyOf()
            isHandDetected = shared.handDetected
            trackingFps = shared.fps
            synchronized(shared.landmarks) {
                landmarkPoints = shared.landmarks.toList()
            }
            if (shared.imgWidth > 0 && shared.imgHeight > 0) {
                imageDimensions = Pair(shared.imgWidth, shared.imgHeight)
            }
            delay(33)
        }
    }

    // Send angles via BLE
    var lastSendMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            if (shared.sendToBle && shared.handDetected) {
                val now = System.currentTimeMillis()
                if (now - lastSendMs >= 50) {
                    lastSendMs = now
                    val a = shared.smoothingBuffer
                    val cmd = "S:${a[0].roundToInt()},${a[1].roundToInt()},${a[2].roundToInt()},${a[3].roundToInt()},${a[4].roundToInt()}\n"
                    bluetoothController.sendCommand(cmd, scope)
                }
            }
            delay(20)
        }
    }

    // MJPEG stream reader for ESP32-CAM
    if (cameraSource == CameraSource.ESP32_CAM && isEspCamConnected) {
        val url = "http://$espCamIp/stream"
        LaunchedEffect(url) {
            isEspCamLoading = true
            try {
                withContext(Dispatchers.IO) {
                    readMjpegStream(url, shared, handLandmarker, timestampGen) { bitmap ->
                        espCamFrameBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                Log.e("MjpegStream", "Stream error: ${e.message}")
            } finally {
                isEspCamConnected = false
                isEspCamLoading = false
                shared.handDetected = false
                synchronized(shared.landmarks) { shared.landmarks.clear() }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${trackingFps.toInt()} FPS",
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (handLandmarker == null) {
                        Text(
                            text = "Error: modelo no cargado",
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val statusColor = when (connectionState) {
                    is ConnectionState.Connected -> SuccessGreen
                    else -> ErrorRed
                }
                val statusIcon = when (connectionState) {
                    is ConnectionState.Connected -> Icons.Default.BluetoothConnected
                    else -> Icons.Default.BluetoothDisabled
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Source selector: Phone Camera / ESP32-CAM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (cameraSource != CameraSource.PHONE_CAMERA) {
                            cameraSource = CameraSource.PHONE_CAMERA
                            isEspCamConnected = false
                            espCamFrameBitmap = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (cameraSource == CameraSource.PHONE_CAMERA) NeonCyan else TextGray
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camara Telefono", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        if (cameraSource != CameraSource.ESP32_CAM) {
                            cameraSource = CameraSource.ESP32_CAM
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (cameraSource == CameraSource.ESP32_CAM) NeonCyan else TextGray
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ESP32-CAM", fontWeight = FontWeight.Bold)
                }
            }

            // ESP32-CAM connection controls
            if (cameraSource == CameraSource.ESP32_CAM) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = espCamIp,
                        onValueChange = { espCamIp = it },
                        label = { Text("Direccion IP", color = TextGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri
                        ),
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = TextWhite),
                        enabled = !isEspCamConnected
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isEspCamConnected) {
                                isEspCamConnected = false
                                espCamFrameBitmap = null
                                shared.handDetected = false
                            } else {
                                isEspCamConnected = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEspCamConnected) ErrorRed else SuccessGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isEspCamConnected) "Desconectar" else "Conectar",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isEspCamLoading && !isEspCamConnected) {
                    Text(
                        text = "Conectando...",
                        color = TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // Camera preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, CardBorderGlass, RoundedCornerShape(20.dp))
            ) {
                // Phone Camera: CameraX
                if (cameraSource == CameraSource.PHONE_CAMERA) {
                    val currentFacing = cameraFacing
                    key(currentFacing) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build()
                                    preview.setSurfaceProvider(previewView.surfaceProvider)

                                    val analysisExecutor = Executors.newSingleThreadExecutor()
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(640, 480))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                        .build()
                                    imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                                        analyzeFrame(proxy, handLandmarker, shared, timestampGen)
                                    }

                                    val selector = CameraSelector.Builder()
                                        .requireLensFacing(currentFacing)
                                        .build()

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner, selector, preview, imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        Log.e("CameraSign", "bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Flip camera button
                    IconButton(
                        onClick = {
                            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT
                            else
                                CameraSelector.LENS_FACING_BACK
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip camera",
                            tint = TextWhite
                        )
                    }
                }

                // ESP32-CAM: show stream frame
                if (cameraSource == CameraSource.ESP32_CAM) {
                    val bitmap = espCamFrameBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "ESP32-CAM stream",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillHeight
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DeepDarkNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEspCamConnected) "Esperando stream..."
                                      else "Conecta a ESP32-CAM",
                                color = TextGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Landmark overlay (works for both sources)
                if (landmarkPoints.isNotEmpty() && imageDimensions.first > 0) {
                    val rot = shared.rotationDegrees
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val viewW = size.width
                        val viewH = size.height
                        val imgW = imageDimensions.first.toFloat()
                        val imgH = imageDimensions.second.toFloat()

                        val dispW = if (rot == 90 || rot == 270) imgH else imgW
                        val dispH = if (rot == 90 || rot == 270) imgW else imgH
                        val scaleX = viewW / dispW
                        val scaleY = viewH / dispH
                        val scale = minOf(scaleX, scaleY)
                        val offX = (viewW - dispW * scale) / 2f
                        val offY = (viewH - dispH * scale) / 2f

                        fun tx(x: Float, y: Float): Pair<Float, Float> {
                            return when (rot) {
                                90 -> Pair(1f - y, x)
                                180 -> Pair(1f - x, 1f - y)
                                270 -> Pair(y, 1f - x)
                                else -> Pair(x, y)
                            }
                        }

                        for (pt in landmarkPoints) {
                            val (tx_, ty_) = tx(pt.first, pt.second)
                            val cx = tx_ * dispW * scale + offX
                            val cy = ty_ * dispH * scale + offY
                            drawCircle(
                                color = NeonCyan,
                                radius = 6f * scale,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                if (!isHandDetected && cameraSource == CameraSource.PHONE_CAMERA) {
                    Text(
                        text = if (handLandmarker == null) "Cargando modelo..." else "Mano no detectada",
                        color = if (handLandmarker == null) TextGray else ErrorRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Angles display
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackgroundGlass),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Angulos de la mano", color = TextWhite, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val labels = arrayOf("Pulgar", "Indice", "Medio", "Anular", "Menique")
                    for (i in 0..4) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${labels[i]}: ${smoothedAngles[i].roundToInt()}°",
                                color = TextGray,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Connect button
            Button(
                onClick = {
                    when (val state = connectionState) {
                        is ConnectionState.Connected -> bluetoothController.disconnect()
                        is ConnectionState.Error -> bluetoothController.disconnect()
                        else -> {}
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState is ConnectionState.Connected) ErrorRed else AccentPink
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> "Desconectar"
                        is ConnectionState.Disconnected -> "Bluetooth: Desconectado"
                        is ConnectionState.Connecting -> "Conectando..."
                        is ConnectionState.Error -> "Error de conexion"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ===================== Phone Camera: CameraX analysis =====================

private fun analyzeFrame(
    proxy: ImageProxy,
    handLandmarker: HandLandmarker?,
    shared: CameraSharedState,
    timestampGen: AtomicLong
) {
    shared.enqueueProxy(proxy)

    if (handLandmarker == null || !shared.enabled) {
        shared.closeNextProxy()
        proxy.close()
        return
    }

    val now = System.currentTimeMillis()
    shared.frameCount++
    val elapsed = now - shared.lastFpsTimeMs
    if (elapsed >= 1000) {
        shared.fps = shared.frameCount.toFloat() / (elapsed / 1000f)
        shared.frameCount = 0
        shared.lastFpsTimeMs = now
    }

    try {
        val rotDeg = proxy.imageInfo.rotationDegrees
        Log.d("analyzeFrame", "Rotation = $rotDeg, handLandmarker is ${if (handLandmarker == null) "null" else "ok"}")
        val ppmImage = proxyToRotatedMediaPipeImage(proxy)
        if (ppmImage == null) {
            Log.e("analyzeFrame", "ppmImage is null")
            shared.closeNextProxy()
            proxy.close()
            return
        }
        Log.d("analyzeFrame", "MPImage size: ${ppmImage.width}x${ppmImage.height}")

        shared.imgWidth = ppmImage.width
        shared.imgHeight = ppmImage.height
        shared.rotationDegrees = 0

        val ts = timestampGen.incrementAndGet()
        handLandmarker.detectAsync(ppmImage, ts)
        Log.d("analyzeFrame", "detectAsync called, ts=$ts")
    } catch (e: Exception) {
        Log.e("analyzeFrame", "Error processing frame", e)
        shared.closeNextProxy()
        proxy.close()
    }
}

private fun proxyToRotatedMediaPipeImage(proxy: ImageProxy): MPImage? {
    val bitmap = rgba8888ToBitmap(proxy)
    if (bitmap == null) {
        Log.e("proxyToRot", "rgba8888ToBitmap returned null")
        return null
    }

    val rotation = proxy.imageInfo.rotationDegrees
    val rotated = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }

    return try {
        BitmapImageBuilder(rotated).build()
    } catch (e: Exception) {
        Log.e("proxyToRot", "BitmapImageBuilder failed", e)
        null
    }
}

private fun rgba8888ToBitmap(proxy: ImageProxy): Bitmap? {
    val planes = proxy.planes
    if (planes.isEmpty()) return null
    val buffer = planes[0].buffer
    val width = proxy.width
    val height = proxy.height
    val stride = planes[0].rowStride
    buffer.rewind()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (stride == width * 4) {
        bitmap.copyPixelsFromBuffer(buffer)
    } else {
        val row = ByteArray(stride)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            buffer.position(y * stride)
            buffer.get(row, 0, stride)
            for (x in 0 until width) {
                val srcIdx = x * 4
                val r = row[srcIdx].toInt() and 0xFF
                val g = row[srcIdx + 1].toInt() and 0xFF
                val b = row[srcIdx + 2].toInt() and 0xFF
                val a = row[srcIdx + 3].toInt() and 0xFF
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
    return bitmap
}

// ===================== ESP32-CAM: MJPEG stream reader =====================

private suspend fun readMjpegStream(
    url: String,
    shared: CameraSharedState,
    handLandmarker: HandLandmarker?,
    timestampGen: AtomicLong,
    onFrame: (Bitmap) -> Unit
) {
    var connection: HttpURLConnection? = null

    try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 0
        connection.setRequestProperty("Accept", "*/*")
        connection.connect()

        val contentType = connection.contentType ?: "multipart/x-mixed-replace; boundary=frame_boundary"
        val boundary = contentType.substringAfter("boundary=", "frame_boundary").trim()
        val boundaryBytes = ("--$boundary").toByteArray()
        val stream = BufferedInputStream(connection.inputStream)

        var frameCount = 0L
        var lastFpsTime = System.currentTimeMillis()

        while (true) {
            // Find next boundary in stream
            if (!findBytes(stream, boundaryBytes)) break

            // Check for end boundary (--boundary--)
            stream.mark(2)
            val b1 = stream.read()
            val b2 = stream.read()
            if (b1 == '-'.code && b2 == '-'.code) break
            stream.reset()

            // Read headers until empty line
            var contentLength = 0
            while (true) {
                val line = readLineFromStream(stream) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (contentLength > 0) {
                val jpegData = ByteArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val read = stream.read(jpegData, offset, contentLength - offset)
                    if (read == -1) break
                    offset += read
                }

                val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                    ?: continue

                // FPS calculation
                frameCount++
                val now = System.currentTimeMillis()
                val elapsed = now - lastFpsTime
                if (elapsed >= 1000) {
                    shared.fps = (frameCount.toFloat() / (elapsed / 1000f))
                    frameCount = 0
                    lastFpsTime = now
                }

                // Send to MediaPipe
                shared.imgWidth = bitmap.width
                shared.imgHeight = bitmap.height
                shared.rotationDegrees = 0

                if (handLandmarker != null) {
                    try {
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        val ts = timestampGen.incrementAndGet()
                        handLandmarker.detectAsync(mpImage, ts)
                    } catch (e: Exception) {
                        Log.e("MjpegStream", "MediaPipe error: ${e.message}")
                    }
                }

                onFrame(bitmap)
            }
        }
    } catch (e: Exception) {
        Log.e("MjpegStream", "Stream error: ${e.message}")
        throw e
    } finally {
        try {
            connection?.disconnect()
        } catch (_: Exception) {}
    }
}

private fun findBytes(stream: InputStream, target: ByteArray): Boolean {
    var matchIdx = 0
    while (matchIdx < target.size) {
        val b = stream.read()
        if (b == -1) return false
        if (b.toByte() == target[matchIdx]) {
            matchIdx++
        } else {
            matchIdx = 0
            if (b.toByte() == target[0]) {
                matchIdx = 1
            }
        }
    }
    return true
}

private fun readLineFromStream(stream: InputStream): String? {
    val sb = StringBuilder()
    while (true) {
        val b = stream.read()
        if (b == -1) return if (sb.isEmpty()) null else sb.toString()
        if (b == '\n'.code) break
        if (b != '\r'.code) sb.append(b.toChar())
    }
    return sb.toString()
}
