"""Corre cada frase de un banco (frase | ruta esperada) por el agente de la app y compara con la ruta que registro.
Uso: python pruebas/correr_rutas.py <serial> <archivo.txt>   (SOLO emulador: algunas frases abren el marcador o crean recordatorios)."""
import os, subprocess, sys, time, re

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
serial, archivo = sys.argv[1], sys.argv[2]

def adb(*a): return subprocess.run([ADB, "-s", serial, *a], capture_output=True).stdout.decode("utf-8", "ignore")

filas = []
for l in open(archivo, encoding="utf-8"):
    l = l.strip()
    if not l or l.startswith("#"): continue
    f, r = [x.strip() for x in l.split("|")]
    filas.append((f, r))

mal = 0
for f, esperada in filas:
    adb("shell", "am", "force-stop", "com.aidly.app"); adb("logcat", "-c")
    adb("shell", "am", "start", "-n", "com.aidly.app/.MainActivity", "--es", "frase", "'" + f + "'")
    ruta = ""
    for _ in range(12):
        time.sleep(0.5)
        m = re.search(r"AGENTE: ruta=([^\r\n]*)", adb("logcat", "-d", "-s", "AidlySpike:I"))
        if m: ruta = m.group(1).strip(); break
    ok = ruta.startswith(esperada)
    mal += (not ok)
    print(("OK   " if ok else "FALLA"), f"| {f} | esperada={esperada} | obtenida={ruta or '(sin ruta)'}")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
print(f"\n{len(filas) - mal}/{len(filas)} bien")
sys.exit(1 if mal else 0)
