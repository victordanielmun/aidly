# Validación de las dos vistas de calendario del diseño (19-nov)

Diseño revisado: lienzo "Aidly – Rediseño" (tableros **Recordatorios y calendario** y **Detalle de recordatorio**).
Validado contra el modelo de datos real de la app, las reglas de accesibilidad del proyecto y pruebas en el emulador
(Android 15, pantalla vertical 1080×2400; español e inglés; letra normal y al 200 %).

## Vista 1: Recordatorios y calendario

| Elemento del diseño | Veredicto | Qué se hizo |
|---|---|---|
| Tira de la semana con puntos de "hay algo" | **Bien**, pero faltaba lo esencial | Se añadieron flechas de semana anterior/siguiente, mes visible y "Ir a hoy". Sin eso no se podía llegar a una cita de la semana próxima |
| Inicial de un solo carácter por día ("L M X J V S D") | **Ajustar**: "X" para miércoles confunde y no existe en inglés | Abreviatura de tres letras según el idioma (Lun, Mar, Mié / Mon, Tue, Wed) |
| Primer día de la semana | **Faltaba** | Sale del JSON de idiomas (lunes en español, domingo en inglés) |
| Lista del día con hora grande, título, tipo y marca de hecho | **Bien** | Hecho. Tipos reales: Medicina, Recordatorio y Cita (el tipo "Familia" del diseño no existe en los datos) |
| Casilla de "hecho" en todas las filas | **Ajustar** | Solo en avisos que ya sonaron o ya pasaron (tienen registro); un aviso futuro no se puede "hacer" por adelantado |
| "SIGUIENTE" resaltado | **Bien** | Solo hoy, el primero pendiente |
| Citas del calendario del teléfono | **Faltaba el estado sin permiso** | Tarjeta "Para ver tus citas necesito tu permiso" con botón |
| Tocar una cita | El diseño la manda al detalle de recordatorio | **Corregido**: las citas abren el calendario del teléfono; el detalle solo es para avisos |
| "Leerme el día" | **Muy buena idea** para baja visión | Lee el día en el idioma de la app, con la hora hablada ("a las 8 de la mañana / at 8 AM") |
| "Añadir con mi voz" | **Bien** | Abre "Hablar con Aidly" |
| Avisos que se repiten | **No estaba pensado** | Los avisos diarios o "cada N horas" se expanden en cada día futuro; los días pasados muestran solo lo que sonó |
| Letra al 200 % | **No cabe tal cual**: la tira de 7 días aplasta las letras y los botones fijos dejan la lista en una franja | Con letra ≥ 130 % la tira se desplaza (y deja a la vista el día elegido) y toda la pantalla se desplaza junto, botones incluidos |
| Barras del sistema | **Faltaba** en el diseño | En Android 15 la app se dibuja bajo la barra de estado y la de navegación: se añadió el margen para **todas** las pantallas, no solo esta |

Contraste y tamaños: texto crema sobre fondo oscuro y tarjetas con ratios altos; botones de día ≥ 44 dp, casillas de 56 dp, filas ≥ 88 dp. Los textos de 15–16 sp ("SIGUIENTE", tipo del aviso) se mantienen
porque escalan con la letra del sistema.

## Vista 2: Detalle de recordatorio

| Elemento del diseño | Veredicto | Qué se hizo |
|---|---|---|
| Hora con flechas grandes (hora ±1, minutos ±5) | **Bien** (botones de 88×52) | Hecho. Se muestra además la hora hablada ("a las 4 de la tarde") porque el reloj de 24 h es menos natural para algunas personas. Los minutos que no son múltiplo de 5 (p. ej. 21:07 de "en 20 minutos") se redondean al primer toque |
| "¿Qué te recuerdo?" con botón de voz | **Bien** | Hecho: abre "Hablar" (el texto no se edita escribiendo) |
| "¿Cada cuánto?": solo hoy / todos los días / **entre semana** | **Parcial** | Hecho: "Solo una vez" y "Todos los días", más la opción real que ya tenga el aviso ("Cada 8 horas"). **"Entre semana" no existe en el modelo** (haría falta un campo de días) y quedó fuera |
| "Avisar a mi familia si no lo marco · Laura recibirá un mensaje" | **No se puede prometer**: la familia no recibe mensajes, ve una página; no hay notificaciones push | **No implementado.** Si se quiere, debe decir "tu familia lo verá en su página" y es por aviso, con el círculo activo. Se deja como pendiente de decisión |
| Falta de fecha | **Faltaba**: un aviso de otro día solo muestra la hora | Pendiente: elegir el día en el detalle |
| "Guardar" y "Volver" | **Ajustar** | "Guardar" aplica los cambios; "Volver" los descarta |
| "Borrar recordatorio" | **Faltaba confirmar** | Se pide un segundo toque ("Toca otra vez para borrar") |

## Hallazgo extra de la prueba
El analizador de voz leía "a las 12:30" como 00:30. Corregido (las 12 son mediodía salvo que se diga "de la noche/madrugada"), en español y en inglés.

## Pendiente
Entre semana; elegir fecha; aviso a la familia por recordatorio (decisión de producto); elegir minutos de uno en uno; prueba con TalkBack en el A23.
