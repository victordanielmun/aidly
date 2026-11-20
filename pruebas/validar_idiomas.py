"""Valida app/src/main/assets/config/idiomas.json: JSON correcto y mismo esquema en todos los idiomas.

Uso:  python pruebas/validar_idiomas.py
Sale con codigo 1 si hay problemas (sirve para correrlo antes de cada entrega).
"""
import json
import os
import re
import sys

RUTA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "config", "idiomas.json")
PERMISOS = {"microfono", "notificaciones", "acceso_mensajes", "calendario", "contactos", "ubicacion", "camara"}
problemas = []


def claves(obj, prefijo=""):
    """Rutas de claves de un objeto (las listas cuentan como hoja)."""
    salida = set()
    if isinstance(obj, dict):
        for k, v in obj.items():
            ruta = f"{prefijo}.{k}" if prefijo else k
            salida.add(ruta)
            salida |= claves(v, ruta)
    return salida


def falla(msg):
    problemas.append(msg)
    print("  FALLA:", msg)


with open(RUTA, encoding="utf-8") as f:
    texto = f.read()
if texto.startswith("﻿"):
    falla("el archivo tiene BOM (debe ser UTF-8 sin BOM)")
datos = json.loads(texto)
print("JSON valido:", os.path.normpath(RUTA))

idiomas = datos["idiomas"]
print("idiomas:", ", ".join(idiomas))

# 1) mismo esquema en todos los idiomas
base_id = datos["predeterminado"]
if base_id not in idiomas:
    falla(f"el idioma predeterminado '{base_id}' no existe")
base = claves(idiomas[base_id])
for iid, cfg in idiomas.items():
    k = claves(cfg)
    # los mapas "comandos", "apps" y "periodos" comparten claves; "bienvenida.pasos" es lista
    for faltante in sorted(base - k):
        falla(f"{iid}: falta '{faltante}' (existe en {base_id})")
    for sobrante in sorted(k - base):
        falla(f"{iid}: sobra '{sobrante}' (no existe en {base_id})")

# 2) pasos de bienvenida: mismos ids y permisos conocidos
ids_base = [p["id"] for p in idiomas[base_id]["bienvenida"]["pasos"]]
for iid, cfg in idiomas.items():
    ids = [p["id"] for p in cfg["bienvenida"]["pasos"]]
    if ids != ids_base:
        falla(f"{iid}: los pasos de bienvenida difieren: {ids} vs {ids_base}")
    for p in cfg["bienvenida"]["pasos"]:
        for perm in p.get("permisos", []):
            if perm not in PERMISOS:
                falla(f"{iid}: permiso desconocido '{perm}' en el paso '{p['id']}'")

# 3) comandos y apps: sin listas vacias ni duplicados dentro de la misma intencion
for iid, cfg in idiomas.items():
    for grupo in ("comandos", "apps"):
        for nombre, frases in cfg[grupo].items():
            if not frases:
                falla(f"{iid}.{grupo}.{nombre}: lista vacia")
            if len(set(frases)) != len(frases):
                falla(f"{iid}.{grupo}.{nombre}: frases repetidas")

# 4) resolucion: todas las equivalencias apuntan a un idioma que existe
res = datos["resolucion"]
for etiqueta, destino in res["equivalencias"].items():
    if destino not in idiomas:
        falla(f"equivalencia '{etiqueta}' -> '{destino}': ese idioma no existe")
if res["si_no_soportado"] not in idiomas:
    falla("'si_no_soportado' apunta a un idioma que no existe")

# 5) paises
if "*" not in datos["paises"]:
    falla("falta el pais comodin '*'")
for pais, p in datos["paises"].items():
    if not re.fullmatch(r"\d{2,3}", p["emergencia"]):
        falla(f"{pais}: emergencia '{p['emergencia']}' no es un numero de 2-3 digitos")
    if p["idioma_sugerido"] not in idiomas:
        falla(f"{pais}: idioma_sugerido '{p['idioma_sugerido']}' no existe")
    if pais != "*" and not re.fullmatch(r"\d{1,3}", p["prefijo_tel"]):
        falla(f"{pais}: prefijo_tel '{p['prefijo_tel']}' invalido")
for c in datos["paises_sin_verificar"]["codigos"]:
    if c in datos["paises"]:
        falla(f"{c} esta en 'paises' y en 'paises_sin_verificar'")

# 6) horas y valores iniciales razonables
for iid, cfg in idiomas.items():
    h = cfg["inicial"]["checkin"]["hora"]
    if not re.fullmatch(r"([01]\d|2[0-3]):[0-5]\d", h):
        falla(f"{iid}: hora de check-in invalida '{h}'")
    r = cfg["inicial"]["recordatorios"]
    if not (1 <= r["posponer_min"] <= 60 and r["marcar_omitido_tras_min"] > r["posponer_min"]):
        falla(f"{iid}: tiempos de recordatorio incoherentes")

print()
if problemas:
    print(f"{len(problemas)} problema(s).")
    sys.exit(1)
print("Todo correcto: esquema igual en todos los idiomas.")
