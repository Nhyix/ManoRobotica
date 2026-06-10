# =============================================================================
# PROYECTO: ESP32-CAM Mano Robotica - Streaming MJPEG + Control de Servos
# DESCRIPCION: Transmite video en tiempo real via WiFi (MJPEG) y permite
#              controlar 5 servomotores mediante peticiones HTTP.
#
# REQUISITOS: MicroPython con modulo "camera" habilitado (ej. firmware
#             Loboris/Koepel, M5Camera, o ESP32-CAM con camera support).
#             Se recomienda ESP32-CAM con PSRAM.
# =============================================================================

import camera
import network
import socket
import machine
import time
import gc

# --- CONFIGURACION DE PINES PARA SERVOS (ESP32-CAM) ---
# Pines que NO entran en conflicto con la camara OV2640 en ESP32-CAM:
#   GPIO13, 12, 14, 15, 2, 4, 1, 3
PIN_PULGAR = 13
PIN_INDICE = 12
PIN_MEDIO = 14
PIN_ANULAR = 15
PIN_MENIQUE = 2

# --- CLASE PARA CONTROL DE SERVO CON TRANSICION SUAVE ---
class ServoSmooth:
    def __init__(self, pin, min_us=500, max_us=2500):
        self.pwm = machine.PWM(machine.Pin(pin), freq=50)
        self.min_us = min_us
        self.max_us = max_us
        self.pos_actual = 0.0
        self.pos_destino = 0.0
        self.write_angle(0)
        self.pos_actual = 0.0

    def write_angle(self, angle):
        angle = max(0.0, min(180.0, angle))
        us = self.min_us + (angle / 180.0) * (self.max_us - self.min_us)
        duty = int((us / 20000.0) * 1023)
        self.pwm.duty(duty)

    def set_target(self, angle):
        self.pos_destino = max(0.0, min(180.0, angle))

    def update(self, speed=0.15):
        if abs(self.pos_actual - self.pos_destino) > 0.5:
            self.pos_actual += (self.pos_destino - self.pos_actual) * speed
            self.write_angle(self.pos_actual)
            return True
        else:
            self.pos_actual = self.pos_destino
            return False

# Inicializar los 5 servos
servos = [
    ServoSmooth(PIN_PULGAR),
    ServoSmooth(PIN_INDICE),
    ServoSmooth(PIN_MEDIO),
    ServoSmooth(PIN_ANULAR),
    ServoSmooth(PIN_MENIQUE)
]

# --- CONFIGURACION WIFI ---
# Modo AP (punto de acceso): la ESP32-CAM crea su propia red
# Establece MODE_AP = False para conectarte a una red WiFi existente
MODE_AP = True

AP_SSID = "ESP32-CAM-Mano"
AP_PASSWORD = "12345678"

WIFI_SSID = "ROBOT_WIFI"
WIFI_PASSWORD = "12345678"

if MODE_AP:
    ap = network.WLAN(network.AP_IF)
    ap.active(True)
    ap.config(essid=AP_SSID, password=AP_PASSWORD)
    while not ap.active():
        time.sleep(0.1)
    ip = ap.ifconfig()[0]
    print("Modo AP activo. SSID: \"{}\"".format(AP_SSID))
else:
    sta = network.WLAN(network.STA_IF)
    sta.active(True)
    sta.connect(WIFI_SSID, WIFI_PASSWORD)
    intentos = 0
    while not sta.isconnected():
        time.sleep(0.5)
        intentos += 1
        if intentos > 20:
            print("ERROR: No se pudo conectar a WiFi")
            raise RuntimeError("WiFi connection failed")
    ip = sta.ifconfig()[0]
    print("Conectado a WiFi como estacion. IP:", ip)

print("Direccion IP: http://{}/".format(ip))

# --- INICIALIZACION DE CAMARA ---
try:
    camera.init(0, format=camera.JPEG, fb_location=camera.PSRAM)
    # Resoluciones disponibles: FRAME_QQVGA, FRAME_QVGA, FRAME_VGA (640x480),
    #                           FRAME_SVGA (800x600), FRAME_HD (1280x720)
    camera.framesize(camera.FRAME_VGA)
    # Calidad de compresion JPEG: 0 (mejor) a 63 (peor)
    camera.quality(10)
    camera.speffect(camera.EFFECT_NONE)
    # Invertir espejo/rotacion si es necesario:
    # camera.flip(1)   # Voltear vertical
    # camera.mirror(1) # Voltear horizontal
    print("Camara inicializada (VGA, calidad 10)")
except Exception as e:
    print("ERROR al inicializar camara:", e)
    raise

# --- SERVIDOR HTTP ---
BOUNDARY = "frame_boundary"

addr = socket.getaddrinfo('0.0.0.0', 80)[0][-1]
server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(addr)
server.listen(2)
print("Servidor HTTP listo en http://{}:80".format(ip))


def parse_query(path):
    params = {}
    if '?' in path:
        qs = path.split('?', 1)[1]
        for part in qs.split('&'):
            if '=' in part:
                k, v = part.split('=', 1)
                try:
                    params[k] = float(v)
                except:
                    params[k] = v
    return params


