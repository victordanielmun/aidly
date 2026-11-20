"""Transcribe cada audio con el ASR del servidor y mide el parecido con la frase original."""
import difflib
import json
import os
import sys
import unicodedata
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tts_cliente as t

CARPETA = r"c:\Users\USUARIO\Desktop\automatizaciones\Aidly\pruebas\audios"


def plano(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return " ".join("".join(c if c.isalnum() or c == " " else " " for c in s).split())


def subir(ruta):
    """Sube el archivo al Gradio (multipart) y devuelve el FileData que espera la API."""
    limite = "----aidly" + uuid.uuid4().hex
    with open(ruta, "rb") as f:
        contenido = f.read()
    cuerpo = (
        f"--{limite}\r\nContent-Disposition: form-data; name=\"files\"; filename=\"{os.path.basename(ruta)}\"\r\n"
        f"Content-Type: audio/wav\r\n\r\n"
    ).encode() + contenido + f"\r\n--{limite}--\r\n".encode()
    req = urllib.request.Request(
        f"{t.BASE}/upload", data=cuerpo, method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={limite}"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        ruta_servidor = json.loads(r.read())[0]
    return {"path": ruta_servidor, "meta": {"_type": "gradio.FileData"}}


def main():
    frases = json.load(open(os.path.join(CARPETA, "frases.json"), encoding="utf-8"))
    malos = []
    for nombre, esperado in frases.items():
        if not esperado:
            continue
        res = t.llamar("transcribe_audio", [subir(os.path.join(CARPETA, nombre + ".wav"))], timeout=300)
        texto = res[0] if isinstance(res[0], str) else json.dumps(res[0], ensure_ascii=False)
        r = difflib.SequenceMatcher(None, plano(esperado), plano(texto)).ratio()
        print(f"{r:4.2f}  {nombre:28s} esperado='{esperado}'  oido='{texto.strip()[:90]}'", flush=True)
        if r < 0.8:
            malos.append(nombre)
    print("\nPoco parecidos (<0.80):", malos or "ninguno")


if __name__ == "__main__":
    main()
