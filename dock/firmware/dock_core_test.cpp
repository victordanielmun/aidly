// Pruebas en PC del nucleo del dock (sin hardware): reloj y plataforma simulados.
// Compilar (MSVC):  cl /std:c++17 /EHsc /W4 dock_core_test.cpp   y ejecutar dock_core_test.exe
#include <algorithm>
#include <string>
#include <vector>

#include "dock_core.h"

struct FakeHw : DockHw {
  uint32_t now = 1000;
  uint8_t mask = 0;
  int lastIdx = 0;                 // fase en la que esta el rotor; se CONSERVA al soltar las bobinas (detente del motor real)
  long actual = 0;                 // pasos reales de la plataforma (puede diferir de lo que cree el firmware)
  long origin = 0;                 // paso fisico del iman de origen
  long windowHalf = 51;            // ~3 grados
  bool hasMagnet = true, usbOn = true, phoneOn = false;
  long coilWrites = 0, offWrites = 0;
  std::vector<std::string> out;
  std::vector<int> idxTrace;

  void coils(uint8_t m) override {
    coilWrites++;
    if (m == 0) { offWrites++; mask = 0; return; }
    static const uint8_t PAT[4] = {0x3, 0x6, 0xC, 0x9};
    int idx = -1;
    for (int i = 0; i < 4; i++) if (PAT[i] == m) idx = i;
    if (idx < 0) { out.push_back("!!patron invalido"); }
    else if (lastIdx >= 0) {
      int diff = (idx - lastIdx + 4) & 3;
      if (diff == 1) actual++; else if (diff == 3) actual--; else out.push_back("!!salto de fase");
    }
    lastIdx = idx; mask = m; idxTrace.push_back(idx);
  }
  bool hall() override { return hasMagnet && labs(actual - origin) <= windowHalf; }
  bool phone() override { return phoneOn; }
  bool usb() override { return usbOn; }
  void write(const char* l) override { out.push_back(l); }
  uint32_t micros() override { return now; }
};

static int fails = 0, checks = 0;
#define CHECK(cond, msg) do { checks++; if (!(cond)) { fails++; printf("  FALLO: %s  (linea %d)\n", msg, __LINE__); } } while (0)

static void send(DockCore& c, const char* s) { for (const char* p = s; *p; p++) c.feed(*p); c.feed('\n'); }
static std::string last(FakeHw& h) { return h.out.empty() ? "" : h.out.back(); }
static void run(DockCore& c, FakeHw& h, int maxIter = 2000000) {
  for (int i = 0; i < maxIter && c.moving(); i++) { h.now += 100; c.tick(); }
}
static void settle(DockCore& c, FakeHw& h) { for (int i = 0; i < 3000; i++) { h.now += 100; c.tick(); } }

