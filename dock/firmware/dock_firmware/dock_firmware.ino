// Adaptador Arduino del nucleo dock_core.h (protocolo v1). NO COMPILADO NI PROBADO EN PLACA:
// no hay Arduino CLI en esta maquina; solo el nucleo se probo en PC (../dock_core_test.cpp).
// Copia dock_core.h junto a este .ino (Arduino exige la cabecera en la carpeta del sketch).
// Placas con USB nativo (CDC): ESP32-S3 ("USB CDC On Boot" activado), RP2040, Leonardo / Pro Micro.
#include "dock_core.h"

const int PIN_IN[4] = {16, 18, 17, 19};   // IN1..IN4 del ULN2003 (mismos pines que firmware-prueba/dock_prueba.ino)
const int PIN_BOTON = 21;                  // boton a GND (opcional)
const int PIN_HALL = -1;                   // -1 = sin sensor: HOME solo "va a 0" con la posicion supuesta
const bool HALL_ACTIVO_BAJO = true;        // la mayoria de sensores Hall digitales bajan con el iman
const int PIN_TELEFONO = -1;               // -1 = sin sensor de presencia (microinterruptor a GND)

struct PlacaHw : DockHw {
  void coils(uint8_t m) override { for (int i = 0; i < 4; i++) digitalWrite(PIN_IN[i], (m >> i) & 1); }
  bool hall() override { return PIN_HALL >= 0 && (digitalRead(PIN_HALL) == (HALL_ACTIVO_BAJO ? LOW : HIGH)); }
  bool phone() override { return PIN_TELEFONO >= 0 && digitalRead(PIN_TELEFONO) == LOW; }
  // Con USB nativo, "Serial" es verdadero mientras el anfitrion tiene el puerto abierto (DTR). Sin verificar en placa.
  bool usb() override { return (bool)Serial; }
  void write(const char* l) override { Serial.println(l); }   // println agrega \r\n: el protocolo y la app lo aceptan
  uint32_t micros() override { return ::micros(); }
};

PlacaHw placa;
DockCore dock(placa, PIN_HALL >= 0, PIN_TELEFONO >= 0);
bool botonAntes = true;
uint32_t botonMs = 0;

void setup() {
  for (int i = 0; i < 4; i++) { pinMode(PIN_IN[i], OUTPUT); digitalWrite(PIN_IN[i], LOW); }
  pinMode(PIN_BOTON, INPUT_PULLUP);
  if (PIN_HALL >= 0) pinMode(PIN_HALL, INPUT_PULLUP);   // en GPIO34/39 del ESP32 (solo entrada) usa una resistencia EXTERNA
  if (PIN_TELEFONO >= 0) pinMode(PIN_TELEFONO, INPUT_PULLUP);
  Serial.begin(115200);
}

void loop() {
  while (Serial.available()) dock.feed((char)Serial.read());
  dock.tick();
  bool boton = digitalRead(PIN_BOTON);                    // antirrebote por tiempo: sin delay(), que bloquearia el motor
  if (!boton && botonAntes && millis() - botonMs > 200) { dock.button(); botonMs = millis(); }
  botonAntes = boton;
}
