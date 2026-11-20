package com.aidly.app

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * "Analiza": la persona apunta la camara a un recibo, una carta, un cobro o un mensaje sospechoso
 * (en la pantalla de otro telefono o impreso) y Aidly explica que es, que piden y si hay senales de estafa.
 * Nunca declara que algo es "seguro": solo senala lo que ve y recomienda verificar con alguien de confianza.
 */
@OptIn(PublicPreviewAPI::class)
object AnalizaValidador {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"
    private const val MAX_LADO = 1792

    class Resultado(
        val legible: Boolean,
        val tipo: String,
        val resumenVoz: String,
        val datos: List<String>,
        val riesgo: String,          // alto | medio | bajo | no_aplica
        val senales: String,
        val queHacer: String,
        val confianza: String,
        val fuente: String,
        val ms: Long,
    ) {
        val confiable get() = legible && resumenVoz.isNotBlank() && confianza != "baja"
        val esMedicina get() = tipo == "medicina"
    }

    private const val PROMPT =
        "Eres Aidly, un acompanante para adultos mayores. La persona te muestra la foto de algo con texto y quiere " +
            "saber QUE DICE: el frasco o la caja de una medicina, la etiqueta de un producto, un cartel, un documento, " +
            "recibo, carta, cobro o mensaje (puede ser la pantalla de otro telefono). Primero dile que es y lee lo " +
            "principal (nombre, marca, lo que esta escrito en grande) en palabras sencillas. " +
            "Si es un frasco o caja de medicina: di el nombre y para que sirve SOLO si esta impreso; nunca des una dosis " +
            "o un horario distintos de los impresos. Si no es algo que pida dinero, datos o una accion, " +
            "riesgo_estafa debe ser no_aplica. "+
            "Reglas: usa SOLO lo que se ve en la imagen; no inventes montos, fechas ni nombres; si algo no se " +
            "lee, dejalo fuera. Nunca digas que algo es 'seguro' o 'legitimo': solo puedes decir que no ves " +
            "senales de alerta, y que conviene confirmarlo con un familiar o llamando al numero oficial. " +
            "Senales de estafa: urgencia o amenazas, piden dinero, claves, codigos, datos de tarjeta o dar " +
            "clic en un enlace, premios inesperados, faltas de ortografia, remitente o enlace raro, " +
            "se hacen pasar por un banco, la policia o un familiar. " +
            "Responde SOLO un objeto JSON con estas claves: " +
            "legible (true/false: se puede leer lo importante), " +
            "tipo (recibo, cobro_o_factura, carta_o_documento, mensaje_o_captura, medicina, etiqueta_o_cartel u otro), " +
            "resumen_para_voz (2 o 3 frases cortas en espanol sencillo, para leer en voz alta: que es y que " +
            "piden o dicen), " +
            "datos_clave (lista de hasta 5 datos importantes, por ejemplo 'Total: 350 pesos', " +
            "'Fecha limite: 5 de diciembre'), " +
            "riesgo_estafa (alto, medio, bajo o no_aplica), " +
            "senales_de_alerta (que te parece sospechoso, o cadena vacia), " +
            "que_hacer (un consejo concreto y breve para una persona mayor, por ejemplo 'No des ese codigo a " +
            "nadie y llama a tu banco al numero de tu tarjeta'), " +
            "confianza (alta, media o baja)."

    private val esquema = Schema.obj(
        mapOf(
            "legible" to Schema.boolean("true si se puede leer lo importante"),
            "tipo" to Schema.enumeration(
                listOf("recibo", "cobro_o_factura", "carta_o_documento", "mensaje_o_captura", "medicina", "etiqueta_o_cartel", "otro"),
                "Tipo de documento",
            ),
            "resumen_para_voz" to Schema.string("Resumen en espanol sencillo para leer en voz alta"),
            "datos_clave" to Schema.array(Schema.string("Un dato importante"), "Hasta 5 datos importantes"),
            "riesgo_estafa" to Schema.enumeration(listOf("alto", "medio", "bajo", "no_aplica"), "Riesgo de estafa"),
            "senales_de_alerta" to Schema.string("Senales sospechosas, o cadena vacia"),
            "que_hacer" to Schema.string("Consejo concreto y breve"),
            "confianza" to Schema.enumeration(listOf("alta", "media", "baja"), "Confianza en la lectura"),
        ),
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_IN_CLOUD),
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = esquema
                temperature = 0f
            },
        )
    }

    suspend fun analizar(foto: Bitmap): Resultado {
        Privacidad.exigirNube()
        val bmp = reducir(foto)
        val t0 = System.currentTimeMillis()
        val r = withTimeout(40_000) {
            model.generateContent(content { image(bmp); text(if (Idioma.ultimo == "en") PROMPT + " If the person uses English: write ALL the text values (names read aloud, summaries, data, advice) in simple English. Keep the JSON keys and the fixed values (alto/medio/bajo/no_aplica, alta/media/baja, tipo) exactly as specified." else PROMPT) })
        }
        val ms = System.currentTimeMillis() - t0
        val crudo = r.text.orEmpty()
        SpikeLog.log("ANALIZA: fuente=${r.inferenceSource} $ms ms json=${crudo.take(400)}")
        val j = JSONObject("{" + crudo.substringAfter("{", "").substringBeforeLast("}") + "}")
        fun s(k: String) = j.optString(k, "").trim()
        val arr = j.optJSONArray("datos_clave")
        val datos = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optString(it)?.trim()?.takeIf(String::isNotBlank) }
        return Resultado(
            legible = j.optBoolean("legible", false),
            tipo = s("tipo").ifBlank { "otro" },
            resumenVoz = s("resumen_para_voz"),
            datos = datos,
            riesgo = s("riesgo_estafa").ifBlank { "no_aplica" },
            senales = s("senales_de_alerta"),
            queHacer = s("que_hacer"),
            confianza = s("confianza").ifBlank { "baja" },
            fuente = r.inferenceSource.toString(),
            ms = ms,
        )
    }

    private fun reducir(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= MAX_LADO) return b
        val k = MAX_LADO.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
    }
}
