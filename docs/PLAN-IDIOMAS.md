# Plan de idiomas y configuración inicial (es / en)

Estado al 19-nov-2026: la app está **solo en español** (textos escritos en el código, voz fija `es-MX`, comandos y
mensajes a Gemini en español). Este plan define cómo agregar inglés sin reescribir la app, con un archivo de
configuración por idioma.

> **Estado 19-nov (noche), nivel B elegido (respaldo en, países pendientes con 112):**
> - Hecho: **Fase 1** (base e selector), **Fase 2** (las 11 pantallas, la cara, los widgets con su vista previa y las notificaciones de avisos, 177 textos en alues y alues-en) y **Fase 5** (página de la familia ES/EN, 17/17 pruebas de reglas). Los textos que Aidly dice en voz alta siguen en español (nivel B).
> - Hecho también: **Fase 3** (`BienvenidaActivity`: 7 pasos generados desde `bienvenida.pasos` del JSON; aplica `inicial` y `paises`: hora y valores del círculo, tiempos de recordatorios, aviso previo de citas, repeticiones de voz de la alarma, número de emergencias y prefijo; "Restablecer valores del idioma" y "Ver la bienvenida otra vez" en Ajustes).
> - Falta: **Fase 6** (revisión en el A23, letra al 200 % en inglés, TalkBack) y, si se decide, el nivel C.
> - Comprobación: `python pruebas/validar_textos.py` (mismas claves y marcadores en es/en) y `python pruebas/validar_idiomas.py`.

**Archivos nuevos (ya creados, aún no conectados al código):**
- `app/src/main/assets/config/idiomas.json`: opciones de idioma y configuración inicial por idioma.
- `pruebas/validar_idiomas.py`: comprueba que todos los idiomas tengan la misma estructura (correrlo antes de cada entrega).

## 1. Qué contiene `idiomas.json`

| Sección | Para qué sirve |
|---|---|
| `predeterminado`, `resolucion` | Cómo se elige el idioma: preferencia del usuario → idioma del sistema → país del SIM → predeterminado. Mapa de variantes (`es-CO`, `en-GB`…) a `es`/`en`. Idioma de respaldo (`en`) si el sistema está en otro. |
| `idiomas.<id>.voz` | Voz de síntesis (locales, velocidad), cadena de reconocimiento, idioma que se le dice a Gemini, palabras de sí / no, "mal oídos" del sí, frases de contexto del transcriptor. |
| `idiomas.<id>.formato` | Patrones de fecha y hora (pantalla y voz), periodos del día ("de la noche" / "at night"), días relativos, primer día de la semana. |
| `idiomas.<id>.saludos` | "Buenos días / tardes / noches" ↔ "Good morning / afternoon / evening". |
| `idiomas.<id>.inicial` | **Configuración inicial**: check-in (hora, pregunta hablada), tiempos de recordatorios, qué se comparte por defecto en el círculo, compañero de mensajes, accesibilidad, máximo de favoritos, saludo de arranque. |
| `idiomas.<id>.bienvenida.pasos` | Pasos del primer arranque (idioma → nombre → micrófono y avisos → mensajes → calendario y contactos → familia → prueba), con los permisos que pide cada uno. |
| `idiomas.<id>.ejemplos` | Frases de ejemplo para mostrar ("Prueba diciendo…"). |
| `idiomas.<id>.comandos`, `apps` | Palabras clave de cada intención del agente (leer mensajes, recordar, agendar, llamar, SOS…) y nombres de apps. |
| `idiomas.<id>.modelo` | Instrucción de idioma y avisos fijos que se anexan a los mensajes a Gemini (medicina, estafa). |
| `idiomas.<id>.web_familia` | Idioma y título de la página de la familia. |
| `paises` | Por ISO: número de emergencias, prefijo telefónico, idioma sugerido, zona horaria, unidad de temperatura, moneda. `*` es el comodín (112). |
| `paises_sin_verificar` | Países que no se incluyen hasta confirmar su número de emergencias (CL, BR, GT, NI, BO). |

