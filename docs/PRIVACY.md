# Aidly: política de privacidad / Privacy policy

Versión de desarrollo (19-nov-2026). Detalle técnico y archivos de código en [PRIVACY_DATA_FLOW.md](PRIVACY_DATA_FLOW.md).

---

## Español

### En una frase
Aidly guarda tus cosas en tu teléfono y solo manda algo a la nube de Google cuando tú usas una función que lo necesita, y solo si aceptaste la nube.

### Qué se queda en tu teléfono
Tus recordatorios, tus favoritos (cifrados en tu teléfono), el número de quien te llama (solo para decirte su nombre), los mensajes y correos que Aidly te lee (se guardan hasta 48 horas), tus ajustes y tu elección de privacidad. No hay respaldo en la nube de Android. Aidly no tiene anuncios, ni cuentas de usuario, ni analíticas, ni informes de fallos.

### Qué va a la nube de Google (Gemini)
Solo cuando usas la función, y solo con la nube encendida:
- **Hablar con Aidly:** el audio de lo que dices.
- **Conversar y entender recordatorios:** el texto de lo que dices (primero se intenta en el teléfono con Gemini Nano, si tu teléfono lo tiene).
- **¿Qué dice aquí?:** la foto que tomas (se reduce de tamaño antes de enviarla) y, si preguntas algo sobre la medicina, tu pregunta y los datos impresos en la caja.
- **Buscar en internet:** tu pregunta.

Google procesa esos datos para responderte. Aidly no los guarda en sus propios servidores.

### Para ti (opcional)
Si eliges tus gustos en «Para ti», Aidly te muestra cosas que te pueden interesar y puede ofrecértelas por voz en el modo dock, solo si lo aceptas. Tus gustos se guardan cifrados en tu teléfono. Con la nube encendida, Aidly manda a Google la lista de temas elegidos y los títulos que ya viste (nunca tu nombre ni tu ubicación) para buscar tarjetas nuevas; con la nube apagada solo ves las que trae la app. Las tarjetas de la web (Wikipedia y titulares de la BBC) llevan el texto de su fuente y siempre la nombran; las que resume la IA lo dicen. Esos sitios ven la dirección IP de tu teléfono, como cualquier página, y solo se consultan con la nube encendida. Para el clima se manda a Open-Meteo el nombre de la ciudad por la que preguntas. Las imágenes de las tarjetas se descargan de esos sitios y no se guardan en el disco. Las frases que dices sobre lo que te gusta leer o ver se guardan cifradas en tu teléfono (hasta 8) y, con la nube encendida, se mandan a Google junto con los temas para buscar cosas parecidas; nunca con tu nombre ni tu ubicación. Si tocas «Leer la noticia» o «Buscar en Google», se abre el navegador de tu teléfono.

### Ayuda con otras apps (opcional)
Si lo enciendes en *Ajustes > Accesibilidad*, Aidly muestra su botón flotante sobre las demás aplicaciones y, **solo cuando se lo pides** («toca Buscar», «atrás», «baja la pantalla»), toca un botón por su texto, va atrás o al inicio y desplaza la pantalla. No recibe lo que escribes ni lo que ocurre en pantalla, no guarda ni envía nada de lo que ve y nunca toca nada por su cuenta. Está apagado hasta que lo enciendes y lo puedes apagar cuando quieras. Detalle: [ACCESIBILIDAD_DECLARACION.md](ACCESIBILIDAD_DECLARACION.md).

### Cámara del modo dock (opcional)
Si activas el modo dock y aceptas, la cámara frontal del teléfono sirve para saber si estás frente a Aidly y para que la base gire hacia ti. Las imágenes se procesan **en tu teléfono**: no se muestran, no se guardan y no se envían. Si después de conversar dejo de verte, te preguntaré en voz alta si estás bien. No detecta caídas ni emergencias. Puedes pausarla cuando quieras.

### Tu familia (opcional)
Si activas «Mi familia», se crea un código aleatorio y se guarda en Google Firebase solo lo que tú elijas compartir: nombre, última actividad, estado de las medicinas, ubicación y cómo te sientes. Quien tenga el código ve esos datos. Si pides ayuda (SOS) se comparte la hora y, si hay permiso, la ubicación. Al dejar de compartir se borra todo en la nube.

### Voz sin la nube
Con la nube apagada, Aidly escucha con el reconocedor de voz de tu teléfono. Según el modelo, Android puede enviar ese audio al servicio de voz de Google; Aidly no lo controla.

