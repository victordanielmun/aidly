# Plan: llevar el rediseño de Aidly a la app

> **Estado 19-nov (noche):** rediseño aplicado y **verificado en el A23 (Android 14, 384 dp)**: Inicio (cabe sin desplazar), ícono y pantalla de arranque con la mascota, cara animada (66 fps, 0,13 % de fotogramas lentos), Ajustes, Mi familia, SOS, alarma, ambos widgets colocados con vista previa y letra al 200 % (emulador).
> Pendiente: TalkBack (orden y etiquetas), contraste con el filtro de luz azul apagado, y repasar Medicina/Analizar con la cámara real. Capturas en `docs/capturas/redisenio/`.

Fuente: lienzo de diseño "Aidly – Rediseño pantalla principal y widget" (4 tableros: **Inicio**, **Hablar con Aidly
(cara animada)**, **Widget grande**, **Widget compacto**). Estado de la app al 19-nov-2026. Entrega del hackathon: 30-nov.

## 1. Qué propone el diseño

| Tablero | Contenido |
|---|---|
| Inicio (390×844) | Cabecera con mascota + "Aidly" + botón Ajustes; saludo "Buenos días" + fecha; **Hablar con Aidly** como tarjeta principal (120 px, micrófono, "Toca y háblame"); 4 filas de 72 px: **Recordatorios** ("Próximo: 12:30"), **Mi familia** ("Llamar o escribir"), **Analizar un papel**, **Leer medicina**; interruptor **Leerme los mensajes**; botón **Pedir ayuda** al pie (coral). |
| Hablar (cara animada) | Cara con 6 emociones: reposo, escuchando (aro que pulsa), pensando (ojos arriba, boca plana), hablando (boca que se abre y cierra), contento (ojos felices, sonrisa grande, rebota), preocupado (cejas, boca triste). Título de estado + subtítulo grande. Interruptor **Reducir movimiento**. |
| Widget grande (360×320) | Mascota + "Aidly", **Hablar** grande y tres botones: **Papel**, **Medicina**, **Ayuda**. |
| Widget compacto (360×140) | **Hablar con Aidly** + **Ayuda** (104 px). |

Sistema visual: fondo `#2B2B26`, tarjetas `#383832`, texto crema `#FFF3DC` y suave `#CFC9BA`, turquesa `#40DDDA`,
amarillo `#FFE000`, coral `#FF7F50`, tinta `#1F1F1B`; tipografía **Atkinson Hyperlegible** (diseñada para baja visión).

## 2. Lo que está bien (se conserva)
- Jerarquía clara: una acción principal enorme, filas homogéneas, **Pedir ayuda** siempre abajo y de otro color.
- Todo cabe en una pantalla sin desplazar (cumple lo que se pidió); íconos con texto, nunca solo íconos.
- Contrastes (medidos): crema/fondo 12,9:1 · suave/tarjeta 7,1:1 · tinta/turquesa 9,9:1 · tinta/amarillo 12,5:1 ·
  tinta/coral 6,6:1. Todo pasa AA y casi todo AAA. (La paleta actual: 17,5:1 · 12,5:1 · 7,4:1, también correcta.)
- La cara da **estado visible sin leer** (escucha, piensa, habla) y es lo más vendible del video. Incluye "reducir movimiento".
- Íconos de trazo uniforme (estilo Lucide, licencia ISC: se pueden usar).

## 3. Hallazgos: dónde el diseño choca con la app o con lo decidido

1. **La paleta del diseño parece sacada de mis capturas, no de la app.** `#2B2B26`, `#FFF3DC` y `#40DDDA` son los
   colores que muestra el A23 con su filtro de luz azul (la app usa fondo `#0A1A2F`, blanco, `#22E5FF`). Hay que
   **decidir una paleta a propósito** (ver §5). Recomiendo adoptar la del diseño: es cálida, menos deslumbrante y pasa contraste.
2. **"Mi familia: Llamar o escribir"** no es lo que hoy hace la pantalla (compartir un código con la familia). Hacen
   falta las dos cosas: contactos favoritos para llamar y la sección "Cómo estoy" (círculo familiar).
3. **"Leer medicina"** vs lo acordado: *Consultar medicina* (identificar + resolver dudas por voz). Cambiar el texto
   y el subtítulo ("Foto y preguntas").
