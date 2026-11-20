// Nucleo portable del firmware del dock Aidly (protocolo v1, ver Aidly/dock/PROTOCOLO.md).
// Sin dependencias de Arduino: el hardware entra por DockHw, asi se prueba en el PC (dock_core_test.cpp).
//
// Diferencias respecto a firmware-prueba/dock_prueba.ino (todas pensadas para cumplir PROTOCOLO.md y ClienteDock.kt):
//  - NO bloquea: GOTO/ROT/HOME responden "OK" al ACEPTAR la orden (la app espera el OK solo 1.5 s) y el avance se
//    consulta con STATUS (MOVING). STOP actua en el siguiente paso.
//  - Para solo si se pierde el USB; suelta las bobinas al terminar (calor, ruido en el microfono).
//  - HOME real con sensor Hall (centra la ventana del iman); sin Hall solo "va a 0" como antes.
//  - Argumentos validados: "GOTO abc" -> ERR ARG.  Extension: "ERR BUSY" si llega GOTO/ROT/HOME durante un HOME.
//  - Eventos EVT LIMIT (al llegar a +-180) y EVT PHONE 0|1 (si hay sensor); EVT BTN con button().
#pragma once
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct DockHw {
  virtual ~DockHw() {}
  virtual void coils(uint8_t mask) = 0;   // bit0..bit3 = IN1..IN4 del ULN2003; 0 = todas apagadas
  virtual bool hall() = 0;                // true si el iman de origen esta frente al sensor
  virtual bool phone() = 0;               // true si hay telefono en la pinza
  virtual bool usb() = 0;                 // true si el anfitrion USB esta conectado
  virtual void write(const char* line) = 0;  // una linea (el adaptador agrega '\n')
  virtual uint32_t micros() = 0;
};

class DockCore {
 public:
  static const int STEPS_MOTOR_REV = 2048;                  // 28BYJ-48, paso completo
  static const int STEPS_PLAT_REV = STEPS_MOTOR_REV * 3;    // reduccion 3:1 de la plataforma
  static const int MAX_DEG = 180;
  static const int MAX_STEPS = STEPS_PLAT_REV / 2;          // 3072 = 180 grados
  static const int LINE_MAX = 40;
  static const uint32_t HOLD_US = 150000;                   // tiempo con bobinas energizadas tras parar
  static const int HOME_SWEEP_FWD = STEPS_PLAT_REV * 200 / 360;
  static const int HOME_SWEEP_REV = STEPS_PLAT_REV * 400 / 360;
  static const int HOME_WINDOW_MAX = STEPS_PLAT_REV * 60 / 360;

  DockCore(DockHw& hw, bool hasHall, bool hasPhone) : hw_(hw), hasHall_(hasHall), hasPhone_(hasPhone) {
    phoneLast_ = hasPhone ? hw.phone() : false;
  }

  void feed(char c) {
    if (c == '\n') { process(); len_ = 0; overflow_ = false; }
    else if (c == '\r') {}
    else if (len_ < LINE_MAX) line_[len_++] = c;
    else overflow_ = true;
  }

  void button() { hw_.write("EVT BTN"); }

  void tick() {
    uint32_t now = hw_.micros();
    if (!hw_.usb() && mode_ != IDLE) halt(now);              // seguridad: sin USB no se mueve
    if (hasPhone_) {
      bool p = hw_.phone();
      if (p != phoneLast_) { phoneLast_ = p; hw_.write(p ? "EVT PHONE 1" : "EVT PHONE 0"); }
    }
    if (mode_ == IDLE) {
      if (coilsOn_ && (uint32_t)(now - idleSince_) >= HOLD_US) { hw_.coils(0); coilsOn_ = false; }
      return;
    }
    if ((uint32_t)(now - lastStep_) < intervalUs()) return;
    lastStep_ = now;
    stepOnce(now);
  }

  long steps() const { return pos_; }
  int degrees() const { return (int)lround(pos_ * 360.0 / STEPS_PLAT_REV); }
  bool moving() const { return mode_ != IDLE; }
  bool homed() const { return homed_; }
  uint32_t intervalUs() const { return (uint32_t)(60000000.0 / ((double)STEPS_MOTOR_REV * (2 + speed_))); }

 private:
  enum Mode { IDLE, GOTO, H_LEAVE, H_SEEK, H_CROSS, H_CENTER };

  DockHw& hw_;
  bool hasHall_, hasPhone_, phoneLast_ = false;
  char line_[LINE_MAX + 1];
  int len_ = 0;
  bool overflow_ = false;
  long pos_ = 0, target_ = 0;
  // SPEED por defecto = 10 (12 rpm de motor = 24 grados/s): la app supone hasta 30 grados/s y nunca manda SPEED;
  // con el 5 del prototipo (14 grados/s) la plataforma iria mucho mas lenta que su control simulado.
  int phase_ = 0, speed_ = 10, dir_ = 1;
  Mode mode_ = IDLE;
  bool coilsOn_ = false, homed_ = false;
  uint32_t lastStep_ = 0, idleSince_ = 0;
  long sweepLeft_ = 0, edge1_ = 0, travel_ = 0;
  bool sweptReverse_ = false;

  void reply(const char* s) { hw_.write(s); }

  static bool parseInt(const char* s, int& out) {
    if (!*s) return false;
    const char* p = s;
    if (*p == '-' || *p == '+') p++;
    if (!*p) return false;
    int digits = 0;
    for (; *p; p++) { if (*p < '0' || *p > '9' || ++digits > 5) return false; }
    out = atoi(s);
    return true;
  }

