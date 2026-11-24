# Aidly: flujo de datos (qué sale del teléfono, cuándo y cómo se controla)

Estado del código al 19-nov-2026 (versión de desarrollo). Cada fila se puede comprobar en el código; la columna "Dónde" dice en qué archivo.
Idea tomada de la tabla de flujos del repo `fun-phone` (reimplementada, sin copiar código).

## 1. Interruptor general: "Usar la nube de Google"
- Al primer arranque, antes de la bienvenida, la persona elige **"Aceptar y usar la nube"** o **"Solo en mi teléfono"** (`PrivacidadActivity`).
- Sin elección, la nube se trata como **apagada**: no se manda nada por omisión.
- Cambia en cualquier momento en Ajustes > Privacidad. Con la nube apagada se bloquea en el código (`Privacidad.exigirNube()`), no solo en pantalla.

## 2. Flujos hacia fuera del teléfono

| ID | Disparador | Datos que salen | Destino | Con nube apagada | Dónde |
|---|---|---|---|---|---|
| F1 | "Hablar con Aidly", dictado de respuestas, preguntas sobre la medicina | Audio de la frase (WAV 16 kHz, hasta 10 s) | Gemini vía Firebase AI Logic (Gemini Developer API) | No sale. Se usa el reconocedor de voz del sistema (ver F9) | `GeminiStt.kt`, `HablarActivity.kt`, `AidlyService.kt` |
| F2 | Conversación libre ("cuéntame…") | Texto de la pregunta | Gemini (primero Gemini Nano en el teléfono, si existe) | Solo Gemini Nano; sin él, Aidly avisa que necesita la nube | `AsistenteVoz.kt` |
| F3 | Recordatorios y citas cuyas frases las reglas locales no entienden | Texto de la frase + fecha y hora actuales | Gemini (primero Nano) | Solo Nano; si no hay, "no entendí cuándo" | `Interprete.kt`, `Recordatorios.kt`, `Agenda.kt` |
| F4 | "¿Qué dice aquí?" con una medicina (pantalla de medicina) | Foto (lado mayor ≤ 1792 px) | Gemini (nube) | Pantalla "Esto necesita la nube" | `MedicinaValidador.kt`, `MedicinaActivity.kt` |
| F5 | Preguntas sobre la medicina leída | Texto de la pregunta + datos impresos en la caja | Gemini (nube) | Igual que F4 | `MedicinaValidador.preguntar` |
| F6 | "Analiza" (papeles, recibos, posibles estafas) | Foto | Gemini (nube) | Pantalla "Esto necesita la nube" | `AnalizaValidador.kt`, `AnalizaActivity.kt` |
| F7 | Preguntas de internet (clima, precios, noticias) | Texto de la pregunta | Gemini con búsqueda de Google | Aidly avisa que necesita la nube; ya no abre Google por su cuenta | `Busqueda.kt`, `Agente.kt` |
| F8 | "Mi familia" activada por la persona | Nombre, código, y **solo lo que se active**: última actividad, estado de medicinas (texto y estado de hasta 12 avisos), ubicación, estado del check-in, idioma. SOS: hora y ubicación | Firestore `circulos/{código de 12 caracteres}` y usuario anónimo de Firebase Auth | Independiente del interruptor: solo ocurre si la persona activa "Mi familia" | `Circulo.kt`, `firestore.rules` |
| F9 | Cualquier uso del reconocedor de voz del sistema (aviso "¿Quieres que te lo lea?", Hablar con nube apagada) | Audio de la frase | **El teléfono**: Android puede resolverlo en el aparato o con el servicio de voz de Google, según modelo. Aidly no lo controla | Es el mismo servicio | `AidlyService.kt`, `EscuchaSistema.kt` |
| F10 | Cada arranque con Firebase | Token de App Check (comprueba que es la app auténtica) | Firebase App Check. En desarrollo se usa el proveedor de depuración; en producción, Play Integrity | Se mantiene | `AidlyApp.kt` |
| F11 | Familiar abre la página web | El código que escribe; la página lee ese documento | Firebase Hosting + Firestore (lectura por código, sin listado) | — | `familia/index.html`, `firestore.rules` |

