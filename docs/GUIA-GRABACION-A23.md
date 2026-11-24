# Guía de grabación en el A23 (video de 3 min) — qué grabar, qué decir, qué evitar

Cada clip va **en vertical, 8 a 15 s**, grabado con la grabadora de pantalla del teléfono (con sonido del sistema; el micrófono solo cuando se hable con Aidly). Todo en **inglés** (idioma de la app: English). Un clip por toma; si falla, se repite entero.

## Antes de grabar (una vez)
- Filtro de luz azul **apagado**, brillo alto, **No molestar** activado, fondo de pantalla neutro, sin notificaciones de otras apps, batería > 50 %.
- Token de **App Check** del A23 registrado (si no, nada de la nube responde).
- Aviso "Morning pill" a las **9:00** creado, círculo familiar de prueba con nombre **"Rosa"** y el código en el portátil (`https://aidly-d108b.web.app/#CÓDIGO`).
- Una caja de medicina **sin marca** (tapa el logo con cinta blanca) y una carta cualquiera sin datos personales.
- Un segundo teléfono (sin marca visible) para mandar el mensaje de "Sam".

## Los clips (en este orden de montaje)
| # | Qué mostrar | Qué decir / hacer | Dura | Sustituye |
|---|---|---|---|---|
| R1 | **Inicio**: botones grandes, la hora, "Talk to Aidly" | Solo mostrar y tocar "Talk to Aidly" | 6 s | `f_home` (ya hay captura estática) |
| R2 | **Hablar** → crear recordatorio. Luego pantalla **Recordatorios** con "9:00 AM · Morning pill" | Decir: *"Aidly, remind me to take my pill at nine."* Aidly confirma | 12 s | maqueta `i_01` |
| R3 | **Medicinas**: apuntar a la caja sin marca; cuenta atrás; tarjeta con "What the box says"; botón *Add a reminder* | Tocar "Read it to me" | 12 s | maqueta `f_med` |
| R4 | **Mensajes**: llega "Good morning, Mom. Call me when you can." desde el 2.º teléfono; Modo dock o Hablar | *"Aidly, read my messages."* Luego dictar: *"Tell him I will call after breakfast."* | 15 s | maquetas `i_05`, `i_06` |
| R5 | **Botón flotante** sobre otra app (Ajustes del sistema, no una app con marca) | *"Aidly, tap Search."* (y *"go back"*) — requiere activar el servicio de accesibilidad antes | 10 s | **nuevo** (organiza el teléfono) |
| R6 | **For you**: una tarjeta con su fuente citada | Tocar "Tell me more" | 8 s | nuevo |
| R7 | **Modo dock**: tarjeta de cámara → "Yes, turn on the camera" → cara y **Simulated base** girando al moverte | Moverte despacio delante del teléfono; quedarte fuera de cuadro y esperar el *"are you okay?"* | 15 s | `f_modo` |
| R8 | **My family**: crear código, interruptores (por defecto: última actividad, medicinas, aviso diario y "¿estás bien?" activados; ubicación apagada), compartir medicinas; luego el **portátil** con la página: "Done — take the morning pill", el aviso amarillo y el verde | Narrar nada; solo mostrar. Rotular "Only what Rosa chose to share" | 15 s | `f_circulo`, `i_02`–`i_04` |
| R9 | **Privacidad**: lo que queda en el teléfono / lo que va a la nube; interruptor "Only on my phone" | Desplazar despacio | 8 s | `f_priv` |

## Qué NO mostrar
- Marcas o logotipos (de teléfonos, apps de mensajes, medicinas); nombres reales, números o ubicaciones reales.
- Cualquier cosa que la app **no haga**: detección de caídas, notificaciones *push* a la familia, la familia viendo citas, el giro del dock real (se enseña con la **base simulada**).
- Gemini Nano funcionando: **no se ha visto** en un teléfono con AICore. Si aparece la insignia, que sea "In Google's cloud" o "Phone rules".
- La página de la familia con el color antiguo: espera al despliegue del arreglo de color o grábala ya y avísame para sustituir el clip.

## Cómo me los pasas
Súbelos a `Aidly/docs/capturas/video/` con los nombres `R1.mp4` … `R9.mp4`. Yo los recorto, rotulo ("Reads the small print", etc.), los mezclo con las escenas generadas y la música, y renderizo la versión final.


## Estado de la grabación por adb (24-nov, A23 conectado)
- **Hechos** (en `docs/capturas/video/`): `R1_home.mp4` (inicio + desplazamiento + "Talk to Aidly") y `R8_mi_familia_crear_codigo.mp4` (crear el código con el nombre "Rosa", interruptores de lo que se comparte).
- **Descartado:** "For you" trae hoy una noticia con foto de personas y el logo de un medio (no se puede mostrar). La página real de la familia mostraba la medicina real del dueño del teléfono ("take the antibiotic"): no se usa; en el video va la página con datos de ejemplo.
- **Privacidad:** el fondo del teléfono es una foto personal y la pantalla de inicio tiene apps con logos: no se graba nunca fuera de Aidly. El círculo de prueba se borró de la nube (la página ya responde "couldn't find that code").
- **Pendiente (necesita tu visto bueno o tus manos):** R2 recordatorio por voz (modifica tus recordatorios), R3 caja (tus manos), R4 mensajes (notificación simulada), R5 botón flotante (activar accesibilidad), R7 Modo dock (cámara), R9 privacidad.


## Actualizacion 2026-11-24 (R5)
- R5 grabado y guardado: `docs/capturas/video/R5_boton_flotante.mp4` (calculadora del sistema, boton flotante, menu en ingles, "Back to Aidly"). Se recorta antes de que se lea el titular de "Para ti" en el inicio.
- Para grabarlo hubo que volver a encender el interruptor "Aidly floating button" (Ajustes > Help with other apps): habia quedado apagado tras validar el menu. Estado actual del telefono: servicio de accesibilidad ON, boton flotante ON (originalmente estaba apagado).
- Texto de la app que nombra una marca: la pantalla "Help with other apps" dice "Open TikTok" en los ejemplos; no mostrar esa pantalla en el video.
