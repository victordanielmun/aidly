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
