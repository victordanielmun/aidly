package com.aidly.app

import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Estado del modelo del telefono (Gemini Nano via AICore). Al arrancar la app se comprueba y, si se puede descargar, se baja en
 * segundo plano para que el respaldo local ya este listo cuando haga falta (el mismo patron que recomienda el equipo de Firebase AI Logic).
 * Si el telefono no lo admite, `PREFER_*` sigue funcionando con la nube (si la persona la encendio) y con las reglas del telefono.
 * La descarga del modelo no envia datos de la persona: baja el modelo de Google al telefono.
 */
@OptIn(PublicPreviewAPI::class)
object ModeloLocal {
    sealed interface Estado {
        data object Comprobando : Estado
        data object NoDisponible : Estado
        data object Descargable : Estado
        data class Descargando(val bytes: Long, val total: Long) : Estado {
            val porcentaje get() = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else -1
        }
        data object Listo : Estado
        data class Fallo(val motivo: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Comprobando)
    val estado: StateFlow<Estado> = _estado
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var arrancado = false

    /** Se llama una vez al arrancar la app. */
    fun iniciar() {
        if (arrancado) return
        arrancado = true
        scope.launch { comprobar() }
    }

    private suspend fun comprobar() {
        try {
            when (FirebaseAIOnDevice.checkStatus()) {
                OnDeviceModelStatus.AVAILABLE -> _estado.value = Estado.Listo
                OnDeviceModelStatus.UNAVAILABLE -> _estado.value = Estado.NoDisponible
                OnDeviceModelStatus.DOWNLOADABLE, OnDeviceModelStatus.DOWNLOADING -> { _estado.value = Estado.Descargable; descargar() }
                else -> _estado.value = Estado.NoDisponible
            }
            SpikeLog.log("MODELO LOCAL: ${_estado.value}")
        } catch (e: Throwable) {
            SpikeLog.log("MODELO LOCAL: no se pudo comprobar (${e::class.simpleName}: ${e.message})")
            _estado.value = Estado.NoDisponible
        }
    }

    private suspend fun descargar() {
        try {
            var total = 0L
            FirebaseAIOnDevice.download().collect { st ->
                when (st) {
                    is DownloadStatus.DownloadStarted -> { total = st.bytesToDownload; _estado.value = Estado.Descargando(0, total) }
                    is DownloadStatus.DownloadInProgress -> _estado.value = Estado.Descargando(st.totalBytesDownloaded, total)
                    is DownloadStatus.DownloadCompleted -> _estado.value = Estado.Listo
                    is DownloadStatus.DownloadFailed -> _estado.value = Estado.Fallo("descarga")
                    else -> {}
                }
            }
            SpikeLog.log("MODELO LOCAL: descarga terminada (${_estado.value})")
        } catch (e: Throwable) {
            SpikeLog.log("MODELO LOCAL: fallo la descarga (${e::class.simpleName}: ${e.message})")
            _estado.value = Estado.Fallo(e.message ?: "error")
        }
    }
}
