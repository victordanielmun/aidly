# Pruebas de voz sin micrófono

`audios/` tiene 26 frases sintéticas generadas con **Qwen3-TTS (VoiceDesign 1.7B)** en el servidor
el servidor Qwen3-TTS que indique la variable de entorno `QWEN_TTS_URL`, con una voz de mujer mayor colombiana (semilla 1234). Son WAV de 16 kHz mono.
`frases.json` guarda el texto de cada una.

## Correr la suite

1. Instala el APK debug en el emulador o teléfono y regístrale el token de App Check (si no, Gemini responde
   "token is invalid").
2. `adb devices` debe mostrar el dispositivo.
3. En PowerShell, desde la carpeta `Aidly`:

   ```powershell
   .\pruebas\correr_audios.ps1 -Serial emulator-5554
   .\pruebas\correr_audios.ps1 -Serial R58T41ZF1DN -Solo 14_auxilio,03_recordatorio_medicina
   ```

Para cada audio, Gemini lo transcribe, el agente elige una ruta y el script la compara con la esperada.
Sirve para regresiones del enrutador y de la transcripción; **no prueba el micrófono real** ni el ruido de un cuarto.

## Regenerar las voces

Los scripts de generación están en la sesión de trabajo (`generar_audios.py`, cliente Gradio `tts_cliente.py`).
Endpoint usado: `/generate_voice_design` (texto, idioma `Spanish`, descripción de la voz, semilla).
Nota: el reconocedor del propio servidor confunde el idioma en frases de 1-3 palabras; no lo uses para validar.
