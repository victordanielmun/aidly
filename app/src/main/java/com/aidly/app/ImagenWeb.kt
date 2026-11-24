package com.aidly.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga las imagenes de las tarjetas de "Para ti" (BBC, Wikipedia/Wikimedia Commons, Smithsonian, NASA). Solo https y solo de sitios de la
 * lista permitida de [FuentesWeb]; solo con la nube encendida (es salir a internet y ese sitio ve la IP del telefono); se reducen al bajarlas
 * y se guardan solo en memoria (nunca en el disco). Si algo falla devuelve null y la tarjeta se muestra sin imagen.
 */
object ImagenWeb {
    private const val MAX_BYTES = 3_000_000
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }

    suspend fun cargar(url: String, anchoMax: Int = 900): Bitmap? {
        if (FuentesWeb.imagenSegura(url).isEmpty() || !Privacidad.nubeActiva()) return null
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8_000; c.readTimeout = 10_000
                c.setRequestProperty("User-Agent", "Aidly/0.1 (Android; concurso #BuildHybridAI)")
                if (c.responseCode != 200) { SpikeLog.log("IMAGEN: ${c.responseCode} en ${url.substringBefore('?').takeLast(40)}"); return@withContext null }
                val bytes = c.inputStream.use { entrada ->
                    val salida = java.io.ByteArrayOutputStream(); val b = ByteArray(8192); var total = 0
                    while (total < MAX_BYTES) { val n = entrada.read(b); if (n < 0) break; salida.write(b, 0, n); total += n }
                    salida.toByteArray()
                }
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
                var muestra = 1
                while (medidas.outWidth / (muestra * 2) >= anchoMax) muestra *= 2
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@withContext null
                cache.put(url, bmp)
                bmp
            } catch (e: Exception) { SpikeLog.log("IMAGEN: fallo ${e::class.simpleName}"); null }
        }
    }
}
