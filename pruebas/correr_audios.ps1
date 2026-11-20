# Prueba de voz de Aidly SIN microfono ni persona: usa los audios de pruebas\audios (Qwen3-TTS).
# Para cada WAV: Gemini transcribe -> el agente decide -> se compara la ruta con la esperada.
#
# Uso (PowerShell):   .\pruebas\correr_audios.ps1 -Serial emulator-5554
#                     .\pruebas\correr_audios.ps1 -Serial 127.0.0.1:5555 -Solo 01_leer_mensajes,14_auxilio
# Requisitos: APK debug instalado (com.aidly.app), dispositivo visible en "adb devices" y App Check
# registrado para ese dispositivo (si no, la parte de Gemini falla con "token is invalid").
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string[]]$Solo = @(),
    [string]$Adb = "C:\Android\sdk\platform-tools\adb.exe"
)

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$audios = Join-Path $raiz "audios"

# audio -> ruta que debe elegir el agente ("*" = cualquiera, no se comprueba)
$esperado = [ordered]@{
    "01_leer_mensajes"          = "mensajes"
    "02_leer_correo"            = "correo"
    "03_recordatorio_medicina"  = "crear recordatorio"
    "04_recordatorio_cada_8h"   = "crear recordatorio"
    "05_recordatorio_en_minutos"= "crear recordatorio"
    "06_agendar_cita"           = "agendar cita"
    "07_que_tengo_hoy"          = "ver agenda"
    "08_que_hora_es"            = "hora/fecha"
    "09_llamar_hija"            = "llamar"
    "10_abrir_netflix"          = "app Netflix"
    "11_musica"                 = "app Spotify"
    "12_videos_cocina"          = "app YouTube"
    "13_analiza"                = "analiza"
    "14_auxilio"                = "pedir ayuda"
    "15_estoy_bien"             = "estoy bien"
    "16_ya_me_la_tome"          = "ya lo hice"
    "17_pregunta_presion"       = "conversacion"
    "18_clima"                  = "busqueda web"
    "19_responder_si"           = "*"
    "20_responder_no"           = "*"
    "21_dictado_respuesta"      = "*"
    "22_contesta_inline"        = "*"
    "23_med_para_que_sirve"     = "*"
    "24_med_con_comida"         = "*"
    "25_med_dos_dosis"          = "*"
    "26_silencio_ruido"         = "(silencio)"
}
$nombres = @($esperado.Keys | Where-Object { $Solo.Count -eq 0 -or $Solo -contains $_ })

function A { param([string[]]$a) & $Adb -s $Serial @a 2>$null }

Write-Host "Dispositivo: $Serial"
$estado = (& $Adb -s $Serial get-state) 2>&1
if ("$estado" -ne "device") { Write-Host "El dispositivo no esta listo ($estado)."; exit 1 }

# 1) subir audios a la carpeta privada de la app (la app debug permite run-as)
A @("shell", "rm -rf /data/local/tmp/aidly_audio; mkdir -p /data/local/tmp/aidly_audio") | Out-Null
foreach ($n in $nombres) { A @("push", (Join-Path $audios "$n.wav"), "/data/local/tmp/aidly_audio/$n.wav") | Out-Null }
A @("shell", "run-as com.aidly.app mkdir -p files/audio") | Out-Null
foreach ($n in $nombres) {
    A @("shell", "run-as com.aidly.app cp /data/local/tmp/aidly_audio/$n.wav files/audio/$n.wav") | Out-Null
}
# Algunos emuladores no permiten run-as: si no se copio, se usa la carpeta externa de la app (la app tambien la lee).
$copiado = (A @("shell", "run-as com.aidly.app ls files/audio")) -join " "
if ($copiado -notmatch "\.wav") {
    Write-Host "run-as no disponible en este dispositivo; uso la carpeta externa de la app."
    A @("shell", "mkdir -p /sdcard/Android/data/com.aidly.app/files/audio") | Out-Null
    foreach ($n in $nombres) { A @("push", (Join-Path $audios "$n.wav"), "/sdcard/Android/data/com.aidly.app/files/audio/$n.wav") | Out-Null }
}

# 2) lanzar la prueba y esperar a "AUDIO fin"
A @("logcat", "-G", "16M") | Out-Null
A @("logcat", "-c") | Out-Null
$lista = ($nombres | ForEach-Object { "$_.wav" }) -join "|"
A @("shell", "am start -f 0x10008000 -n com.aidly.app/.MainActivity --ez nube true --es audio '$lista'") | Out-Null
$limite = (Get-Date).AddSeconds(90 + 50 * $nombres.Count)
$log = ""
while ((Get-Date) -lt $limite) {
    Start-Sleep -Seconds 5
    $log = (A @("logcat", "-d", "-s", "AidlySpike:I")) -join "`n"
    if ($log -match "AUDIO fin") { break }
}

# 3) resultados
$actual = $null; $oido = @{}; $ruta = @{}; $resp = @{}
foreach ($linea in ($log -split "`n")) {
    if ($linea -match "AUDIO (\S+)\.wav oido='(.*)'") { $actual = $Matches[1]; $oido[$actual] = $Matches[2]; continue }
    if ($actual -and $linea -match "AGENTE: ruta=(.+)$") { $ruta[$actual] = $Matches[1].Trim(); continue }
    if ($linea -match "AUDIO (\S+)\.wav -> (.*)$") { $resp[$Matches[1]] = $Matches[2]; continue }
    if ($linea -match "AUDIO (\S+)\.wav error (.*)$") { $resp[$Matches[1]] = "ERROR " + $Matches[2] }
}
$ok = 0; $mal = 0
Write-Host ""
foreach ($n in $nombres) {
    $e = $esperado[$n]; $r = $ruta[$n]
    $paso = if ($e -eq "(silencio)") { $oido[$n] -eq "(silencio)" } elseif ($e -eq "*") { $oido.ContainsKey($n) } else { $r -eq $e -or ($r -and $r.StartsWith($e)) }
    if ($paso) { $ok++ } else { $mal++ }
    "{0}  {1,-28} oido='{2}'  ruta='{3}' (esperada '{4}')" -f $(if ($paso) { "OK   " } else { "FALLA" }), $n, $oido[$n], $r, $e | Write-Host
    if ($resp[$n]) { "         respuesta: $($resp[$n])" | Write-Host }
}
Write-Host ""
Write-Host "Resultado: $ok correctas, $mal con problemas, de $($nombres.Count)."
