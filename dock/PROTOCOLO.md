# Protocolo del dock Aidly (v1)

Estado: **especificación + código de la app + firmware de prueba**. El código de la app (`ProtocoloDock.kt`, `DockUsb.kt`) tiene pruebas
unitarias con un puerto simulado; **no se ha probado con una base real** (ver `firmware-prueba/` para probar con una placa).

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
| `HOME` | `OK` | Busca el origen (sensor Hall) y queda en 0° |
| `GOTO <grados>` | `OK` o `ERR RANGE` | Gira hasta el ángulo absoluto |
| `ROT <grados>` | `OK` o `ERR RANGE` | Gira un ángulo relativo |
| `SPEED <1-10>` | `OK` o `ERR ARG` | Velocidad (1 lenta, 10 rápida) |
| `STOP` | `OK` | Detiene el motor de inmediato |
| `STATUS` | `POS <grados> MOVING <0\|1> LIM <0\|1>` | Posición actual, si se mueve, si tocó un límite |

Cualquier otra línea: `ERR CMD`.

## Eventos del dock a la app (sin que se pidan)
| Línea | Significado |
|---|---|
| `EVT BTN` | Se pulsó el botón físico del dock: la app empieza a escuchar |
| `EVT LIMIT` | Llegó a un límite de recorrido |
| `EVT PHONE 0\|1` | Sensor de presencia del teléfono en la pinza |

## Reglas de seguridad de la app (U6)
- No se manda ningún giro con la cámara pausada ni fuera del Modo dock; al pausar, salir o perder el USB se manda `STOP`.
- Como mucho 5 órdenes por segundo y solo si el ángulo cambia ≥ 2°.
- Velocidad máxima inicial de seguimiento: 30 °/s (el motor 28BYJ-48 con reducción 3:1 hace ~18–30 °/s).

## Pendiente de verificar con hardware
Sentido del giro (derecha/izquierda frente a la cámara), ruido del motor en el micrófono, enrutado del audio USB en el A23, y carga con el hub.
