"""Valida los textos de pantalla: mismas claves en values (es) y values-en (en), y que todo R.string.X del codigo exista.

Uso:  python pruebas/validar_textos.py     (sale con codigo 1 si hay problemas)
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main")
RES = os.path.join(RAIZ, "res")
problemas = []


def falla(msg):
    problemas.append(msg)
    print("  FALLA:", msg)


def cargar(ruta):
    arbol = ET.parse(ruta)
    return {s.attrib["name"]: (s.text or "") for s in arbol.getroot().iter("string")}


es = cargar(os.path.join(RES, "values", "strings.xml"))
en = cargar(os.path.join(RES, "values-en", "strings.xml"))
print(f"claves: es={len(es)}  en={len(en)}")

for k in sorted(set(es) - set(en)):
    falla(f"falta en values-en: {k}")
for k in sorted(set(en) - set(es)):
    falla(f"sobra en values-en (no existe en es): {k}")

# mismos marcadores de formato (%1$s, %2$d...) en las dos versiones
patron = re.compile(r"%\d\$[sd]")
for k in sorted(set(es) & set(en)):
    if sorted(patron.findall(es[k])) != sorted(patron.findall(en[k])):
        falla(f"{k}: los marcadores de formato no coinciden: es={patron.findall(es[k])} en={patron.findall(en[k])}")
    if (es[k].strip() == "" or en[k].strip() == ""):
        falla(f"{k}: texto vacio")
    # en ingles, un apostrofe sin escapar rompe la compilacion de recursos
    for idioma, d in (("es", es), ("en", en)):
        if re.search(r"(?<!\\)'", d[k]):
            falla(f"{idioma}.{k}: apostrofe sin escapar")

# claves usadas en el codigo y en los layouts
usadas = set()
for base, _, archivos in os.walk(RAIZ):
    for a in archivos:
        if a.endswith((".kt", ".xml")) and not base.endswith(("values", "values-en")):
            with open(os.path.join(base, a), encoding="utf-8") as f:
                txt = f.read()
            usadas |= set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", txt))
            usadas |= set(re.findall(r"@string/([A-Za-z0-9_]+)", txt))
for k in sorted(usadas - set(es)):
    falla(f"el codigo usa R.string.{k} pero no existe en values")
sin_usar = sorted(set(es) - usadas)
if sin_usar:
    print("  (aviso) claves definidas y sin usar:", ", ".join(sin_usar))

print()
if problemas:
    print(f"{len(problemas)} problema(s).")
    sys.exit(1)
print("Todo correcto: es y en tienen las mismas claves y marcadores, y el codigo solo usa claves que existen.")