**Regla de diseño:** el JSON guarda *opciones y valores*; los **textos de pantalla** (≈300) van en
`res/values/strings.xml` (es) y `res/values-en/strings.xml` (en), que Android ya elige solo según el idioma.
Así no se duplica nada y las traducciones se revisan con las herramientas normales de Android.

## 2. Flujo en la app

**Primer arranque**
1. Se detecta el idioma (orden de `resolucion`) y se muestra el primer paso con ese idioma ya elegido.
2. Al confirmar, se **aplican los valores de `inicial` de ese idioma**: hora del check-in, qué se comparte por
   defecto, tiempos de recordatorios, accesibilidad.
3. Con el país del SIM se aplican `paises[<ISO>]`: número de emergencias, prefijo para WhatsApp, zona horaria.
4. Se recorren los `bienvenida.pasos`, cada uno pidiendo solo sus permisos; todo lo no obligatorio se puede saltar.

**Cambio de idioma en Ajustes** (`cambiar_en_caliente`)
Se guarda la preferencia y se ejecutan las acciones de `al_cambiar`: recrear actividades, reiniciar la voz,
reprogramar la alarma del check-in (cambia el texto hablado) y actualizar los widgets. **No** se reinician los
valores ya personalizados (hora de check-in, favoritos, qué se comparte): `inicial` solo se aplica la primera vez
o al elegir "Restablecer".

## 3. Dónde está hoy cada cosa y a dónde va

| Hoy (en el código) | Pasa a |
|---|---|
| `Emergencia.NUMEROS`, `Prefijo.POR_PAIS` (`Emergencia.kt`, `Datos.kt`) | `paises` |
| `Locale("es","MX")` en `Voz.kt`, `AidlyService.kt` | `voz.tts.locales_preferidos` |
| `LANGS = [null,"es-MX","es-419","es-ES"]` (`AidlyService.kt`) | `voz.stt.cadena_idiomas` |
| `SI`, `NO`, `SI_MAL_OIDO` (`AidlyService.kt`) | `voz.stt.palabras_si / palabras_no / si_mal_oido` |
| Frases de contexto del prompt (`GeminiStt.kt`) | `voz.stt.frases_contexto` + `voz.stt.gemini_idioma` |
| Saludo y fecha (`HomeActivity.kt`, `Locale("es","CO")`) | `saludos`, `formato.fecha_larga` |
| Hora hablada "a las 8 de la noche" (`Recordatorios.horaHablada`) | `formato.hora_voz` |
| Regex del agente (`Agente.kt`, ≈25 patrones) | `comandos` y `apps` (el código arma los patrones) |
| `ParserLocal.kt` (números, "mañana", "cada N horas") | Tabla propia por idioma (ver fase 4) |
| Prompts a Gemini (`AsistenteVoz`, `Interprete`, `MedicinaValidador`, `AnalizaValidador`, `Busqueda`) | Plantilla con `modelo.instruccion_idioma`, `aviso_*` |
| Textos de pantalla en Kotlin | `strings.xml` (es y en) |
| Valores por defecto sueltos (hora 9:00, 10 min, 30 min…) | `inicial` |
| Página de la familia (`familia/index.html`) | Tabla de textos es/en + selector, según `web_familia` |

## 4. Fases

### Fase 1 · Base (0,5 día)
- `Idioma.kt`: lee `idiomas.json`, resuelve el idioma, expone `Idioma.actual`, `Idioma.pais`, `Idioma.config`.
- Preferencia guardada (`Preferencias`), `AppCompatDelegate.setApplicationLocales` (o `LocaleManager`) para el cambio en caliente.
- Pantalla "Idioma" en Ajustes y paso 1 de la bienvenida.
- **Hecho cuando:** cambiar entre ES y EN en Ajustes recrea la pantalla y recuerda la elección.

