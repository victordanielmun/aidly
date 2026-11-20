# Aidly Dock: prototipo de diseño (NO construido)

Estado: **solo diseño en planos**. No existe un aparato físico ni se ha probado nada con hardware. Para el hackathon #BuildHybridAI 2026
el proyecto se entrega como app Android; el dock se muestra como evolución del producto.

## La idea
Una base de mesa donde se apoya el teléfono de una persona mayor: lo mantiene cargado y en el mismo sitio, le da un parlante más fuerte y un
micrófono de mesa, y puede girar la pantalla hacia ella. Todo por **un solo conector USB-C** hacia el teléfono.

## Revisiones (todas con fecha 19-nov-2026)
| Rev. | Archivos | Qué es | Estado |
|---|---|---|---|
| A | `planos_base_dock_usbc.pdf`, `base_dock_usbc_asistente.dxf` | Base fija 108×60×42 mm con conector USB-C inclinado 15°, 2 parlantes Ø28 y micrófono MEMS; el teléfono actúa de anfitrión USB y la base se presenta como audio USB | Diseño |
| B | `planos_dock_giratorio_esp32_v2.pdf`, `dock_giratorio_esp32_v2.dxf` | Dock giratorio: pinza 60–84 mm, motor 28BYJ-48, anillo colector, ESP32 con Bluetooth (A2DP + HFP), 2 celdas 18650 | Diseño; **superada** (ver problemas) |
| C | *(descrita aquí, sin planos)* | **Un solo USB-C**: cable flexible hasta una base con hub USB-C de paso de carga, tarjeta de audio USB con amplificador y parlantes, y un microcontrolador con puerto serie USB para el motor; giro de ±180° con el cable flojo; sin anillo colector, sin Bluetooth, sin batería | Dirección elegida |

```
Pared ─ cargador USB-C PD ─► [BASE] hub USB-C con paso de carga
                                 ├─ tarjeta de audio USB ─ amplificador ─ parlantes + micrófono
                                 └─ microcontrolador (puerto serie USB) ─ ULN2003 ─ motor + sensor Hall
                          cable USB-C flexible, por el agujero central ─► [PLACA GIRATORIA] conector al teléfono
```

## Cómo se usaría (modo dock)
- El teléfono apoyado en el dock muestra **la cara de Aidly**; la persona **habla con Aidly a través del dock** (parlantes y micrófono por el USB-C).
- La **cámara frontal** (que gira con el dock) ve a la persona con MediaPipe **en el propio teléfono**; el control corrige el ángulo para mantenerla en el encuadre. Esto evita
  tener que ubicar la voz con un solo micrófono. La persona **acepta** esta cámara y puede pausarla; no se guardan ni se envían imágenes.
- Si, después de conversar un rato, la persona deja de verse, Aidly **la llama y le pregunta si está bien**. **No detecta caídas** ni es un dispositivo de emergencias.
- En la app, el giro se muestra con un **simulador en pantalla** (interfaz `Dock` con `DockSimulado` hoy y `DockUsb` como futuro). El audio externo se prueba con un parlante Bluetooth.

## Conexión con la app
La app ya incluye el código para hablarle al dock por USB (`DockUsb.kt`, `ProtocoloDock.kt`, `AudioExterno.kt`; protocolo en [PROTOCOLO.md](PROTOCOLO.md)) y
un firmware de prueba ([firmware-prueba/](firmware-prueba/)). **Probado solo con pruebas unitarias y un puerto simulado; no con una base real.**

## Por qué la Rev. C y no la B
- Bluetooth HFP baja la calidad de voz (8/16 kHz); el audio USB la conserva, y Aidly midió su mejor precisión con el micrófono del teléfono.
- Sin canal de control en la Rev. B (el ESP32 solo hacía audio). Por USB el teléfono manda órdenes de giro por un puerto serie.
- Quita firmware Bluetooth a medida, el anillo colector (poco fiable con datos USB) y la batería de litio.
- La carga de la Rev. B con 56 kΩ en CC solo anuncia corriente USB por defecto; un hub con paso de carga negocia la potencia.

## Riesgos abiertos (sin verificar)
- Que el teléfono objetivo (Galaxy A23) enrute el audio por USB y se siga cargando con un hub: se prueba en una tarde con un hub y un parlante USB comprados.
- El motor 28BYJ-48 con reducción 3:1 es lento (~6 s para media vuelta); el ruido del motor llega al micrófono (girar solo cuando no se escucha).
- Los parlantes quedan en la base fija y no giran con el teléfono.
- Falta el soporte USB en la app Aidly (permiso al conectar, puerto serie, fijar el micrófono USB).
- Qué hace girar la base: gestos ligados a la cara animada (barato) o seguimiento con la cámara frontal (vistoso, con implicaciones de privacidad).
- Seguridad eléctrica y referencias de piezas sin comprobar contra hojas de datos.

## Cómo se presenta
Siempre como **"Aidly Dock · design prototype, not built"**.
