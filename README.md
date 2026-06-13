# ManoRobotica

Control de mano robótica mediante reconocimiento de lenguaje de señas, control por voz y gestos capturados por cámara.

# Integrantes del Proyecto:

•	García Rodríguez Héctor Mauricio (Control: 22240332)
•	Bernal Tolentino Raziel (Control: 22240232)
•	Zamora Martínez Kimberly Paola del Rocío (Control: 22240276)

## Arquitectura del Sistema

```
App Android (Control Mano Robótica)
  ├── Reconocimiento de manos por cámara (MediaPipe HandLandmarker)
  │     ├── Cámara del teléfono (Phone Camera)
  │     └── ESP32-CAM (stream MJPEG vía WiFi)
  ├── Reconocimiento de voz (Google Speech-to-Text)
  ├── Biblioteca de gestos predefinidos (LSM - Lenguaje de Señas Mexicano)
  └── Comunicación Bluetooth Low Energy (BLE)
        └── Protocolo NUS (Nordic UART Service)
              └── ESP32 (MicroPython) o Arduino (HC-05/HC-06)
                    └── 5 servomotores (Pulgar, Índice, Medio, Anular, Meñique)
```

## Componentes del Proyecto

### 1. Firmware para Robotic Hand

#### a) ESP32 con MicroPython (`robotic_hand_micropython/main.py`)
- Controla 5 servos con PWM y transiciones suaves (interpolación lineal)
- Se comunica por BLE usando el Nordic UART Service (NUS)
- Se anuncia como `ESP32-Mano`
- Pines: Pulgar=13, Índice=12, Medio=14, Anular=27, Meñique=26

#### b) Arduino con HC-05/HC-06 (`robotic_hand_arduino/robotic_hand_arduino.ino`)
- Controla 5 servos por comunicación serial Bluetooth clásica a 9600 baudios
- Pines: Pulgar=3, Índice=5, Medio=6, Anular=9, Meñique=10

#### c) ESP32-CAM con MicroPython (`robotic_hand_micropython/esp32_cam_main.py`)
- Transmite video en tiempo real vía WiFi (streaming MJPEG)
- Recibe ángulos de servos mediante peticiones HTTP
- Puede operar en modo AP (crea su propia red `ESP32-CAM-Mano`) o como estación WiFi
- Pines (compatibles con OV2640): Pulgar=13, Índice=12, Medio=14, Anular=15, Meñique=2

### 2. Aplicación Android

**Lenguaje:** Kotlin + Jetpack Compose + Material 3
**Ubicación:** `app/src/main/java/com/example/manorobotica/`

| Archivo | Descripción |
|---|---|
| `MainActivity.kt` | Punto de entrada, permisos, navegación, reconocimiento de voz |
| `bluetooth/BluetoothController.kt` | Cliente BLE GATT, escaneo, conexión, envío de comandos |
| `camera/HandLandmarkProcessor.kt` | Convierte landmarks de MediaPipe a ángulos de servos (5 dedos) |
| `ui/screens/ControlScreen.kt` | Pantalla principal: controles, gestos, voz, consola serial |
| `ui/screens/CameraSignScreen.kt` | Cámara en vivo con detección de manos (cámara teléfono y ESP32-CAM) |
| `ui/screens/HandVisualizer.kt` | Visualización holográfica de la mano con los ángulos actuales |
| `ui/theme/Color.kt` | Paleta de colores estilo cyberpunk/neon |
| `ui/theme/Theme.kt` | Tema oscuro de Compose |

**Dependencias principales:**
- CameraX (preview + image analysis)
- MediaPipe Tasks Vision (hand landmark detection)
- Jetpack Compose + Material 3

### 3. Protocolo de Comunicación

```
Formato: S:<pulgar>,<indice>,<medio>,<anular>,<menique>\n
Ejemplo: S:180,90,0,45,120\n

Rango de ángulos: 0° a 180°
0°  = Dedo extendido (abierto)
180° = Dedo flexionado (cerrado)
```

**BLE:** Servicio NUS `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- Característica RX (escritura): `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- Característica TX (notificación): `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
- Tipo de escritura: `WRITE_TYPE_NO_RESPONSE` (fire-and-forget)

**HTTP (ESP32-CAM):**
- `GET /stream` → Stream MJPEG multipart/x-mixed-replace
- `GET /angle?p=90&i=90&m=90&a=90&me=90` → Establecer ángulos de servos

## Instalación y Configuración

### Firmware ESP32 (MicroPython)

```bash
# 1. Instalar MicroPython en el ESP32
# 2. Subir el firmware con ampy o Thonny
ampy -p COM3 put robotic_hand_micropython/main.py main.py

