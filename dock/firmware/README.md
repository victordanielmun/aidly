# Firmware del dock Aidly (protocolo v1)

Implementa [`../PROTOCOLO.md`](../PROTOCOLO.md) y lo que de verdad espera la app (`ClienteDock`, `DockConectado`). Sustituye al firmware de
prueba anterior, que bloqueaba hasta terminar cada giro y hacía que la app diera por fallidas las órdenes.

| Archivo | Qué es | Estado |
|---|---|---|
| `dock_core.h` | Núcleo sin dependencias de Arduino (protocolo, movimiento, HOME con Hall, eventos) | **Probado en PC**: 55 comprobaciones, 0 fallos |
| `dock_core_test.cpp` | Pruebas con reloj y plataforma simulados | Compila con MSVC `/W4` |
| `dock_firmware/dock_firmware.ino` | Adaptador Arduino (pines, DTR, botón) | **Sin compilar ni probar en placa** |

```powershell
cd dock\firmware
cmd /c '"C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat" && cl /nologo /std:c++17 /EHsc /W4 dock_core_test.cpp'
.\dock_core_test.exe
```

## Cómo responde
`GOTO`, `ROT` y `HOME` contestan `OK` **al aceptar la orden**, no al terminar el giro; el avance se consulta con `STATUS ... MOVING`.
`STOP` actúa en el siguiente paso, el dock se detiene solo si pierde el USB y suelta las bobinas 150 ms después de parar.

## Cambios respecto al protocolo anterior
1. **`OK` = orden aceptada**, no "giro terminado".
2. **`ERR BUSY`** para `GOTO`/`ROT`/`HOME` mientras hay un `HOME` en curso.
3. **`ERR ARG`** para argumentos no numéricos (`GOTO abc`, `GOTO` sin número).
4. **`SPEED` por defecto = 10** (12 rpm de motor = **24 °/s**). La app no manda `SPEED`.
5. **Sin sensor Hall** (`PIN_HALL = -1`), `HOME` solo va a 0 con la posición supuesta.

## Lo que hace la app con esto
- Al conectar manda `HOME` y espera `MOVING 0` antes de mover nada; si falla, no se mueve y manda `STOP`.
- Su control interno usa 24 °/s (la velocidad del firmware).
- `ERR BUSY` no se trata como fallo: reintenta con el siguiente cuadro.
- Un segundo después del último `GOTO` consulta `STATUS` y corrige su ángulo si el dock quedó en otro sitio.

## No verificado (necesita hardware)
- Que `(bool)Serial` refleje la pérdida de USB en la placa elegida (ESP32-S3, RP2040, Pro Micro).
- Pasos perdidos: el firmware asume que el rotor arranca en la fase 0; el primer giro puede perder hasta 3 pasos (≈0,2°) y `HOME` con Hall lo corrige. No hay rampa de aceleración.
- Polaridad y ventana real del Hall (la prueba simula ±3°). En GPIO34/39 del ESP32 hace falta pull-up externo.
- Que el ruido de los pasos no moleste al micrófono.