  // Si la linea es "NOMBRE" o "NOMBRE <arg>" devuelve el argumento ("" si falta); si no, nullptr ("GOTOX" no es GOTO).
  static const char* argOf(const char* s, const char* name) {
    size_t n = strlen(name);
    if (strncmp(s, name, n) != 0) return nullptr;
    if (s[n] == 0) return s + n;
    if (s[n] == ' ') { const char* a = s + n + 1; while (*a == ' ') a++; return a; }
    return nullptr;
  }

  static long degToSteps(int deg) { return lround(deg * (double)STEPS_PLAT_REV / 360.0); }

  void halt(uint32_t now) { mode_ = IDLE; target_ = pos_; idleSince_ = now; }

  void startGoto(long target) {
    target_ = target;
    mode_ = GOTO;
    lastStep_ = hw_.micros();
  }

  void finish(uint32_t now) {
    mode_ = IDLE;
    idleSince_ = now;
    if (labs(pos_) >= MAX_STEPS) reply("EVT LIMIT");
  }

  void doStep(int dir) {
    phase_ = (phase_ + dir) & 3;
    static const uint8_t PAT[4] = {0x3, 0x6, 0xC, 0x9};   // IN1+IN2, IN2+IN3, IN3+IN4, IN4+IN1
    hw_.coils(PAT[phase_]);
    coilsOn_ = true;
    pos_ += dir;
  }

  void startHome() {
    if (!hasHall_) { startGoto(0); homed_ = false; return; }  // sin Hall: solo "va a 0" (posicion supuesta)
    homed_ = false;
    sweptReverse_ = false;
    sweepLeft_ = HOME_SWEEP_FWD;
    travel_ = 0;
    if (hw_.hall()) { dir_ = 1; mode_ = H_LEAVE; }          // dentro de la ventana: salir y volver a entrar
    else { dir_ = 1; mode_ = H_SEEK; }
    lastStep_ = hw_.micros();
  }

  void homeFail(uint32_t now) { mode_ = IDLE; idleSince_ = now; reply("EVT LIMIT"); }

  void stepOnce(uint32_t now) {
    switch (mode_) {
      case GOTO: {
        long d = target_ - pos_;
        if (d == 0) { finish(now); return; }
        doStep(d > 0 ? 1 : -1);
        if (pos_ == target_) finish(now);
        return;
      }
      case H_CENTER: {
        long d = target_ - pos_;
        if (d == 0) { pos_ = 0; target_ = 0; homed_ = true; mode_ = IDLE; idleSince_ = now; return; }
        doStep(d > 0 ? 1 : -1);
        if (pos_ == target_) { pos_ = 0; target_ = 0; homed_ = true; mode_ = IDLE; idleSince_ = now; }
        return;
      }
      case H_LEAVE:
        doStep(dir_);
        if (++travel_ > HOME_WINDOW_MAX) { homeFail(now); return; }
        if (!hw_.hall()) { dir_ = -1; mode_ = H_SEEK; sweepLeft_ = HOME_SWEEP_REV; travel_ = 0; sweptReverse_ = true; }
        return;
      case H_SEEK:
        doStep(dir_);
        if (hw_.hall()) { edge1_ = pos_; mode_ = H_CROSS; travel_ = 0; return; }
        if (--sweepLeft_ <= 0) {
          if (!sweptReverse_) { sweptReverse_ = true; dir_ = -1; sweepLeft_ = HOME_SWEEP_REV; }
          else homeFail(now);
        }
        return;
      case H_CROSS:
        doStep(dir_);
        if (++travel_ > HOME_WINDOW_MAX) { homeFail(now); return; }
        if (!hw_.hall()) { target_ = (edge1_ + pos_) / 2; mode_ = H_CENTER; }
        return;
      default: return;
    }
  }

  void process() {
    line_[len_] = 0;
    char* s = line_;
    while (*s == ' ') s++;
    for (int i = (int)strlen(s) - 1; i >= 0 && s[i] == ' '; i--) s[i] = 0;
    if (overflow_) { reply("ERR CMD"); return; }

    char buf[48];
    int v = 0;
    uint32_t now = hw_.micros();
    bool homing = (mode_ == H_LEAVE || mode_ == H_SEEK || mode_ == H_CROSS || mode_ == H_CENTER);

    if (!strcmp(s, "HELLO")) reply("AIDLY-DOCK 1 ROT=180");
    else if (!strcmp(s, "STOP")) { halt(now); reply("OK"); }
    else if (!strcmp(s, "STATUS")) {
      snprintf(buf, sizeof buf, "POS %d MOVING %d LIM %d", degrees(), mode_ != IDLE ? 1 : 0, labs(pos_) >= MAX_STEPS ? 1 : 0);
      reply(buf);
    } else if (!strcmp(s, "HOME")) {
      if (homing) reply("ERR BUSY"); else { startHome(); reply("OK"); }
    } else if (const char* a = argOf(s, "GOTO")) {
      if (homing) reply("ERR BUSY");
      else if (!parseInt(a, v)) reply("ERR ARG");
      else if (v < -MAX_DEG || v > MAX_DEG) reply("ERR RANGE");
      else { startGoto(degToSteps(v)); reply("OK"); }
    } else if (const char* b = argOf(s, "ROT")) {
      if (homing) reply("ERR BUSY");
      else if (!parseInt(b, v)) reply("ERR ARG");
      else {
        long t = pos_ + degToSteps(v);
        if (labs(t) > MAX_STEPS) reply("ERR RANGE"); else { startGoto(t); reply("OK"); }
      }
    } else if (const char* d = argOf(s, "SPEED")) {
      if (!parseInt(d, v) || v < 1 || v > 10) reply("ERR ARG"); else { speed_ = v; reply("OK"); }
    } else reply("ERR CMD");
  }
};
