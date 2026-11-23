package com.aidly.app

import android.graphics.Bitmap

/**
 * Pasa una foto de una pantalla a otra sin guardarla en el disco (solo en memoria): la camara general ("Tomar una foto") reconoce que es una
 * medicina y le da la MISMA foto a la ficha de medicinas, en vez de pedir otra. Se entrega una sola vez y se olvida.
 */
object FotoPendiente {
    @Volatile private var foto: Bitmap? = null
    fun poner(b: Bitmap) { foto = b }
    fun tomar(): Bitmap? = foto.also { foto = null }
}
