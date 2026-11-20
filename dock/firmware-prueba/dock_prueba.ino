// Firmware de PRUEBA del dock Aidly (protocolo v1, ver ../PROTOCOLO.md). SIN PROBAR con hardware.
// Placas con USB nativo (CDC): ESP32-S3 ("USB CDC On Boot" activado), RP2040, Arduino Leonardo/Pro Micro.
// Mueve un motor 28BYJ-48 con ULN2003 (pines IN1..IN4 abajo) y un LED indica comandos. Si no hay motor, solo responde.
#include <Stepper.h>

const int PASOS_VUELTA = 2048;                 // 28BYJ-48 en modo completo
const int PIN_IN1 = 16, PIN_IN2 = 18, PIN_IN3 = 17, PIN_IN4 = 19;   // cambia segun tu placa
const int PIN_LED = LED_BUILTIN;
const int PIN_BOTON = 21;                      // opcional: boton a GND
const float RELACION = 3.0;                    // reduccion 3:1 de la plataforma
Stepper motor(PASOS_VUELTA, PIN_IN1, PIN_IN3, PIN_IN2, PIN_IN4);

float posicion = 0;                            // grados de la plataforma
int velocidad = 5;
bool botonAntes = true;
String linea;

long pasosPara(float grados) { return lround(grados / 360.0 * PASOS_VUELTA * RELACION); }

void irA(float objetivo) {
  float delta = objetivo - posicion;
  motor.setSpeed(2 + velocidad);               // rpm del motor
  motor.step(pasosPara(delta));
  posicion = objetivo;
}

void responder(const String& s) { Serial.println(s); }

void atender(String c) {
  c.trim();
  digitalWrite(PIN_LED, !digitalRead(PIN_LED));
  if (c == "HELLO") responder("AIDLY-DOCK 1 ROT=180");
  else if (c == "HOME") { irA(0); responder("OK"); }
  else if (c.startsWith("GOTO ")) {
    int g = c.substring(5).toInt();
    if (g < -180 || g > 180) responder("ERR RANGE"); else { irA(g); responder("OK"); }
  } else if (c.startsWith("ROT ")) {
    float o = posicion + c.substring(4).toInt();
    if (o < -180 || o > 180) responder("ERR RANGE"); else { irA(o); responder("OK"); }
  } else if (c.startsWith("SPEED ")) {
    int v = c.substring(6).toInt();
    if (v < 1 || v > 10) responder("ERR ARG"); else { velocidad = v; responder("OK"); }
  } else if (c == "STOP") responder("OK");
  else if (c == "STATUS") responder("POS " + String((int)lround(posicion)) + " MOVING 0 LIM 0");
  else responder("ERR CMD");
}

void setup() {
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BOTON, INPUT_PULLUP);
  Serial.begin(115200);
}

void loop() {
  while (Serial.available()) {
    char ch = Serial.read();
    if (ch == '\n') { atender(linea); linea = ""; }
    else if (ch != '\r' && linea.length() < 40) linea += ch;
  }
  bool boton = digitalRead(PIN_BOTON);
  if (!boton && botonAntes) { responder("EVT BTN"); delay(200); }
  botonAntes = boton;
}
