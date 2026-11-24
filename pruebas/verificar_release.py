#!/usr/bin/env python
"""Compuerta de publicacion de Aidly (idea tomada de los 'release gates' de fun-phone, escrita desde cero).

Revisa el manifiesto, las dependencias, secretos en el codigo y los documentos de privacidad antes de subir el codigo
o publicar. Sale con codigo 1 si hay FALLOS; los AVISOS no bloquean pero hay que leerlos.
Uso:  python pruebas/verificar_release.py
"""
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MANIFIESTO = RAIZ / "app/src/main/AndroidManifest.xml"
GRADLE = RAIZ / "app/build.gradle.kts"
fallos, avisos = [], []


def fallo(m): fallos.append(m)
def aviso(m): avisos.append(m)


# ---------- Manifiesto ----------
man = MANIFIESTO.read_text(encoding="utf-8")
permisos = set(re.findall(r'<uses-permission android:name="android\.permission\.([A-Z_]+)"', man))

PERMITIDOS = {
    "INTERNET", "RECORD_AUDIO", "CAMERA", "POST_NOTIFICATIONS", "READ_CONTACTS", "ACCESS_COARSE_LOCATION",
    "ACCESS_FINE_LOCATION", "READ_CALENDAR", "WRITE_CALENDAR", "USE_EXACT_ALARM", "SCHEDULE_EXACT_ALARM",
    "RECEIVE_BOOT_COMPLETED", "USE_FULL_SCREEN_INTENT", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MICROPHONE",
}
for p in sorted(permisos - PERMITIDOS):
    fallo(f"permiso nuevo sin revisar: {p} (si es intencional, anadelo a PERMITIDOS y a docs/PRIVACY_DATA_FLOW.md)")
for p in sorted(PERMITIDOS - permisos):
    aviso(f"permiso esperado que ya no esta en el manifiesto: {p}")
if "AD_ID" in man or "com.google.android.gms.permission.AD_ID" in man:
    fallo("el manifiesto pide AD_ID (Aidly no tiene anuncios)")
if 'android:allowBackup="false"' not in man:
    fallo("allowBackup debe ser false (los datos no deben ir al respaldo en la nube)")
if "android:debuggable" in man:
    fallo("el manifiesto fija debuggable")
if "usesCleartextTraffic=\"true\"" in man:
    fallo("trafico sin cifrar permitido")

# El servicio de accesibilidad solo lo puede conectar el sistema y no debe pedir eventos ni nada fuera de su finalidad
if "android.permission.BIND_ACCESSIBILITY_SERVICE" not in man:
    fallo("el servicio de accesibilidad no exige BIND_ACCESSIBILITY_SERVICE")
cfg = RAIZ / "app/src/main/res/xml/aidly_accesibilidad.xml"
if cfg.exists():
    x = cfg.read_text(encoding="utf-8")
    if "accessibilityEventTypes" in x.replace("<!--", "").split("-->")[-1]:
        fallo("el servicio de accesibilidad se suscribe a eventos: debe quedar sin ninguno")

# Componentes exportados: solo los esperados
# (los servicios de sistema que el propio Android debe poder conectar van exportados pero protegidos por un permiso de sistema:
#  notificaciones, filtro de llamadas y accesibilidad con BIND_ACCESSIBILITY_SERVICE)
ESPERADOS_EXPORTADOS = {".HomeActivity", ".ArranqueReceiver", ".AidlyWidgetReceiver",
                        ".AidlyWidgetCompactoReceiver", ".AidlyNotificationListener", ".AidlyCallScreeningService", ".AidlyAccesibilidad"}
exportados = set()
for bloque in re.findall(r"<(?:activity|receiver|service)\b.*?>", man, flags=re.S):
    if 'android:exported="true"' in bloque:
        n = re.search(r'android:name="([^"]+)"', bloque)
        if n: exportados.add(n.group(1))
for n in sorted(exportados - ESPERADOS_EXPORTADOS):
    fallo(f"componente exportado inesperado: {n}")
# MainActivity (pruebas tecnicas y ganchos de adb) solo se exporta en app/src/debug; en el manifiesto principal es un FALLO.

# ---------- Dependencias ----------
g = GRADLE.read_text(encoding="utf-8")
for malo in ("analytics", "crashlytics", "admob", "play-services-ads", "facebook", "adjust", "appsflyer", "mixpanel", "sentry"):
    if re.search(rf'implementation\([^)]*{malo}', g, flags=re.I):
        fallo(f"dependencia de seguimiento o anuncios: {malo}")
if re.search(r'^\s*implementation\("com\.google\.firebase:firebase-appcheck-debug', g, flags=re.M):
    aviso("App Check de DEPURACION va dentro de la version final: cambiar a Play Integrity antes de publicar "
          "(pasar firebase-appcheck-debug a debugImplementation y usar PlayIntegrityAppCheckProviderFactory en release)")