int main() {
  printf("== 1. saludo y errores de sintaxis\n");
  { FakeHw h; DockCore c(h, false, false);
    send(c, "HELLO"); CHECK(last(h) == "AIDLY-DOCK 1 ROT=180", "HELLO");
    send(c, "FOO"); CHECK(last(h) == "ERR CMD", "comando desconocido");
    send(c, "GOTO 181"); CHECK(last(h) == "ERR RANGE", "GOTO 181");
    send(c, "GOTO -181"); CHECK(last(h) == "ERR RANGE", "GOTO -181");
    send(c, "GOTO abc"); CHECK(last(h) == "ERR ARG", "GOTO abc");
    send(c, "GOTO "); CHECK(last(h) == "ERR ARG", "GOTO con espacio y sin argumento");
    send(c, "GOTO"); CHECK(last(h) == "ERR ARG", "GOTO sin argumento");
    send(c, "GOTOX 5"); CHECK(last(h) == "ERR CMD", "GOTOX no es GOTO");
    send(c, "goto 5"); CHECK(last(h) == "ERR CMD", "el protocolo es en mayusculas");
    send(c, "ROT 12x"); CHECK(last(h) == "ERR ARG", "ROT 12x");
    send(c, "SPEED 0"); CHECK(last(h) == "ERR ARG", "SPEED 0");
    send(c, "SPEED 11"); CHECK(last(h) == "ERR ARG", "SPEED 11");
    send(c, "SPEED x"); CHECK(last(h) == "ERR ARG", "SPEED x");
    send(c, "SPEED 10"); CHECK(last(h) == "OK", "SPEED 10");
    send(c, "STATUS"); CHECK(last(h) == "POS 0 MOVING 0 LIM 0", "STATUS inicial");
    CHECK(h.coilWrites == 0, "sin ordenes de giro no se tocan las bobinas");
    // linea demasiado larga y recuperacion
    std::string basura(60, 'A'); send(c, basura.c_str()); CHECK(last(h) == "ERR CMD", "linea larga -> ERR CMD");
    send(c, "HELLO"); CHECK(last(h) == "AIDLY-DOCK 1 ROT=180", "se recupera tras linea larga");
    // CRLF y espacios
    for (const char* p = "  STATUS  \r\n"; *p; p++) c.feed(*p); CHECK(last(h) == "POS 0 MOVING 0 LIM 0", "CRLF y espacios");
  }

  printf("== 2. GOTO responde OK al aceptar (la app espera 1.5 s) y llega exacto\n");
  { FakeHw h; DockCore c(h, false, false);
    send(c, "GOTO 90"); CHECK(last(h) == "OK", "OK inmediato"); CHECK(c.moving(), "queda moviendose");
    send(c, "STATUS"); CHECK(last(h) == "POS 0 MOVING 1 LIM 0", "STATUS durante el giro");
    uint32_t t0 = h.now; run(c, h); uint32_t dt = h.now - t0;
    CHECK(c.steps() == 1536 && h.actual == 1536, "1536 pasos = 90 grados exactos");
    send(c, "STATUS"); CHECK(last(h) == "POS 90 MOVING 0 LIM 0", "STATUS al llegar");
    printf("  giro de 90 grados con la velocidad por defecto (10): %.2f s (intervalo %u us/paso)\n", dt / 1e6, (unsigned)c.intervalUs());
    CHECK(dt / 1e6 > 3.6 && dt / 1e6 < 3.9, "90 grados en ~3.75 s (24 grados/s)");
    CHECK(h.mask != 0, "bobinas aun energizadas justo al parar (retencion breve)");
    settle(c, h); CHECK(h.mask == 0 && h.offWrites == 1, "bobinas liberadas tras el tiempo de retencion");
    send(c, "GOTO -90"); run(c, h); CHECK(c.steps() == -1536 && h.actual == -1536, "cambio de sentido -90");
    send(c, "ROT 90"); run(c, h); send(c, "STATUS"); CHECK(last(h) == "POS 0 MOVING 0 LIM 0", "ROT 90 vuelve a 0");
    send(c, "ROT 181"); CHECK(last(h) == "ERR RANGE", "ROT fuera de rango");
  }

  printf("== 3. STOP inmediato y reorientacion en pleno giro\n");
  { FakeHw h; DockCore c(h, false, false);
    send(c, "GOTO 180");
    for (int i = 0; i < 20000; i++) { h.now += 100; c.tick(); if (c.steps() >= 500) break; }
    long antes = c.steps(); send(c, "STOP"); CHECK(last(h) == "OK", "STOP responde OK");
    for (int i = 0; i < 100; i++) { h.now += 5000; c.tick(); }
    CHECK(c.steps() == antes && !c.moving(), "no avanza ningun paso tras STOP");
    send(c, "GOTO 60"); for (int i = 0; i < 4000; i++) { h.now += 100; c.tick(); if (c.steps() > antes + 40) break; }
    send(c, "GOTO -30"); run(c, h);
    CHECK(c.steps() == -512 && h.actual == -512, "reorientacion: termina en -30 grados sin perder pasos");
    CHECK(h.out.end() == std::find(h.out.begin(), h.out.end(), "!!salto de fase"), "sin saltos de fase al invertir");
  }

  printf("== 4. perdida de USB: se detiene sola\n");
  { FakeHw h; DockCore c(h, false, false);
    send(c, "GOTO 100");
    for (int i = 0; i < 30000; i++) { h.now += 100; c.tick(); if (c.steps() >= 300) break; }
    h.usbOn = false; long antes = c.steps(); h.now += 100; c.tick();
    for (int i = 0; i < 100; i++) { h.now += 5000; c.tick(); }
    CHECK(!c.moving() && c.steps() == antes, "sin USB: parada inmediata y sin movimiento posterior");
  }

  printf("== 5. limite: EVT LIMIT al llegar a +-180 y LIM 1\n");
  { FakeHw h; DockCore c(h, false, false);
    send(c, "SPEED 10"); send(c, "GOTO 180"); run(c, h);
    CHECK(last(h) == "EVT LIMIT", "EVT LIMIT"); send(c, "STATUS"); CHECK(last(h) == "POS 180 MOVING 0 LIM 1", "LIM 1 en el tope");
    send(c, "GOTO 170"); run(c, h); send(c, "STATUS"); CHECK(last(h) == "POS 170 MOVING 0 LIM 0", "LIM 0 al alejarse");
  }

  printf("== 6. fases de las bobinas y velocidad\n");
  { FakeHw h; DockCore c(h, false, false);
    send(c, "ROT 1"); run(c, h);
    bool seq = true; for (size_t i = 1; i < h.idxTrace.size(); i++) seq &= (h.idxTrace[i] == (h.idxTrace[i-1] + 1) % 4);
    CHECK(seq && h.idxTrace.size() == 17, "avance: fases 0,1,2,3,0.. (17 pasos = 1 grado)");
    send(c, "SPEED 1"); uint32_t lento = c.intervalUs(); send(c, "SPEED 10"); uint32_t rapido = c.intervalUs();
    printf("  intervalo SPEED 1 = %u us (%.1f rpm motor), SPEED 10 = %u us (%.1f rpm)\n", (unsigned)lento, 60e6 / (2048.0 * lento), (unsigned)rapido, 60e6 / (2048.0 * rapido));
    CHECK(lento > rapido, "SPEED 10 es mas rapido");
    double dps = 360.0 / (6144.0 * rapido / 1e6);
    printf("  velocidad maxima de la plataforma: %.1f grados/s (la app limita a 30)\n", dps);
  }

  printf("== 7. HOME con sensor Hall\n");
  { FakeHw h; h.origin = 0; DockCore c(h, true, false);
    h.actual = 853;                                   // la plataforma esta a ~50 grados del iman; el firmware cree estar en 0
    send(c, "SPEED 10"); send(c, "HOME"); CHECK(last(h) == "OK", "HOME responde OK de inmediato");
    run(c, h);
    CHECK(c.homed() && c.steps() == 0, "HOME termina con posicion 0");
    printf("  error de origen tras HOME: %ld pasos (%.2f grados)\n", h.actual - h.origin, (h.actual - h.origin) * 360.0 / 6144.0);
    CHECK(labs(h.actual - h.origin) <= 1, "centra la ventana del iman (+-1 paso)");
    send(c, "GOTO 45"); run(c, h); CHECK(labs(h.actual - (h.origin + 768)) <= 1, "GOTO posterior parte del origen real");
  }
  { FakeHw h; h.actual = -2000; DockCore c(h, true, false);        // origen detras (negativo)
    send(c, "SPEED 10"); send(c, "HOME"); run(c, h);
    CHECK(c.homed() && labs(h.actual - h.origin) <= 1, "HOME encuentra el iman barriendo en sentido negativo");
  }
  { FakeHw h; h.actual = 20; DockCore c(h, true, false);            // empieza dentro de la ventana
    send(c, "SPEED 10"); send(c, "HOME"); run(c, h);
    CHECK(c.homed() && labs(h.actual - h.origin) <= 1, "HOME desde dentro de la ventana");
  }
  { FakeHw h; h.hasMagnet = false; DockCore c(h, true, false);       // sensor sin iman: debe fallar sin girar para siempre
    send(c, "SPEED 10"); send(c, "HOME"); run(c, h);
    CHECK(!c.homed() && last(h) == "EVT LIMIT", "sin iman: HOME falla con EVT LIMIT");
    CHECK(labs(h.actual) < 6144 * 2, "el barrido esta acotado");
    send(c, "GOTO 10"); CHECK(last(h) == "OK", "tras fallar, acepta ordenes");
  }
  { FakeHw h; h.actual = 853; DockCore c(h, true, false);
    send(c, "SPEED 1"); send(c, "HOME");
    send(c, "GOTO 10"); CHECK(last(h) == "ERR BUSY", "GOTO durante HOME -> ERR BUSY");
    send(c, "HOME"); CHECK(last(h) == "ERR BUSY", "HOME durante HOME -> ERR BUSY");
    send(c, "STOP"); CHECK(last(h) == "OK" && !c.moving(), "STOP cancela el HOME");
  }

  printf("== 8. telefono y boton\n");
  { FakeHw h; DockCore c(h, false, true);
    h.phoneOn = true; h.now += 1000; c.tick(); CHECK(last(h) == "EVT PHONE 1", "EVT PHONE 1");
    h.phoneOn = false; h.now += 1000; c.tick(); CHECK(last(h) == "EVT PHONE 0", "EVT PHONE 0");
    c.button(); CHECK(last(h) == "EVT BTN", "EVT BTN");
  }

  printf("\n%d comprobaciones, %d fallos\n", checks, fails);
  return fails ? 1 : 0;
}
