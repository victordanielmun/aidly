"""Cliente minimo del Gradio de Qwen3-TTS (URL en la variable QWEN_TTS_URL, por defecto http://localhost:7860)."""
import json
import urllib.request

import os
BASE = os.environ.get("QWEN_TTS_URL", "http://localhost:7860").rstrip("/") + "/gradio_api"


def llamar(endpoint, datos, timeout=600):
    req = urllib.request.Request(
        f"{BASE}/call/{endpoint}", data=json.dumps({"data": datos}).encode(), method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        evento = json.loads(r.read())["event_id"]
    # El resultado llega como flujo SSE: "event: complete" + "data: [...]"
    with urllib.request.urlopen(f"{BASE}/call/{endpoint}/{evento}", timeout=timeout) as r:
        ultimo_evento, resultado = None, None
        for linea in r:
            linea = linea.decode("utf-8", "replace").rstrip("\n")
            if linea.startswith("event:"):
                ultimo_evento = linea[6:].strip()
            elif linea.startswith("data:") and ultimo_evento in ("complete", "error"):
                resultado = linea[5:].strip()
                if ultimo_evento == "error":
                    raise RuntimeError(f"{endpoint}: {resultado}")
                return json.loads(resultado)
    raise RuntimeError(f"{endpoint}: sin resultado")


def descargar(archivo, destino):
    """[archivo] es el dict de Gradio ({path, url, ...}); guarda el contenido en [destino]."""
    url = archivo.get("url") or f"{BASE}/file={archivo['path']}"
    with urllib.request.urlopen(url, timeout=120) as r, open(destino, "wb") as f:
        f.write(r.read())
    return destino
