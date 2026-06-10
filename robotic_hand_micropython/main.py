# =============================================================================
# PROYECTO: Control de Mano Robótica por Bluetooth Low Energy (BLE) con ESP32 (MicroPython)
# DESCRIPCIÓN: Recibe tramas de ángulos ("S:P,I,M,A,M\n") a través de BLE (NUS)
#              y controla 5 servomotores usando PWM con transiciones ultra suaves.
# =============================================================================

import machine
import time
import math
import bluetooth

# --- CONFIGURACIÓN DE PINES PARA SERVOS (ESP32) ---
PIN_PULGAR = 13
PIN_INDICE = 12
PIN_MEDIO  = 14
PIN_ANULAR = 27
PIN_MENIQUE = 26

# --- CLASE PARA CONTROL DE SERVO DE ALTA PRECISIÓN ---
class ServoSmooth:
    def __init__(self, pin, min_us=500, max_us=2500):
        # Configurar PWM a 50Hz (frecuencia estándar para servos analógicos y digitales)
        self.pwm = machine.PWM(machine.Pin(pin), freq=50)
        self.min_us = min_us
        self.max_us = max_us
        self.pos_actual = 0.0
        self.pos_destino = 0.0
        
        # Mover inicialmente a 0 grados
        self.write_angle(0)
        self.pos_actual = 0.0
        
    def write_angle(self, angle):
        # Limitar ángulo por seguridad mecánica
        angle = max(0.0, min(180.0, angle))
        
        # Convertir ángulo (0-180) a ciclo de trabajo de microsegundos (500us - 2500us)
        us = self.min_us + (angle / 180.0) * (self.max_us - self.min_us)
        
        # En MicroPython del ESP32, el ciclo de trabajo de PWM es de 10 bits (0 a 1023)
        # 50Hz equivale a un período de 20ms (20000us)
        # Ciclo de trabajo de 10 bits = (us / 20000) * 1023
        duty = int((us / 20000.0) * 1023)
        self.pwm.duty(duty)

    def set_target(self, angle):
        self.pos_destino = max(0.0, min(180.0, angle))

    def update(self, speed=0.15):
        # Interpolación lineal suave (smooth sweep)
        if abs(self.pos_actual - self.pos_destino) > 0.5:
            self.pos_actual += (self.pos_destino - self.pos_actual) * speed
            self.write_angle(self.pos_actual)
            return True
        else:
            self.pos_actual = self.pos_destino
            return False

# Inicializar los 5 servos en el ESP32
servos = [
    ServoSmooth(PIN_PULGAR),   # Servos[0]
    ServoSmooth(PIN_INDICE),   # Servos[1]
    ServoSmooth(PIN_MEDIO),    # Servos[2]
    ServoSmooth(PIN_ANULAR),   # Servos[3]
    ServoSmooth(PIN_MENIQUE)   # Servos[4]
]