| F12 | "Decir quién llama" (opcional, Android 10+; la persona concede el rol de filtrado de llamadas) | Ninguno: el número entrante se busca en favoritos y contactos del propio teléfono y Aidly lo dice en voz alta. Aidly nunca bloquea ni contesta la llamada | No sale | Desactivado hasta que se concede el rol | `Llamadas.kt` |

| F13 | **Modo dock** con cámara (opcional, apagada hasta que la persona acepta en esa pantalla; se puede pausar) | Ninguno: la cámara frontal se analiza **en el teléfono** con MediaPipe (detector de caras y pose ligera). No hay vista previa, no se guarda ni se envía ningún cuadro; de cada cuadro solo se usa "se ve / no se ve" y la posición horizontal. Si la persona deja de verse tras haber conversado, Aidly le pregunta en voz alta si está bien (**no detecta caídas ni emergencias**) | No sale | Apagada hasta que se acepta; con la cámara pausada no se analiza nada | `ModoDockActivity.kt`, `PresenciaCamara.kt`, `CompaniaAtenta.kt` |

| F14 | **Base USB del dock** (opcional): la app busca un puerto serie USB, pide el permiso al sistema y solo le habla si el dispositivo responde "AIDLY-DOCK" | Órdenes de ángulo (GOTO/STOP…) a la base; el micrófono y los parlantes USB son audio local. Nada se envía a internet | No sale | Sin base USB conectada no ocurre nada | `DockUsb.kt`, `ProtocoloDock.kt`, `AudioExterno.kt` |

| F15 | **Para ti** (opcional; la persona elige sus gustos en esa pantalla) | Solo con la nube encendida y al pedir novedades: la **lista de temas elegidos** (por ejemplo "música, cocina"), la fecha, el país del idioma del teléfono y los **títulos ya mostrados** (para no repetirlos). No sale el nombre, ni la ubicación, ni lo que la persona dice | Gemini vía Firebase AI Logic, con búsqueda de Google si hay cuota; si no, sin búsqueda | Sin nube: solo las tarjetas que trae la app y las ya guardadas; no sale nada | `FeedTarjetas.kt`, `Intereses.kt`, `ParaTiActivity.kt` |
| F16 | **Conversar sobre una tarjeta** ("Háblame de esto") | El audio de la pregunta (con la nube, igual que F1) y el texto de la tarjeta que se está viendo. Con Gemini Nano en el teléfono, si existe, no sale | Gemini (primero Nano) | Sin nube: reconocedor del sistema (F9) y solo el modelo local | `AsistenteVoz.responderSobre` |

| F17 | **Fuentes web de "Para ti" y clima** (solo con la nube encendida) | Una petición HTTPS normal a Wikipedia (efemérides del día), la BBC, Smithsonian Magazine (titulares) y Open-Meteo (clima: el **nombre de la ciudad** que la persona dijo, o la última que preguntó). Esos sitios ven la dirección IP del teléfono como cualquier página web. Las **imágenes** de las tarjetas se descargan de los sitios permitidos (BBC, Wikimedia Commons, Smithsonian, NASA), solo por https, se guardan solo en memoria y nunca en el disco. Los titulares y datos públicos descargados se mandan a Gemini solo para que juzgue si son adecuados. **Tocar "Leer la noticia" o "Buscar en Google" abre el navegador del teléfono**: desde ahí la persona ya está en esa página y Aidly no ve lo que hace | Wikipedia, BBC, Open-Meteo y Gemini (Firebase AI Logic) | Sin nube no se contacta a ninguno | `FuentesWeb.kt`, `FeedTarjetas.kt` |

| F18 | **Lo que me gusta leer o ver** (frases libres que la persona dice) | La frase se guarda cifrada solo en el teléfono (hasta 8, de máximo 40 caracteres; se rechazan correos y teléfonos). Con la nube encendida, esas frases viajan a Gemini dentro de la petición de tarjetas (sin nombre ni ubicación); además se usan en el teléfono para ordenar los titulares | Gemini (Firebase AI Logic), con la nube encendida | Sin nube solo se usan en el teléfono | `Intereses.kt`, `ParaTiActivity.kt` |

