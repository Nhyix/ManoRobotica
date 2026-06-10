/*
  =============================================================================
  PROYECTO: Control de Mano Robótica por Bluetooth (Lenguaje de Señas)
  AUTOR: Antigravity - Google DeepMind Team
  DESCRIPCIÓN: Código para Arduino que recibe tramas seriales vía Bluetooth
               (ej. desde módulo HC-05/HC-06) en formato: "S:P,I,M,A,M\n"
               y mueve 5 servomotores con transiciones suaves integradas.
  =============================================================================
*/

#include <Servo.h>

// Definición de pines para los 5 servomotores (Dedos)
#define PIN_PULGAR   3
#define PIN_INDICE   5
#define PIN_MEDIO    6
#define PIN_ANULAR   9
#define PIN_MENIQUE  10

// Servos
Servo servoPulgar;
Servo servoIndice;
Servo servoMedio;
Servo servoAnular;
Servo servoMenique;

// Posiciones actuales e ideales de los servos para transiciones suaves
float posActual[5] = {0, 0, 0, 0, 0}; // Posición física actual de cada servo
float posDestino[5] = {0, 0, 0, 0, 0}; // Posición deseada enviada por la App

// Constante de velocidad para suavizar el movimiento (0.01 = Muy lento, 1.0 = Instantáneo)
const float VELOCIDAD_SUAVE = 0.15; 

String inputBuffer = ""; // Buffer para almacenar la cadena serial entrante

void setup() {
  // Inicialización de la comunicación serial (Por defecto a 9600 baudios, estándar para HC-05/HC-06)
  Serial.begin(9600);
  
  // Conectar los servos a sus respectivos pines
  servoPulgar.attach(PIN_PULGAR);
  servoIndice.attach(PIN_INDICE);
  servoMedio.attach(PIN_MEDIO);
  servoAnular.attach(PIN_ANULAR);
  servoMenique.attach(PIN_MENIQUE);

  // Inicializar todos los servos en posición extendida (0 grados)
  for (int i = 0; i < 5; i++) {
    posActual[i] = 0;
    posDestino[i] = 0;
  }
  actualizarServosFisicos();

  Serial.println("--- MANO ROBOTICA INICIALIZADA (9600 Bps) ---");
}

void loop() {
  // 1. Leer datos de Bluetooth Serial
  while (Serial.available() > 0) {
    char c = Serial.read();
    
    if (c == '\n') {
      // Fin de línea detectado: Procesar el comando recibido
      procesarComando(inputBuffer);
      inputBuffer = ""; // Limpiar buffer para el próximo comando
    } else if (c != '\r') {
      // Ignorar retornos de carro y concatenar el caracter al buffer
      inputBuffer += c;
    }
  }

  // 2. Realizar transiciones suaves (Interpolación lineal)
  bool huboCambio = false;
  for (int i = 0; i < 5; i++) {
    if (abs(posActual[i] - posDestino[i]) > 0.5) {
      // Nos movemos un porcentaje de la distancia que falta en cada ciclo (Smooth Sweep)
      posActual[i] += (posDestino[i] - posActual[i]) * VELOCIDAD_SUAVE;
      huboCambio = true;
    } else {
      posActual[i] = posDestino[i]; // Fijar exactamente en el destino final
    }
  }

  // 3. Escribir los valores actualizados en los servomotores
  if (huboCambio) {
    actualizarServosFisicos();
    delay(15); // Pequeña pausa para regular la velocidad de las interpolaciones
  }
}

// Procesa comandos tipo "S:P,I,M,A,M" (ej: "S:180,90,0,45,120")
void procesarComando(String cmd) {
  cmd.trim();
  if (cmd.startsWith("S:")) {
    // Extraer los valores numéricos omitiendo la cabecera "S:"
    String valores = cmd.substring(2);
    
    int indexComa1 = valores.indexOf(',');
    int indexComa2 = valores.indexOf(',', indexComa1 + 1);
    int indexComa3 = valores.indexOf(',', indexComa2 + 1);
    int indexComa4 = valores.indexOf(',', indexComa3 + 1);

    if (indexComa1 != -1 && indexComa2 != -1 && indexComa3 != -1 && indexComa4 != -1) {
      // Parsear cada posición individualmente
      float pulgar = valores.substring(0, indexComa1).toFloat();
      float indice = valores.substring(indexComa1 + 1, indexComa2).toFloat();
      float medio = valores.substring(indexComa2 + 1, indexComa3).toFloat();
      float anular = valores.substring(indexComa3 + 1, indexComa4).toFloat();
      float menique = valores.substring(indexComa4 + 1).toFloat();

      // Limitar valores de seguridad entre 0° y 180°
      posDestino[0] = constrain(pulgar, 0, 180);
      posDestino[1] = constrain(indice, 0, 180);
      posDestino[2] = constrain(medio, 0, 180);
      posDestino[3] = constrain(anular, 0, 180);
      posDestino[4] = constrain(menique, 0, 180);

      // Confirmación serial de regreso al monitor
      Serial.print("Ángulos recibidos -> P:");
      Serial.print(posDestino[0]);
      Serial.print(" I:");
      Serial.print(posDestino[1]);
      Serial.print(" M:");
      Serial.print(posDestino[2]);
      Serial.print(" A:");
      Serial.print(posDestino[3]);
      Serial.print(" m:");
      Serial.println(posDestino[4]);
    } else {
      Serial.println("Error: Formato de comando incorrecto. Use 'S:P,I,M,A,M\\n'");
    }
  }
}

// Aplica el valor actual acumulado a cada servo individual
void actualizarServosFisicos() {
  servoPulgar.write((int)posActual[0]);
  servoIndice.write((int)posActual[1]);
  servoMedio.write((int)posActual[2]);
  servoAnular.write((int)posActual[3]);
  servoMenique.write((int)posActual[4]);
}