### Fase 2 · Textos de pantalla (1 día)
- Extraer ≈300 textos a `strings.xml`; crear `values-en/strings.xml`.
- Cuidar plurales y texto con variables (`%1$s`); fechas con los patrones de `formato`.
- **Hecho cuando:** ninguna pantalla muestra texto en español con el idioma en inglés (revisión pantalla por pantalla con capturas).

### Fase 3 · Configuración inicial y bienvenida (1 día)
- Pantalla de bienvenida generada desde `bienvenida.pasos` (mismo componente para todos los pasos).
- Aplicar `inicial` y `paises` al terminar; botón "Restablecer valores del idioma" en Ajustes.
- **Hecho cuando:** instalar en limpio con el teléfono en inglés deja check-in 09:00, textos en inglés y número de emergencias del país del SIM.

### Fase 4 · Voz, comandos y modelo (1,5 días) — nivel C
- Voz: TTS y reconocimiento con los locales del idioma; `gemini_idioma` en el transcriptor.
- Agente: construir los patrones desde `comandos`/`apps`; `ParserLocal` con tabla por idioma (en inglés: "at 8 pm", "in 20 minutes", "every 8 hours", "tomorrow").
- Prompts a Gemini en el idioma elegido, conservando los avisos de seguridad (`aviso_medicina`, `aviso_estafa`).
- **Hecho cuando:** la suite de audios pasa en inglés (hay que generar los 26 audios en inglés con Qwen3-TTS) con la misma tasa que en español.

### Fase 5 · Página de la familia (0,5 día)
- Tabla de textos es/en en `familia/index.html`, selector ES/EN y `lang` del documento; idioma inicial según el navegador.
- El teléfono guarda en Firestore el idioma de la persona (campo `idioma`) para que la página lo use por defecto.
- **Hecho cuando:** `https://aidly-d108b.web.app/#demo` se ve igual en ambos idiomas.

### Fase 6 · Calidad (0,5 día)
- `python pruebas/validar_idiomas.py` sin problemas; revisión de textos largos (el inglés ocupa distinto ancho que el español).
- Pruebas con letra al 200 % en ambos idiomas; capturas para Devpost en inglés.

## 5. Niveles y recomendación

| Nivel | Incluye | Fases | Esfuerzo |
|---|---|---|---|
| **A** | Solo subtítulos en el video (estado actual) | — | 0 |
| **B** | Interfaz + configuración inicial + página de la familia en inglés; voz y comandos siguen en español | 1, 2, 3, 5, 6 | ≈3,5 días |
| **C** | B + voz, comandos y mensajes a Gemini en inglés | 1-6 | ≈5 días |

**Recomendación: B primero, con la Fase 1 y la 5 antes que nada** (la página de la familia es lo único que los jueces
abren sin instalar la app). C solo si sobra tiempo tras el video y Devpost.

## 6. Riesgos
- **Textos más largos en inglés o alemán de botones fijos:** las alturas ya son mínimas y crecen; revisar el menú a 200 %.
- **Doble voz:** con nivel B el sintetizador sigue en español. Si la interfaz está en inglés, la voz debería ser inglesa
  o la mezcla confunde. Por eso, en B, el selector avisa: "La voz de Aidly sigue en español" (o se hace C).
- **El reconocimiento en inglés con acento hispano** puede fallar más; probar con voces de la suite.
- **Mantener sincronizado el JSON:** cualquier clave nueva debe existir en todos los idiomas; el validador lo exige.

## 7. Decisiones que necesito
1. **Nivel:** ¿B (recomendado) o C?
2. **Idioma de respaldo** para idiomas no soportados: `en` (como está) o `es`.
3. **Inglés de Estados Unidos o neutro:** el JSON usa `en-US` primero y `en-GB` de respaldo. ¿Cambia el orden?
4. **Países pendientes** (CL, BR, GT, NI, BO): ¿los verifico y los agrego, o quedan con 112?