# --- CONTROLADOR BLE NORDIC UART SERVICE (NUS) ---
class BLEUART:
    def __init__(self, name="ESP32-Mano"):
        self._ble = bluetooth.BLE()
        self._ble.active(True)
        self._ble.irq(self._irq)
        
        # Registrar servicio de UART Nórdico (NUS)
        # Service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
        # RX (Write without response): 6E400002-B5A3-F393-E0A9-E50E24DCCA9E
        # TX (Notify): 6E400003-B5A3-F393-E0A9-E50E24DCCA9E
        
        NUS_UUID = bluetooth.UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        RX_UUID = bluetooth.UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        TX_UUID = bluetooth.UUID("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        
        TX_CHAR = (TX_UUID, bluetooth.FLAG_NOTIFY)
        RX_CHAR = (RX_UUID, bluetooth.FLAG_WRITE | bluetooth.FLAG_WRITE_NO_RESPONSE)
        
        NUS_SERVICE = (NUS_UUID, (TX_CHAR, RX_CHAR))
        
        ((self._tx_handle, self._rx_handle),) = self._ble.gatts_register_services((NUS_SERVICE,))
        
        self._connections = set()
        self._write_callback = None
        self._name = name
        self.advertise()

    def _irq(self, event, data):
        # Eventos del stack Bluetooth
        if event == 1:  # _IRQ_CENTRAL_CONNECT
            conn_handle, _, _ = data
            self._connections.add(conn_handle)
            print("BLE: Dispositivo móvil conectado!")
        elif event == 2:  # _IRQ_CENTRAL_DISCONNECT
            conn_handle, _, _ = data
            if conn_handle in self._connections:
                self._connections.remove(conn_handle)
            print("BLE: Dispositivo móvil desconectado.")
            self.advertise()
        elif event == 3:  # _IRQ_GATTS_WRITE
            conn_handle, value_handle = data
            if conn_handle in self._connections and value_handle == self._rx_handle:
                # Leer bytes escritos por la app
                data_bytes = self._ble.gatts_read(self._rx_handle)
                if self._write_callback:
                    self._write_callback(data_bytes)

    def on_write(self, callback):
        self._write_callback = callback

    def send(self, data):
        data_bytes = data.encode("utf-8") if isinstance(data, str) else data
        for conn_handle in self._connections:
            self._ble.gatts_write(self._tx_handle, data_bytes)
            self._ble.gatts_notify(conn_handle, self._tx_handle)

    def advertise(self):
        # El límite de tamaño de un paquete de anuncio BLE primario es de 31 bytes.
        # Al concatenar Flags, Nombre y el UUID de 128-bits excedemos el límite (33 bytes), lo que causa el error -18 en NimBLE.
        # Solución: Dividir los datos en un paquete primario (adv_data) y un paquete de respuesta de escaneo (resp_data).
        
        # Paquete Primario: Flags + Nombre del dispositivo "ESP32-Mano" (15 bytes)
        adv_data = b"\x02\x01\x06\x0b\x09ESP32-Mano"
        
        # Respuesta de Escaneo: Lista de UUIDs de 128 bits con el servicio NUS (18 bytes)
        resp_data = b"\x11\x07\x9e\xca\xdc\x24\x0e\xe5\xa9\xe0\xf3\x93\xb5\xa3\x01\x00\x40\x6e"
        
        try:
            self._ble.gap_advertise(100, adv_data, resp_data=resp_data)
            print("BLE: Anunciando dispositivo como 'ESP32-Mano'...")
        except Exception as e:
            print("Error al iniciar anuncios BLE:", e)

# Inicializar BLE
ble_uart = BLEUART()

buffer = ""

# Callback cuando llegan bytes a la característica RX
def rx_callback(data_bytes):
    global buffer
    try:
        text = data_bytes.decode('utf-8')
        buffer += text
    except Exception as e:
        print("Error al decodificar RX:", e)

# Registrar callback de escritura
ble_uart.on_write(rx_callback)

print("--- ESP32 MANO ROBOTICA CON BLE (MICROPYTHON) INICIALIZADA ---")
print("Anunciando canal BLE para la App móvil...")

while True:
    # 1. Verificar si hay tramas completas en el buffer acumulado
    if "\n" in buffer:
        partes = buffer.split("\n")
        # El último fragmento puede ser una trama incompleta, la dejamos en el buffer
        buffer = partes[-1]
        
        # Procesar todas las tramas completas precedentes
        for trama in partes[:-1]:
            trama = trama.strip()
            if trama.startswith("S:"):
                try:
                    valores_str = trama[2:].split(',')
                    if len(valores_str) == 5:
                        # Extraer y asignar ángulos de destino para cada servo
                        for i in range(5):
                            angulo = float(valores_str[i])
                            servos[i].set_target(angulo)
                        print("Comando BLE -> Pulgar: {:.1f}, Índice: {:.1f}, Medio: {:.1f}, Anular: {:.1f}, Meñique: {:.1f}".format(
                            servos[0].pos_destino, servos[1].pos_destino, servos[2].pos_destino, servos[3].pos_destino, servos[4].pos_destino
                        ))
                except Exception as e:
                    print("Error al parsear trama BLE:", e)

    # 2. Actualizar las posiciones físicas de los servos de manera suave
    hubo_cambio = False
    for servo in servos:
        if servo.update(speed=0.15):
            hubo_cambio = True

    # 3. Pequeño retardo de ciclo (~20ms) para una tasa de actualización óptima de 50Hz
    time.sleep_ms(20)