# ---------- Secretos en el codigo ----------
PATRONES = {
    "clave de API de Google": re.compile(r"AIza[0-9A-Za-z_\-]{35}"),
    "clave privada": re.compile(r"-----BEGIN (?:RSA |EC )?PRIVATE KEY-----"),
    "token de acceso": re.compile(r"(?:ghp|gho|ghs)_[A-Za-z0-9]{30,}"),
}
IGNORAR = {"build", ".gradle", ".git", "node_modules"}
_gi = RAIZ / ".gitignore"
IGNORADOS_GIT = {l.strip() for l in _gi.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")} if _gi.exists() else set()
# Solo lo que se publicaria o se sube al repo (no las carpetas de entrenamiento ni las cache de modelos).
CARPETAS = ("app", "familia", "docs", "pruebas", "firebase")
ARCHIVOS = ("firestore.rules", "firebase.json", "PLAN.md", "build.gradle.kts", "settings.gradle.kts")
candidatos = [RAIZ / a for a in ARCHIVOS] + [r for c in CARPETAS if (RAIZ / c).exists() for r in (RAIZ / c).rglob("*")]
for ruta in candidatos:
    try:
        if not ruta.is_file() or any(part in IGNORAR for part in ruta.parts):
            continue
    except OSError:
        continue
    if ruta.suffix.lower() not in {".kt", ".kts", ".xml", ".json", ".md", ".html", ".js", ".py", ".ps1", ".properties", ".txt", ".rules"}:
        continue
    try:
        t = ruta.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        continue
    for nombre, pat in PATRONES.items():
        if pat.search(t):
            rel = ruta.relative_to(RAIZ).as_posix()
            if rel in IGNORADOS_GIT or any(rel.startswith(d) for d in IGNORADOS_GIT if d.endswith("/")):
                if rel != "app/google-services.json":      # este tiene su propio aviso mas abajo
                    aviso(f"{rel} lleva una clave pero esta en .gitignore (correcto: no se sube)")
                    continue
            if ruta.name == "google-services.json":
                gi = (RAIZ / ".gitignore").read_text(encoding="utf-8") if (RAIZ / ".gitignore").exists() else ""
                if "google-services.json" not in gi:
                    fallo(f"{rel} lleva una clave de API y NO esta en .gitignore: las reglas prohiben claves de produccion en el repositorio (regla 'Testing'); sacalo del repo y deja un ejemplo")
                else:
                    aviso(f"{rel} lleva una clave de API pero esta en .gitignore (correcto: no subirlo; la APK de prueba se entrega aparte)")
            elif rel == "familia/index.html" and nombre == "clave de API de Google":
                fallo(f"{rel} lleva la configuracion web de Firebase con clave: en el repositorio publico debe ir un marcador (la pagina ya publicada en Hosting conserva la real)")
            else:
                fallo(f"{nombre} en {rel}")

# ---------- Documentos y textos ----------
for doc in ("docs/PRIVACY.md", "docs/PRIVACY_DATA_FLOW.md", "docs/PLAY_DATA_SAFETY.md"):
    if not (RAIZ / doc).exists():
        fallo(f"falta {doc}")
r = subprocess.run([sys.executable, str(RAIZ / "pruebas/validar_textos.py")], capture_output=True, text=True)
if r.returncode != 0:
    fallo("validar_textos.py fallo:\n" + r.stdout[-600:])
r = subprocess.run([sys.executable, str(RAIZ / "pruebas/validar_idiomas.py")], capture_output=True, text=True)
if r.returncode != 0:
    fallo("validar_idiomas.py fallo:\n" + r.stdout[-600:])
r = subprocess.run([sys.executable, str(RAIZ / "pruebas/validar_traducciones.py")], capture_output=True, text=True)
if r.returncode != 0:
    fallo("validar_traducciones.py fallo:" + chr(10) + r.stdout[-600:])

# ---------- Registro de depuracion ----------
log = (RAIZ / "app/src/main/java/com/aidly/app/SpikeLog.kt").read_text(encoding="utf-8")
if "if (!BuildConfig.DEBUG) return" not in log:
    fallo("SpikeLog escribe frases habladas tambien en la version final")


# ---------- Requisitos de las reglas oficiales (revisadas el 19-nov-2026) ----------
if not any((RAIZ / n).exists() for n in ("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING")):
    fallo("falta el archivo de licencia de codigo abierto (las reglas exigen repositorio publico con licencia visible en About; se prefiere Apache-2.0)")
if not (RAIZ / "README.md").exists():
    fallo("falta README.md con propuesta de valor, uso de Firebase AI Logic en tiempo de ejecucion e instrucciones")
if not (RAIZ / ".git").exists():
    aviso("la carpeta aun no es un repositorio git (las reglas piden URL publica en GitHub o GitLab)")
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
for ruta in candidatos:
    try:
        if not ruta.is_file() or ruta.suffix.lower() not in {".md", ".kt", ".xml", ".html", ".json", ".txt"} or any(p in IGNORAR for p in ruta.parts):
            continue
        for m in EMAIL.findall(ruta.read_text(encoding="utf-8", errors="ignore")):
            if not m.lower().endswith(("@example.com", "@android.com", "@gmail.com.example")) and "noreply" not in m.lower():
                fallo(f"posible correo (dato personal) en {ruta.relative_to(RAIZ).as_posix()}: {m}")
    except OSError:
        continue

print("== Compuerta de publicacion de Aidly ==")
for a in avisos: print("AVISO:", a)
for f in fallos: print("FALLO:", f)
print(f"\n{len(fallos)} fallo(s), {len(avisos)} aviso(s).")
sys.exit(1 if fallos else 0)