4. **Alturas fijas** (72/76/120 px): con la letra del sistema al 150-200 % el texto se recorta. En Android deben ser
   `heightIn(min = …)` y crecer.
5. **Pantalla Hablar**: "Probar emociones" y "Probar una conversación" son controles de prototipo, no producto.
   Faltan: línea con lo que dijo la persona ("Tú: …"), botón **Hablar otra vez**, **Detener**, y qué pasa si falla.
6. **Ajustes** aparece como botón, pero no está diseñado. Aquí van: permisos faltantes, poner widget, reducir movimiento,
   tamaño de letra, velocidad de voz y "Pruebas técnicas" (hoy en "Más opciones").
7. **Icono de Ayuda = teléfono**: nuestro flujo avisa a la familia y ofrece el 123; el ícono sugiere "llamar".
   Opciones: ícono de alerta/campana o dejar el teléfono si el primer toque marca de verdad. Decidir.
8. **Los widgets** no pueden copiar el diseño al pie de la letra: Glance no admite tipografías propias ni formas
   arbitrarias; usa fuente del sistema, esquinas redondeadas (API 31+) e íconos como imágenes vectoriales.
9. **Solo hay 4 tableros**: faltan Recordatorios (lista y pantalla de alarma), Medicina (cámara, resultado, preguntas),
   Analizar, Mi familia, Pedir ayuda y Ajustes. Se resuelven con los mismos componentes (§4, fase 5).
10. **Emulador** (MSI App Player): Android 9, 1280×720 a 240 dpi. Sirve para lógica y voz; **no** valida la vista
    previa del widget (API 31+), los permisos de Android 13/14 ni el tamaño real de pantalla. El A23 sigue siendo el banco visual.

## 4. Plan por fases

Reglas: tokens en un solo lugar, componentes compartidos, nada de colores sueltos; todo con `heightIn`, `sp` y `contentDescription`.

### Fase 0 · Decisiones (medio día, bloquea el resto)
Responder las 6 preguntas de §5. Congelar tokens.

### Fase 1 · Base visual (1 día)
- `Tema.kt`: tokens del diseño (colores, radios 24/36/38, alturas mínimas), `AidlyTema` con tipografía Atkinson.
- `res/font/atkinson_regular.ttf`, `atkinson_bold.ttf` (OFL, ~54 KB cada uno; descarga verificada en googlefonts/atkinson-hyperlegible).
- `Iconos.kt`: los 10 íconos usados (micrófono, campana, personas, documento, pastilla, altavoz, teléfono, chevron, ajustes, volver) como `ImageVector`.
- `Componentes.kt`: `TarjetaHablar`, `FilaAccion(icono, titulo, subtitulo, estilo)`, `PildoraAyuda`, `InterruptorAidly`, `CabeceraAidly`.
- **Hecho cuando:** una pantalla de prueba muestra todos los componentes igual que el lienzo, con letra del sistema al 100 % y al 200 %.

### Fase 2 · Inicio + Ajustes (1,5 días)
- Reescribir `HomeActivity` según el tablero: saludo por hora (días/tardes/noches) y fecha en español; "Próximo: HH:mm"
  desde `Recordatorios.listar()`; interruptor "Leerme los mensajes" = Compañero (con guía de permisos si faltan).
- `AjustesActivity`: permisos faltantes, widget, reducir movimiento, tamaño de letra, voz, pruebas técnicas.
- Con pantallas pequeñas o letra grande: el contenido se desplaza, **Pedir ayuda** queda fijo al pie.
- **Hecho cuando:** en el A23 a 100 % cabe sin desplazar y a 200 % no se recorta nada.

### Fase 3 · Cara animada (2 días) — lo más visible para el jurado
- `AidlyCara(emocion, reducirMovimiento)` en Compose (`Canvas` + `rememberInfiniteTransition`): parpadeo, mirada, aro que pulsa,
  boca que habla, rebote, cejas. Tomar las rutas del SVG del lienzo.
- `enum Emocion { REPOSO, ESCUCHANDO, PENSANDO, HABLANDO, CONTENTO, PREOCUPADO }` y mapa desde el flujo real:
  mic abierto → ESCUCHANDO · llamada a Gemini → PENSANDO · TTS hablando → HABLANDO · éxito → CONTENTO · error/"no te entendí" → PREOCUPADO.