### Tus controles
- **Ajustes > Privacidad:** encender o apagar la nube.
- **Dejar de compartir** en «Mi familia».
- **Borrar todos mis datos:** borra todo lo guardado, el círculo familiar y el usuario anónimo de Firebase.
- Los permisos (micrófono, cámara, contactos, calendario, ubicación, mensajes) se piden al usarlos y se quitan en los ajustes de Android.

### Lo que Aidly no hace
No vende datos, no muestra anuncios, no mide tu uso, no te rastrea y no manda nada a la nube si la apagas (salvo lo que tú actives en «Mi familia»).

### Cosas por confirmar antes de publicar
Condiciones de uso de datos del nivel de Gemini que usamos y Play Integrity en producción (ver el documento técnico).

---

## English

### In one sentence
Aidly keeps your things on your phone and only sends something to Google's cloud when you use a feature that needs it, and only if you accepted the cloud.

### What stays on your phone
Your reminders, your favorites, the number of whoever calls you (only to say their name), the messages and emails Aidly reads to you (kept up to 48 hours), your settings and your privacy choice. There is no Android cloud backup. Aidly has no ads, no user accounts, no analytics and no crash reports.

### What goes to Google's cloud (Gemini)
Only when you use the feature, and only with the cloud turned on:
- **Talk to Aidly:** the audio of what you say.
- **Conversation and understanding reminders:** the text of what you say (it is tried on the phone first with Gemini Nano, if your phone has it).
- **What does this say?:** the photo you take (resized before sending) and, if you ask about the medicine, your question and the data printed on the box.
- **Search the internet:** your question.

Google processes this data to answer you. Aidly does not store it on its own servers.

### For you (optional)
If you choose your interests in "For you", Aidly shows things you may enjoy and, only if you agree, can offer them aloud in dock mode. Your interests are stored encrypted on your phone. With the cloud on, Aidly sends Google the list of chosen topics and the titles you have already seen (never your name or location) to look for new cards; with the cloud off you only see the ones that ship with the app. Web cards (Wikipedia and BBC headlines) carry their source's text and always name it; those summarised by AI say so. Those sites see your phone's IP address, like any web page, and are only contacted with the cloud on. For weather, the name of the city you ask about is sent to Open-Meteo. Card images are downloaded from those sites and never saved to disk. The phrases you say about what you like to read or watch are stored encrypted on your phone (up to 8) and, with the cloud on, sent to Google together with the topics to look for similar things; never with your name or location. If you tap "Read the article" or "Search on Google", your phone's browser opens.

### Help with other apps (optional)
If you turn it on in *Settings > Accessibility*, Aidly shows its floating button over other apps and, **only when you ask** ("tap Search", "go back", "scroll down"), taps a button by its text, goes back or home and scrolls the screen. It does not receive what you type or what happens on screen, does not store or send anything it sees, and never taps anything on its own. It is off until you turn it on and you can turn it off any time. Details: [ACCESIBILIDAD_DECLARACION.md](ACCESIBILIDAD_DECLARACION.md).

### Dock-mode camera (optional)
If you turn on dock mode and accept, the phone's front camera is used to know whether you are in front of Aidly and so the base can turn toward you. Images are processed **on your phone**: they are not shown, saved or sent. If I stop seeing you after we talk, I will ask aloud whether you are okay. It does not detect falls or emergencies. You can pause it any time.

### Your family (optional)
If you turn on "My family", a random code is created and only what you choose to share is stored in Google Firebase: name, last activity, medicine status, location and how you feel. Whoever has the code sees that data. If you ask for help (SOS) the time and, with permission, the location are shared. When you stop sharing, everything is deleted from the cloud.

### Voice without the cloud
With the cloud off, Aidly listens with your phone's speech recognizer. Depending on the model, Android may send that audio to Google's speech service; Aidly does not control this.

### Your controls
- **Settings > Privacy:** turn the cloud on or off.
- **Stop sharing** in "My family".
- **Delete all my data:** deletes everything stored, the family circle and the anonymous Firebase user.
- Permissions (microphone, camera, contacts, calendar, location, messages) are requested when used and can be removed in Android settings.

### What Aidly does not do
It does not sell data, show ads, measure your use or track you, and it sends nothing to the cloud if you turn it off (except what you turn on in "My family").

### To confirm before publishing
Data-use terms of the Gemini tier we use and Play Integrity in production (see the technical document).
