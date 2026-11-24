#!/usr/bin/env python3
"""Auditoria de traducciones de Aidly (espanol / ingles). Busca:
  1. Textos escritos a mano en el codigo (Text("..."), Toast, notificaciones, contentDescription) en lugar de strings.xml.
  2. Claves de values-en que son copia exacta del espanol (probable traduccion que falta).
  3. Desajustes en los JSON de config (idiomas.json, para_ti.json) entre es y en.
Sale con codigo 1 si hay hallazgos. Las pantallas de depuracion (MainActivity) y los registros (SpikeLog) se ignoran."""
import json, re, sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FUENTES = RAIZ / "app/src/main/java/com/aidly/app"
RES = RAIZ / "app/src/main/res"
CFG = RAIZ / "app/src/main/assets/config"
IGNORAR = {"MainActivity.kt", "SpikeLog.kt"}          # pantalla de pruebas tecnicas (solo depuracion)
PERMITIDOS = {"Aidly", "AIDLY", "OK", "Wi-Fi", "WiFi", "kWh", "BBC", "Wikipedia", "TikTok", "WhatsApp", "Google", "Gemini", "Nano"}
hallazgos = []

def hall(msg): hallazgos.append(msg)

# --- 1. textos a mano en el codigo ---
PATRONES = [
    r'\bText\(\s*"((?:[^"\x5c]|\x5c.)+)"',
    r'\btext\s*=\s*"((?:[^"\x5c]|\x5c.)+)"',
    r'Toast\.makeText\([^,]+,\s*"((?:[^"\x5c]|\x5c.)+)"',
    r'contentDescription\s*=\s*"((?:[^"\x5c]|\x5c.)+)"',
    r'setContentTitle\(\s*"((?:[^"\x5c]|\x5c.)+)"', r'setContentText\(\s*"((?:[^"\x5c]|\x5c.)+)"',
    r'\.label\s*=\s*"((?:[^"\x5c]|\x5c.)+)"',
]
def parece_texto(t: str) -> bool:
    t = t.strip()
    if t in PERMITIDOS or len(t) < 3: return False
    if "$" in t and not re.search(r"[A-Za-zÁÉÍÓÚáéíóúñ]{4,}", re.sub(r"\$\{[^}]*\}|\$\w+", "", t)): return False   # solo plantillas
    sin_plantilla = re.sub(r"\$\{[^}]*\}|\$\w+", "", t)
    return bool(re.search(r"[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,}", sin_plantilla)) and not re.fullmatch(r"[\d\W_]+", sin_plantilla)
for f in sorted(FUENTES.glob("*.kt")):
    if f.name in IGNORAR: continue
    for n, linea in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        if linea.strip().startswith(("//", "*", "/*")) or "SpikeLog" in linea or "Log." in linea: continue
        for pat in PATRONES:
            for m in re.finditer(pat, linea):
                if parece_texto(m.group(1)):
                    hall(f"texto a mano en {f.name}:{n}: \"{m.group(1)[:60]}\"")
    # espanol suelto en literales (tildes, ¿, ¡) fuera de comentarios y registros
    lineas = f.read_text(encoding="utf-8").splitlines()
    rama_idioma = re.compile(r'if \(en\)|en *(==|->)|== "en"|idioma == "en"|\(en\)|else if \(en|es *->')
    for n, linea in enumerate(lineas, 1):
        c = linea.strip()
        if c.startswith(("//", "*", "/*")) or "SpikeLog" in c or "Log." in c or "Regex(" in c or "r(\"" in c: continue
        if "i18n-ok" in linea: continue
        if any(rama_idioma.search(l) for l in lineas[max(0, n - 8):n + 8]): continue   # tiene su rama en ingles
        for m in re.finditer(r'"((?:[^"\x5c]|\x5c.)*[áéíóúñ¿¡][^"]*)"', c):
            if f.name in ("Lexico.kt", "Idioma.kt"): continue
            hall(f"espanol en un literal de {f.name}:{n}: \"{m.group(1)[:60]}\"")

# --- 2. values-en igual a values ---
def leer(p: Path):
    d = {}
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', p.read_text(encoding="utf-8"), flags=re.S): d[m.group(1)] = m.group(2).strip()
    return d
es, en = leer(RES / "values/strings.xml"), leer(RES / "values-en/strings.xml")
for k in sorted(set(es) ^ set(en)): hall(f"clave sin par en {'en' if k in es else 'es'}: {k}")
for k, v in es.items():
    if k in en and v == en[k] and len(re.sub(r"%\d\$\w|\W", "", v)) > 8 and not re.fullmatch(r"[\d\W]+", v):
        hall(f"values-en/{k} es copia exacta del espanol: {v[:60]}")

# --- 3. JSON de config ---
def claves(o, pref=""):
    if isinstance(o, dict):
        for k, v in o.items(): yield from claves(v, pref + "/" + k)
    elif isinstance(o, list):
        # las listas de palabras/frases pueden tener distinto largo en cada idioma; solo se compara la estructura de las listas de objetos
        if o and all(isinstance(v, dict) for v in o):
            yield pref + f"[{len(o)}]"
            for i, v in enumerate(o): yield from claves(v, pref + f"[{i}]")
        else: yield pref + "[]"
    else: yield pref
idi = json.load(open(CFG / "idiomas.json", encoding="utf-8"))["idiomas"]
if "es" in idi and "en" in idi:
    a, b = set(claves(idi["es"])), set(claves(idi["en"]))
    for k in sorted(a ^ b): hall(f"idiomas.json: {k} no esta en {'en' if k in a else 'es'}")
pt = json.load(open(CFG / "para_ti.json", encoding="utf-8"))
for idioma in ("es", "en"):
    if idioma not in pt: hall(f"para_ti.json: falta {idioma}")
if "es" in pt and "en" in pt:
    ta = sorted((t["tema"]) for t in pt["es"]); tb = sorted((t["tema"]) for t in pt["en"])
    if ta != tb: hall(f"para_ti.json: las tarjetas incluidas no coinciden por tema (es={len(ta)}, en={len(tb)})")

for h in hallazgos: print("HALLAZGO:", h)
print(f"\n{len(hallazgos)} hallazgo(s).")
sys.exit(1 if hallazgos else 0)