- Rediseñar `HablarActivity`: cara + estado + "Tú: …" + respuesta + **Hablar otra vez** / **Volver**; quitar controles de demo.
- Respetar `ANIMATOR_DURATION_SCALE = 0` del sistema además del interruptor propio.
- Reemplazar `Mascota` estática por `AidlyCara` en alarma de recordatorio (HABLANDO), SOS (PREOCUPADO→CONTENTO),
  Medicina y Analizar (PENSANDO).
- **Límite conocido:** Android no da la amplitud del TTS; la boca se anima en bucle mientras `UtteranceProgressListener` indica que habla (no es sincronía labial real).
- **Hecho cuando:** las seis emociones se ven en el A23 a 60 fps sin tirones y el consumo es razonable (la animación se detiene al salir de pantalla).

### Fase 4 · Widgets (1,5 días)
- Dos widgets Glance: `AidlyWidgetCompacto` (4×2, Hablar + Ayuda) y `AidlyWidgetGrande` (4×3, Hablar + Papel/Medicina/Ayuda).
  Conservar `AidlyWidgetReceiver` como el grande para no romper el que ya está colocado.
- Íconos como vectores en `res/drawable`; fondo y botones con `cornerRadius`; previsualización (`previewLayout`) de cada uno.
- Fuente del sistema (límite de Glance); mismos colores y textos que el diseño.
- **Hecho cuando:** se añaden desde el selector sin mensaje de error y los botones abren la pantalla correcta.

### Fase 5 · Resto de pantallas con los mismos componentes (1 día)
Alarma de recordatorio, lista de recordatorios, Medicina (cámara/resultado/preguntas), Analizar, Mi familia (con contactos
favoritos + "Cómo estoy"), Pedir ayuda. Si se quiere diseño propio de alguna, pedir tablero; si no, se aplica el sistema.

### Fase 6 · Calidad (1 día)
Letra 100/150/200 %, TalkBack (orden y etiquetas), contraste con el filtro de luz azul encendido y apagado, A23 + emulador,
capturas para Devpost, actualizar `PLAN.md` y la memoria.

## 5. Decisiones que necesito (una línea cada una)
1. **Paleta:** ¿la del diseño (cálida, `#2B2B26`) o la actual (azul marino)? *Recomiendo la del diseño.*
2. **Medicina:** "Consultar medicina" (acordado) o "Leer medicina" (diseño)? *Recomiendo Consultar.*
3. **Mi familia:** ¿una pantalla con dos secciones — llamar a favoritos + cómo estoy? *Recomiendo sí.*
4. **Pedir ayuda:** ¿ícono de teléfono (como el diseño) o de alerta? *Recomiendo alerta, porque primero avisa y luego ofrece llamar.*
5. **Widgets:** ¿ofrecer los dos tamaños? *Recomiendo sí.*
6. **Alcance de fase 5:** ¿aplico el sistema yo, o prefieres diseñar esas pantallas primero? *Recomiendo aplicar el sistema.*

## 6. Calendario (hoy jue 19; entrega lun 30)

| Día | Trabajo |
|---|---|
| Vie 20 | Fase 0 + Fase 1 |
| Sáb 21 – dom 22 | Fase 2 (Inicio + Ajustes) |
| Lun 23 – mar 24 | Fase 3 (cara animada + Hablar) |
| Mié 25 | Fase 4 (widgets) |
| Jue 26 | Fase 5 + Fase 6 |
| Vie 27 | Pruebas con audios y con el teléfono; correcciones |
| Sáb 28 – dom 29 | Video de 60 s y texto de Devpost |
| Lun 30 | Margen de entrega |

Si el tiempo aprieta, orden de recorte: Fase 5 (se hereda el estilo base) → widget compacto → Ajustes avanzados. **No recortar** Inicio ni la cara.

## 7. Riesgos
- Nube de Gemini sobrecargada (8-11 s por respuesta): la cara en PENSANDO ayuda a que la espera no parezca fallo; aun así hay que grabar el video con calma.
- Rendimiento de la animación en un A23 (gama baja): medir; si baja de 50 fps, simplificar (menos capas, sin sombras).
- Cambio de paleta tardío: por eso la Fase 0 va primero y los colores solo viven en `Tema.kt`.
