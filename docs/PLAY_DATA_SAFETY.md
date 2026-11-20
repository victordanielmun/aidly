# Aidly: borrador de la declaración "Seguridad de los datos" (Google Play)

BORRADOR de trabajo (19-nov-2026), basado en lo que el código hace hoy (ver [PRIVACY_DATA_FLOW.md](PRIVACY_DATA_FLOW.md)).
Formato inspirado en el `PLAY_DATA_SAFETY.md` de fun-phone. **No es asesoría legal ni se ha enviado a Play**: las filas marcadas
"VERIFICAR" dependen de condiciones de Google que hay que confirmar antes de publicar. Aidly aún no está en Play (es para el hackathon).

## 1. Preguntas generales
| Pregunta de Play | Respuesta propuesta |
|---|---|
| ¿La app recopila o comparte datos del usuario? | **Sí** (solo con la nube encendida o si la persona activa "Mi familia") |
| ¿Todos los datos se cifran en tránsito? | **Sí** (HTTPS/TLS de Firebase y Gemini) |
| ¿Se puede pedir el borrado de datos? | **Sí**: en la app, Ajustes > Privacidad > "Borrar todos mis datos" y "Dejar de compartir" en Mi familia. La app no tiene cuentas de usuario |
| Anuncios | **No** |
| Análisis o telemetría propia | **No** (ni analíticas ni informes de fallos) |

## 2. Tipos de datos
Todo es **opcional** (la persona puede apagar la nube y no activar "Mi familia"). "Compartido" significa entregado a un tercero que
no actúa en nombre de Aidly; Google (Gemini/Firebase) procesa por encargo de Aidly, lo que Play suele tratar como proveedor de servicios
(**VERIFICAR** con las condiciones de la API usada).

| Tipo de dato (Play) | Qué es en Aidly | ¿Recopilado? | Finalidad | Compartido | Opcional |
|---|---|---|---|---|---|
| Audio > Grabaciones de voz | Frases que la persona dice a Aidly (hasta 10 s) | Sí, enviado a Gemini; no se guarda en Aidly | Funcionalidad de la app | No (proveedor de servicios) | Sí (nube apagable) |
| Fotos y videos > Fotos | Foto de una medicina o un papel | Sí, enviada a Gemini; no se guarda en el teléfono ni en Aidly | Funcionalidad de la app | No (proveedor de servicios) | Sí |
| Mensajes > Otros mensajes en la app | Texto de preguntas y frases dichas a Aidly | Sí, enviado a Gemini | Funcionalidad de la app | No (proveedor de servicios) | Sí |
| Ubicación > Ubicación aproximada y precisa | Solo si se comparte con la familia o en un SOS | Sí (Firestore) | Funcionalidad de la app | No (la ve quien tenga el código) | Sí |
| Información personal > Nombre | Cómo quiere que la llamen (se muestra a la familia) | Sí, solo con "Mi familia" | Funcionalidad de la app | Con quien tenga el código | Sí |
| Salud y estado físico > Información de salud | Estado de los avisos de medicinas (texto y si se tomó) | Sí, solo si se activa "medicinas" en Mi familia | Funcionalidad de la app | Con quien tenga el código | Sí |
| Actividad en la app | Hora de la última vez que se usó el teléfono | Sí, solo con "Mi familia" | Funcionalidad de la app | Con quien tenga el código | Sí |
| ID de dispositivo u otros ID | ID de usuario anónimo de Firebase Auth; token de App Check | Sí | Seguridad y prevención de fraude | No | No (necesario para Mi familia/IA) |

## 3. Datos a los que la app accede pero NO recopila
- Contactos (READ_CONTACTS): solo para decir el nombre de quien llama y llamar por nombre; se procesan en el teléfono.
- Calendario: se lee y escribe en el calendario del propio teléfono.
- Notificaciones de mensajería (acceso a notificaciones): el texto se lee en voz alta y se guarda hasta 48 h cifrado en el teléfono.
- Número de quien llama (rol de filtrado de llamadas): se compara en el teléfono y no sale.

## 4. Permisos y justificación (para la ficha)
| Permiso | Por qué |
|---|---|
| RECORD_AUDIO | Escuchar a la persona |
| CAMERA | Fotografiar medicinas y papeles |
| READ_CONTACTS | Decir quién llama y llamar por nombre |
| READ/WRITE_CALENDAR | Leer y crear citas por voz |
| ACCESS_COARSE/FINE_LOCATION | Compartir ubicación con la familia y SOS (solo si se activa) |
| POST_NOTIFICATIONS, USE_EXACT_ALARM, USE_FULL_SCREEN_INTENT | Avisos de medicinas y citas a la hora exacta |
| RECEIVE_BOOT_COMPLETED | Volver a programar los avisos al reiniciar |
| FOREGROUND_SERVICE(_MICROPHONE) | Compañero de voz para leer mensajes |
| Acceso a notificaciones (servicio) | Leer mensajes y correos en voz alta |
| Filtrado de llamadas (rol) | Decir quién llama; nunca bloquea |

Nota para Play: `USE_EXACT_ALARM` y `USE_FULL_SCREEN_INTENT` tienen requisitos de categoría de app (alarmas/recordatorios); revisar la política vigente antes de publicar.

## 5. Pendientes antes de enviar
1. Confirmar condiciones del nivel de la API de Gemini (uso de datos para mejorar productos) y si hace falta facturación.
2. Pasar App Check a Play Integrity.
3. Publicar `PRIVACY.md` en una URL pública (Play pide enlace a la política).
4. Revisar la categoría de las alarmas exactas y la intención de pantalla completa.
