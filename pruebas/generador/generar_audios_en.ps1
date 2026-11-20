# Genera los 25 audios de prueba EN INGLES con las voces de Windows (System.Speech), a 16 kHz mono, en pruebas\audios_en.
# Las voces sinteticas de Windows sirven para regresiones del enrutador y de la transcripcion; no prueban un micro real.
# Uso:  powershell -ExecutionPolicy Bypass -File pruebas\generador\generar_audios_en.ps1
Add-Type -AssemblyName System.Speech
$raiz = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$salida = Join-Path $raiz "audios_en"
New-Item -ItemType Directory -Force $salida | Out-Null
$frases = [ordered]@{
    "01_leer_mensajes"           = "Read my messages."
    "02_leer_correo"             = "Read my email."
    "03_recordatorio_medicina"   = "Remind me to take my blood pressure pill at eight in the evening."
    "04_recordatorio_cada_8h"    = "Remind me to take the antibiotic every eight hours."
    "05_recordatorio_en_minutos" = "Remind me in twenty minutes to turn off the stove."
    "06_agendar_cita"            = "Schedule a doctor appointment on Tuesday at ten in the morning."
    "07_que_tengo_hoy"           = "What do I have today?"
    "08_que_hora_es"             = "What time is it?"
    "09_llamar_hija"             = "Call my daughter."
    "10_abrir_netflix"           = "Open Netflix."
    "11_musica"                  = "Play music by Julio Iglesias."
    "12_videos_cocina"           = "I want to watch cooking videos."
    "13_analiza"                 = "Analyze this."
    "14_auxilio"                 = "Help me, I feel sick."
    "15_estoy_bien"              = "I am fine."
    "16_ya_me_la_tome"           = "I already took my pill."
    "17_pregunta_presion"        = "What is blood pressure?"
    "18_clima"                   = "What is the weather like in Bogota today?"
    "19_responder_si"            = "Yes."
    "20_responder_no"            = "No."
    "21_dictado_respuesta"       = "Hi my love, I just got home, do not worry."
    "22_contesta_inline"         = "Reply that I am on my way."
    "23_med_para_que_sirve"      = "What is this medicine for?"
    "24_med_con_comida"          = "Can I take it on an empty stomach?"
    "25_med_dos_dosis"           = "I forgot a pill, should I take two today?"
}
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voz = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like "en-*" -and $_.Enabled } | Select-Object -First 1
if (-not $voz) { Write-Host "No hay voz en ingles instalada en Windows."; exit 1 }
$s.SelectVoice($voz.VoiceInfo.Name)
$s.Rate = -1
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
foreach ($k in $frases.Keys) {
    $ruta = Join-Path $salida "$k.wav"
    $s.SetOutputToWaveFile($ruta, $fmt)
    $s.Speak($frases[$k])
    $s.SetOutputToNull()
    Write-Host ("{0}  {1}" -f $k, $frases[$k])
}
Copy-Item (Join-Path $raiz "audios\26_silencio_ruido.wav") (Join-Path $salida "26_silencio_ruido.wav") -Force
$frases | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $salida "frases.json")
Write-Host "Voz usada: $($voz.VoiceInfo.Name). Audios en $salida"