# Alternativa: usar el navegador web de MicroPython (WebREPL)
```

### Firmware ESP32-CAM (MicroPython)

```bash
# Requiere firmware MicroPython con soporte del módulo "camera"
# (ej. firmware Loboris, M5Camera, Koepel)
ampy -p COM3 put robotic_hand_micropython/esp32_cam_main.py main.py

# Configurar modo AP o estación editando las constantes en el archivo
# MODE_AP = True  → Crea red "ESP32-CAM-Mano"
# MODE_AP = False → Se conecta a WIFI_SSID/WIFI_PASSWORD
```

### Firmware Arduino

```
Abrir robotic_hand_arduino.ino en Arduino IDE
Seleccionar placa y puerto
Subir el sketch
Conectar módulo HC-05/HC-06 a los pines RX/TX del Arduino
```

### Aplicación Android

```bash
# Abrir en Android Studio
./gradlew assembleDebug
# o
./gradlew installDebug

# APK precompilado disponible en:
# app/build/outputs/apk/debug/Mano v2.apk
```

## Uso

### Pantalla Principal (ControlScreen)

1. **Bluetooth:** Escanea dispositivos, selecciona "ESP32-Mano" y conecta
2. **Voz:** Presiona el micrófono para traducir voz a señas
3. **Gestos:** Selecciona gestos predefinidos del LSM (Mano Abierta, Puño, Te Amo, OK, etc.)
4. **Cámara:** Abre la cámara para reconocimiento de manos en tiempo real
5. **Sliders:** Control manual de cada servo (0-180°)
6. **Consola Serial:** Muestra el historial de comandos enviados

### Pantalla de Cámara (CameraSignScreen)

**Modo Cámara del Teléfono:**
- Reconocimiento de manos en tiempo real usando MediaPipe
- Envío automático de ángulos por BLE cada 50ms
- Cambio entre cámara frontal/trasera
- Visualización de landmarks (puntos cyan) superpuestos

**Modo ESP32-CAM:**
1. Selecciona "ESP32-CAM" en el selector superior
2. Ingresa la dirección IP del ESP32-CAM (por defecto `192.168.4.1` en modo AP)
3. Presiona "Conectar"
4. El stream MJPEG se procesa con MediaPipe y los ángulos se envían por BLE

### Gestos del Lenguaje de Señas Mexicano (LSM)

| Gesto | Descripción |
|---|---|
| Mano Abierta | Todos los dedos extendidos (0°) |
| Puño | Todos los dedos flexionados (180°) |
| Señal A | Pulgar extendido, demás cerrados |
| Señal B | Todos extendidos, pulgar doblado |
| Señal C | Forma de C con la mano |
| Señal L | Índice y pulgar en L |
| Señal V | Índice y medio en V |
| Te Amo | Señal con pulgar, índice y meñique |
| OK | Círculo con pulgar e índice |

### Alfabeto LSM (voz a letras)

La app reconoce voz y traduce letra por letra usando ángulos predefinidos para cada carácter (a-z, incluyendo ñ).

## Estructura del Proyecto

```
ManoRobotica/
├── app/                          # Aplicación Android
│   ├── build/outputs/apk/        # APK compilado
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── hand_landmarker.task  # Modelo MediaPipe
│       └── java/com/example/manorobotica/
│           ├── MainActivity.kt
│           ├── bluetooth/
│           │   └── BluetoothController.kt
│           ├── camera/
│           │   └── HandLandmarkProcessor.kt
│           └── ui/
│               ├── screens/
│               │   ├── CameraSignScreen.kt
│               │   ├── ControlScreen.kt
│               │   └── HandVisualizer.kt
│               └── theme/
│                   ├── Color.kt
│                   └── Theme.kt
├── robotic_hand_arduino/
│   └── robotic_hand_arduino.ino   # Firmware Arduino (HC-05/06)
├── robotic_hand_micropython/
│   ├── main.py                    # Firmware ESP32 BLE
│   └── esp32_cam_main.py          # Firmware ESP32-CAM (MJPEG + WiFi)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Especificaciones Técnicas

| Parámetro | Valor |
|---|---|
| Servomotores | 5 (Pulgar, Índice, Medio, Anular, Meñique) |
| Rango angular | 0° - 180° |
| Frecuencia PWM | 50 Hz |
| Velocidad de interpolación | 0.15 (factor de suavizado) |
| Protocolo BLE | Nordic UART Service (NUS) |
| Resolución de cámara | 640x480 (VGA) |
| FPS de tracking | ~15-30 fps |
| Tasa de envío BLE | Cada 50 ms (20 updates/segundo) |

## Recursos

- **MediaPipe Hand Landmarker:** Modelo de detección de 21 landmarks de la mano
- **Nordic UART Service:** Protocolo estándar BLE para comunicación serial
- **Material 3:** Sistema de diseño de Android
- **Jetpack Compose:** Framework de UI declarativo para Android
