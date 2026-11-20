package com.aidly.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Registro simple visible en pantalla y en logcat (tag AidlySpike). Solo para desarrollo: el registro incluye frases
 * habladas y respuestas, asi que en la version final (release) no se escribe nada, ni en logcat ni en memoria.
 */
object SpikeLog {
    private val _lines = MutableStateFlow<List<String>>(listOf("Listo."))
    val lines: StateFlow<List<String>> = _lines

    fun log(msg: String) {
        if (!BuildConfig.DEBUG) return
        Log.i("AidlySpike", msg)
        _lines.value = (_lines.value + msg).takeLast(60)
    }
}