No sale nunca del teléfono: el texto de los mensajes y correos que Aidly lee en voz alta (viene de las notificaciones), la lista de recordatorios, los favoritos, el número de quien llama, el contenido del calendario (solo se lee o se escribe en el calendario del propio teléfono).

## 3. Lo que se guarda en el teléfono

| Almacén | Contenido | Retención | Dónde |
|---|---|---|---|
| `bandeja` | Mensajes y correos recibidos como notificación, para leerlos | 48 h; se marca como leído | `Bandeja.kt` |
| `recordatorios` | Avisos programados e historial (medicinas, citas, check-in) | Historial 30 días / 100 entradas | `Recordatorios.kt` |
| `favoritos` | Hasta 4 contactos (nombre y número) | Hasta que se quiten | `Datos.kt` |
| `circulo` | Código familiar y qué se comparte | Hasta que se deje de compartir | `Circulo.kt` |
| `ajustes` | Idioma, movimiento, nombre, bienvenida hecha | — | `Datos.kt`, `Idioma.kt` |
| `privacidad` | Elección de nube y aceptación de la cámara del modo dock | — | `Privacidad.kt` |
| `intereses` | Temas que le gustan, reacciones ("me interesa / no me interesa"), títulos ya mostrados y si aceptó que Aidly le ofrezca cosas (cifrado) | Hasta que se cambien o se borre todo | `Intereses.kt` |
| `feed` | Últimas tarjetas de "Para ti" (cifrado) | 6 h; se reemplazan | `FeedTarjetas.kt` |

- Almacenamiento privado de la app; `allowBackup=false`: **no hay respaldo en la nube de Android** ni transferencia automática (AndroidManifest).
- **Cifrado en reposo** (`Cifrado.kt`): `bandeja`, `recordatorios` (avisos e historial de medicinas), `favoritos`, y el código, nombre y coordenadas de SOS del `circulo` se guardan con AES-256-GCM y una clave del Android Keystore que no sale del aparato. Protege contra la copia de los archivos de la app; no contra alguien con el teléfono desbloqueado ni contra root ejecutando la propia app. Los datos antiguos sin cifrar se cifran solos al guardarse de nuevo. `ajustes` y `privacidad` (idioma, movimiento, elección de nube) no se cifran porque no son sensibles.
- Registro de depuración (`SpikeLog`): en la versión de desarrollo escribe frases y respuestas en logcat; en la versión final (release) **no escribe nada**.

## 4. Control de la persona
- **Apagar la nube**: Ajustes > Privacidad.
- **Dejar de compartir con la familia**: en "Mi familia"; borra el documento de la nube y los ajustes locales.
- **Borrar todos mis datos**: Ajustes > Privacidad (dos toques). Cancela los avisos, borra todos los almacenes de la tabla anterior, cierra y elimina el usuario anónimo de Firebase, borra el documento del círculo si hay internet y vuelve a la pantalla de inicio. Los permisos del sistema (mensajes, calendario, contactos…) se quitan desde los ajustes de Android.
- **Permisos**: se piden uno por uno al usarlos; la app funciona (con menos funciones) sin ellos.

## 5. Pendientes y puntos a verificar (honestidad ante todo)
1. **Cifrado en reposo**: hecho para los almacenes sensibles (ver §3). Pendiente: probarlo en el A23 y con una restauración de datos (si se pierde la clave, los datos cifrados cuentan como vacíos y no hay fallo).
2. **Gemini Developer API**: confirmar en las condiciones vigentes si el nivel gratuito permite usar los datos para mejorar los productos de Google, y pasar a un proyecto con facturación si hace falta. No afirmar nada en Devpost hasta confirmarlo.
3. **App Check en producción**: cambiar el proveedor de depuración por Play Integrity.
4. **Retención en Firebase**: Firestore guarda el documento mientras la familia esté activa; no hay copia propia. Confirmar la retención de registros de Firebase en la consola.
5. **Ubicación**: solo se lee al sincronizar si la persona la compartió, o en un SOS. No hay rastreo continuo.
6. **Telemetría propia**: ninguna (sin analíticas, sin anuncios, sin Crashlytics). Firebase puede registrar datos técnicos propios del servicio (IP, dispositivo) al usar App Check, Auth, Firestore y AI Logic.
