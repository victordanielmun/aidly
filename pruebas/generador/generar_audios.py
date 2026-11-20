"""Genera los audios de prueba de Aidly con Qwen3-TTS (VoiceDesign) y los deja en 16 kHz mono WAV."""
import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tts_cliente as t

SALIDA = r"c:\Users\USUARIO\Desktop\automatizaciones\Aidly\pruebas\audios"
FFMPEG = r"C:\ffmpeg\bin\ffmpeg.exe"
VOZ = (
    "Una mujer mayor, de unos 70 años, hablando español de Colombia con acento paisa suave, "
    "voz cálida y pausada, con claridad, a volumen normal de conversación."
)
SEMILLA = 1234

# nombre_de_archivo -> frase que diria la persona
FRASES = {
    "01_leer_mensajes": "Léeme los mensajes.",
    "02_leer_correo": "Léeme el correo.",
    "03_recordatorio_medicina": "Recuérdame tomar la pastilla de la presión a las ocho de la noche.",
    "04_recordatorio_cada_8h": "Avísame que tome el antibiótico cada ocho horas.",
    "05_recordatorio_en_minutos": "Recuérdame en veinte minutos apagar la estufa.",
    "06_agendar_cita": "Agéndame cita con el doctor el martes a las diez de la mañana.",
    "07_que_tengo_hoy": "¿Qué tengo hoy?",
    "08_que_hora_es": "¿Qué hora es?",
    "09_llamar_hija": "Llama a mi hija.",
    "10_abrir_netflix": "Abre Netflix.",
    "11_musica": "Ponme música de Julio Iglesias.",
    "12_videos_cocina": "Quiero ver videos de cocina.",
    "13_analiza": "Analiza esto.",
    "14_auxilio": "Auxilio, me siento mal.",
    "15_estoy_bien": "Estoy bien.",
    "16_ya_me_la_tome": "Ya me tomé la pastilla.",
    "17_pregunta_presion": "¿Qué es la presión arterial?",
    "18_clima": "¿Cómo está el clima hoy en Bogotá?",
    "19_responder_si": "Sí.",
    "20_responder_no": "No.",
    "21_dictado_respuesta": "Hola mi amor, ya llegué a casa, no te preocupes.",
    "22_contesta_inline": "Contesta que ya voy para allá.",
    "23_med_para_que_sirve": "¿Para qué sirve esta medicina?",
    "24_med_con_comida": "¿La puedo tomar con el estómago vacío?",
    "25_med_dos_dosis": "Se me olvidó una pastilla, ¿me tomo dos hoy?",
    "26_silencio_ruido": None,  # sin voz: solo para probar el "no te escuché"
}


def main(solo=None):
    os.makedirs(SALIDA, exist_ok=True)
    print("Cargando modelo VoiceDesign 1.7B...", flush=True)
    try:
        print(json.dumps(t.llamar("load_model_manual", ["VoiceDesign", "1.7B"], timeout=900), ensure_ascii=False)[:200])
    except Exception as e:
        print("  (no se pudo cargar explicitamente, se intenta igual):", e)
    for nombre, frase in FRASES.items():
        if solo and nombre not in solo:
            continue
        destino16 = os.path.join(SALIDA, nombre + ".wav")
        if frase is None:
            # 3 s de ruido muy bajo, para probar "no te escuche"
            subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-f", "lavfi", "-i", "anoisesrc=d=3:c=pink:a=0.002",
                            "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", destino16], check=True)
            print("OK  ", nombre, "(ruido)")
            continue
        t0 = time.time()
        res = t.llamar("generate_voice_design", [frase, "Spanish", VOZ, SEMILLA], timeout=900)
        archivo = res[0]
        crudo = os.path.join(SALIDA, "_" + nombre + "_orig.wav")
        t.descargar(archivo, crudo)
        subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", crudo, "-ar", "16000", "-ac", "1",
                        "-c:a", "pcm_s16le", destino16], check=True)
        os.remove(crudo)
        print(f"OK   {nombre}  {time.time() - t0:.1f}s  {os.path.getsize(destino16) // 1024} KB  <- {frase}", flush=True)
    with open(os.path.join(SALIDA, "frases.json"), "w", encoding="utf-8") as f:
        json.dump({k: v for k, v in FRASES.items()}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main(set(sys.argv[1:]) or None)
