# Protocolo del dock Aidly (v1)

Estado: **especificación + código de la app + firmware de prueba**. El código de la app (`ProtocoloDock.kt`, `DockUsb.kt`) tiene pruebas
unitarias con un puerto simulado; **no se ha probado con una base real** (ver [`firmware/`](firmware/): el núcleo está probado en PC; el adaptador de placa no está compilado).

## Conexión
- Un solo cable USB-C. El teléfono es el anfitrión USB y ve **dos cosas** en el dock:
  1. **Audio** USB estándar (parlantes y micrófono): no necesita protocolo, la app solo elige el dispositivo (`AudioExterno.kt`).
  2. **Puerto serie CDC-ACM** (115200 8N1) para el motor y los sensores: este protocolo.
- La carga del teléfono la negocia el hub USB-C con paso de carga de la base (no la app).

## Reglas
- Texto ASCII, **una línea por mensaje**, terminada en `\n` (se acepta `\r\n`). Mayúsculas. Un comando, una respuesta.
- La app **nunca manda movimiento** a un dispositivo que no responda al saludo `HELLO` con `AIDLY-DOCK`.
- Ángulos enteros en grados, de **-180** (izquierda) a **180** (derecha), 0 = de frente. El dock limita el recorrido a ±180° (cable flojo, sin anillo).
- El dock debe **parar solo** si pierde el USB.

## Comandos de la app al dock
| Comando | Respuesta | Qué hace |
|---|---|---|
| `HELLO` | `AIDLY-DOCK 1 ROT=180` | Saludo. Versión del protocolo y recorrido máximo en grados |
| `HOME` | `OK` o `ERR BUSY` | Busca el origen (sensor Hall) y queda en 0°. Sin sensor Hall solo va a 0 con la posición supuesta |
| `GOTO <grados>` | `OK`, `ERR RANGE`, `ERR ARG` o `ERR BUSY` | Gira hasta el ángulo absoluto |
| `ROT <grados>` | `OK`, `ERR RANGE`, `ERR ARG` o `ERR BUSY` | Gira un ángulo relativo |
| `SPEED <1-10>` | `OK` o `ERR ARG` | Velocidad (1 lenta, 10 rápida). Por defecto 10 = 24 °/s |
| `STOP` | `OK` | Detiene el motor de inmediato |
| `STATUS` | `POS <grados> MOVING <0\|1> LIM <0\|1>` | Posición actual, si se mueve, si tocó un límite |

Cualquier otra línea: `ERR CMD`. Los argumentos no numéricos dan `ERR ARG`.

**`OK` significa orden aceptada, no giro terminado.** Para saber si el giro acabó, la app consulta `STATUS` hasta ver `MOVING 0`. Mientras hay un `HOME` en curso, `GOTO`, `ROT` y `HOME` responden `ERR BUSY` (no es un fallo: se reintenta).

## Eventos del dock a la app (sin que se pidan)
| Línea | Significado |
|---|---|
| `EVT BTN` | Se pulsó el botón físico del dock: la app empieza a escuchar |
| `EVT LIMIT` | Llegó a un límite de recorrido |
| `EVT PHONE 0\|1` | Sensor de presencia del teléfono en la pinza |

## Reglas de seguridad de la app (U6)
- No se manda ningún giro con la cámara pausada ni fuera del Modo dock; al pausar, salir o perder el USB se manda `STOP`.
- Como mucho 5 órdenes por segundo y solo si el ángulo cambia ≥ 2°.
- Al conectar la app manda `HOME` y espera `MOVING 0` antes de cualquier giro; si falla, no mueve nada.
- Velocidad de seguimiento: 24 °/s, la velocidad por defecto del firmware (`SPEED 10`); la app no manda `SPEED`. Un segundo después del último `GOTO` consulta `STATUS` y corrige su ángulo si el dock quedó en otro sitio.

## Pendiente de verificar con hardware
Sentido del giro (derecha/izquierda frente a la cámara), ruido del motor en el micrófono, enrutado del audio USB en el A23, y carga con el hub.
