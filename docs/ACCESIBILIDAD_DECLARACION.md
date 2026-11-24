# Aidly: declaración del servicio de accesibilidad y del botón flotante / Accessibility service and floating button declaration

Este documento sigue el formato de las declaraciones que Google Play pide para la API de accesibilidad y sirve también como explicación para los
jueces del concurso. Código: [`AidlyAccesibilidad.kt`](../app/src/main/java/com/aidly/app/AidlyAccesibilidad.kt),
[`aidly_accesibilidad.xml`](../app/src/main/res/xml/aidly_accesibilidad.xml), [`OrdenActivity.kt`](../app/src/main/java/com/aidly/app/OrdenActivity.kt),
[`AyudaOtrasAppsActivity.kt`](../app/src/main/java/com/aidly/app/AyudaOtrasAppsActivity.kt).

## Español

**Para qué existe.** Aidly es para personas mayores, y muchas se pierden al salir de Aidly hacia otra aplicación (TikTok, YouTube, WhatsApp): no saben cómo
volver, no encuentran el botón de atrás, no aciertan a tocar un botón pequeño. El servicio de accesibilidad existe solo para ayudar en eso, y no tiene
otra finalidad (`isAccessibilityTool = true`).

**Qué hace, y solo cuando la persona lo pide.**
1. Muestra el **botón flotante de Aidly** sobre las demás apps (capa de accesibilidad, sin el permiso «mostrar sobre otras apps»). Se arrastra y se puede quitar.
2. El botón abre un panel de botones grandes: *decir una orden*, *atrás*, *inicio*, *bajar*, *subir*, *volver a Aidly*, *quitar botón*.
3. Con la voz («toca Buscar», «atrás», «baja la pantalla»): toca el botón cuyo texto coincide, va atrás o al inicio, o desplaza la pantalla.

**Qué NO hace.**
- No se suscribe a ningún evento de accesibilidad: no recibe lo que se escribe ni lo que ocurre en pantalla.
- Solo mira los textos de la ventana de la otra app **en el instante en que la persona pide tocar algo**, para encontrar el botón; no guarda ni envía lo que ve.
- Nunca toca nada por su cuenta, no lee contraseñas ni mensajes, no se usa para publicidad, análisis ni para controlar apps sin que la persona lo pida.

**Control de la persona.** Está apagado de fábrica. Solo se enciende desde *Ajustes > Accesibilidad* del sistema, después de leer el aviso de la pantalla
«Ayuda con otras apps» (qué hace y qué no hace). Se apaga ahí mismo en cualquier momento, y el botón flotante se puede quitar desde el propio botón o desde esa pantalla.

**Datos.** No sale nada del teléfono por este servicio (ver `PRIVACY_DATA_FLOW.md`, F19 y F20). El audio de una orden de voz sigue los flujos normales de la voz (F1, F9).

## English

**Why it exists.** Aidly is for older adults, and many get lost when they leave Aidly for another app (TikTok, YouTube, WhatsApp): they do not know how to get back, cannot find
the back button or miss small targets. The accessibility service exists only to help with that (`isAccessibilityTool = true`).

**What it does, only when the person asks.** Shows the Aidly floating button over other apps (accessibility overlay layer; no "display over other apps" permission);
opens a panel of large buttons (say a command, back, home, scroll down, scroll up, back to Aidly, hide button); and, by voice ("tap Search", "go back", "scroll down"),
taps the button whose text matches, goes back or home, or scrolls.

**What it does NOT do.** It subscribes to no accessibility events (it never receives what you type or what happens on screen). It only looks at the other app's window text
**at the moment the person asks to tap something**, to find the button, and it never stores or sends what it sees. It never taps anything on its own, never reads passwords or messages,
and is not used for ads, analytics or to drive apps unprompted.

**User control.** Off by default. It can only be turned on in the system's *Settings > Accessibility*, after the in-app notice ("Help with other apps") explaining what it does and does not do.
It is turned off there at any time; the floating button can be hidden from the button itself or from that screen.

**Data.** Nothing leaves the phone through this service (`PRIVACY_DATA_FLOW.md`, F19 and F20). The audio of a voice command follows the normal voice flows (F1, F9).