def lee_linea(sock):
    """Lee una linea (hasta \\n) del socket sin bloquearse mas de lo necesario."""
    buf = b""
    while True:
        c = sock.read(1)
        if not c:
            break
        if c == b'\n':
            break
        if c != b'\r':
            buf += c
    return buf.decode('utf-8', errors='ignore') if buf else ""


def maneja_cliente(client):
    try:
        client.settimeout(3.0)

        request = b""
        while b"\r\n\r\n" not in request:
            chunk = client.recv(1024)
            if not chunk:
                break
            request += chunk
            if len(request) > 4096:
                break

        if not request:
            client.close()
            return

        linea0 = request.split(b"\r\n")[0].decode('utf-8', errors='ignore')
        partes = linea0.split(' ')
        if len(partes) < 2:
            client.close()
            return

        method = partes[0]
        path = partes[1]

        # --- ENDPOINT: /angle?p=X&i=X&m=X&a=X&me=X ---
        if path.startswith('/angle'):
            params = parse_query(path)
            if 'p' in params:
                servos[0].set_target(params['p'])
            if 'i' in params:
                servos[1].set_target(params['i'])
            if 'm' in params:
                servos[2].set_target(params['m'])
            if 'a' in params:
                servos[3].set_target(params['a'])
            if 'me' in params:
                servos[4].set_target(params['me'])
            resp = b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Type: text/plain\r\n\r\nOK"
            client.sendall(resp)
            client.close()

        # --- ENDPOINT: /stream (MJPEG) ---
        elif path.startswith('/stream'):
            resp = (
                b"HTTP/1.1 200 OK\r\n"
                b"Content-Type: multipart/x-mixed-replace; boundary=" + BOUNDARY.encode() + b"\r\n"
                b"Access-Control-Allow-Origin: *\r\n"
                b"Cache-Control: no-cache\r\n"
                b"Connection: close\r\n"
                b"\r\n"
            )
            try:
                client.sendall(resp)
                while True:
                    frame = camera.capture()
                    if frame is None:
                        time.sleep_ms(10)
                        continue

                    # Verificar si el cliente aun esta conectado
                    try:
                        client.settimeout(0)
                        peek = client.read(1)
                        if peek is None or peek == b'':
                            break
                        client.settimeout(3.0)
                    except:
                        pass

                    header = (
                        b"--" + BOUNDARY.encode() + b"\r\n"
                        b"Content-Type: image/jpeg\r\n"
                        b"Content-Length: " + str(len(frame)).encode() + b"\r\n"
                        b"\r\n"
                    )
                    try:
                        client.sendall(header + frame)
                    except:
                        break

                    # Actualizar servos entre frames
                    for s in servos:
                        s.update()

                    gc.collect()
            except:
                pass
            client.close()

        # --- ENDPOINT: / (pantalla de estado) ---
        else:
            angs = [s.pos_destino for s in servos]
            html = (
                "<!DOCTYPE html>"
                "<html><head><meta charset='UTF-8'>"
                "<title>ESP32-CAM Mano Robotica</title>"
                "<style>"
                "body{font-family:sans-serif;background:#111;color:#0ff;margin:40px;text-align:center}"
                "h1{color:#0ff;text-shadow:0 0 10px #0ff}"
                "a{color:#f0f;font-size:1.2em}"
                ".val{font-size:1.5em;margin:10px}"
                "</style></head><body>"
                "<h1>ESP32-CAM Mano Robotica</h1>"
                "<p>Angulos actuales:</p>"
                "<div class='val'>Pulgar: {:.0f}&deg;</div>"
                "<div class='val'>Indice: {:.0f}&deg;</div>"
                "<div class='val'>Medio: {:.0f}&deg;</div>"
                "<div class='val'>Anular: {:.0f}&deg;</div>"
                "<div class='val'>Menique: {:.0f}&deg;</div>"
                "<br><a href='/stream'>Ver Stream MJPEG</a>"
                "</body></html>"
            ).format(*angs)
            resp = (
                b"HTTP/1.1 200 OK\r\n"
                b"Content-Type: text/html; charset=UTF-8\r\n"
                b"Content-Length: " + str(len(html)).encode() + b"\r\n"
                b"\r\n" + html.encode()
            )
            client.sendall(resp)
            client.close()

    except Exception as e:
        print("Error en cliente:", e)
    finally:
        try:
            client.close()
        except:
            pass


# --- BUCLE PRINCIPAL ---
print("--- ESP32-CAM MANO ROBOTICA INICIALIZADA ---")
print("Stream MJPEG: http://{}/stream".format(ip))
print("Control servos: http://{}/angle?p=90&i=90&m=90&a=90&me=90".format(ip))

while True:
    try:
        client, addr = server.accept()
        maneja_cliente(client)
    except OSError:
        pass

    # Actualizar servos aunque no haya clientes conectados
    for s in servos:
        s.update()

    time.sleep_ms(5)
