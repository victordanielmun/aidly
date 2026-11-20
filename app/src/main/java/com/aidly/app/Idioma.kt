package com.aidly.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import org.json.JSONObject
import java.util.Locale

/**
 * Idiomas de Aidly (es / en). Lee `assets/config/idiomas.json`:
 *  - que idioma usar (preferencia > idioma del sistema > pais del SIM > predeterminado),
 *  - opciones de voz, formatos, valores iniciales, comandos y datos por pais.
 * Los textos de pantalla viven en res/values (es) y res/values-en (en); Android elige el que corresponde
 * porque cada pantalla se envuelve con el idioma resuelto ([ActividadAidly]).
 */
object Idioma {
    class Opcion(val id: String, val nombre: String, val estado: String)

    @Volatile private var cache: JSONObject? = null
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    @Synchronized
    private fun datos(ctx: Context): JSONObject {
        cache?.let { return it }
        val j = try {
            JSONObject(ctx.applicationContext.assets.open("config/idiomas.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            SpikeLog.log("IDIOMA: no pude leer idiomas.json (${e.message}); uso solo espanol")
            JSONObject("""{"predeterminado":"es","resolucion":{"equivalencias":{"es":"es"},"si_no_soportado":"es"},"idiomas":{"es":{"nombre":"Español","estado":"completo"}},"paises":{}}""")
        }
        cache = j
        return j
    }

    /** Idiomas que se ofrecen en Ajustes, en el orden del archivo. */
    fun disponibles(ctx: Context): List<Opcion> {
        val ids = datos(ctx).getJSONObject("idiomas")
        return ids.keys().asSequence().map { id ->
            val c = ids.getJSONObject(id)
            Opcion(id, c.optString("nombre", id), c.optString("estado"))
        }.toList()
    }

    /** "" = automatico (sigue al telefono). */
    fun preferido(ctx: Context): String = prefs(ctx).getString("idioma", "").orEmpty()

    fun fijar(ctx: Context, id: String) = prefs(ctx).edit().putString("idioma", id).apply()

    /** Idioma efectivo: preferencia, idioma del sistema, pais del SIM, predeterminado. */
    fun resolver(ctx: Context): String {
        val d = datos(ctx)
        val existentes = d.getJSONObject("idiomas")
        preferido(ctx).takeIf { it.isNotEmpty() && existentes.has(it) }?.let { return it }
        val res = d.getJSONObject("resolucion")
        val eq = res.getJSONObject("equivalencias")
        val sistema = Resources.getSystem().configuration.locales.let { if (it.isEmpty) null else it[0] }
        if (sistema != null) {
            val etiqueta = sistema.toLanguageTag()
            val mapeado = eq.optString(etiqueta).ifEmpty { eq.optString(sistema.language) }
            if (mapeado.isNotEmpty() && existentes.has(mapeado)) return mapeado
            return res.optString("si_no_soportado", d.getString("predeterminado")).takeIf { existentes.has(it) } ?: d.getString("predeterminado")
        }
        val pais = Emergencia.pais(ctx)
        d.getJSONObject("paises").optJSONObject(pais)?.optString("idioma_sugerido")?.takeIf { existentes.has(it) }?.let { return it }
        return d.getString("predeterminado")
    }

    fun locale(ctx: Context): Locale = Locale.forLanguageTag(resolver(ctx))

    /** Configuracion del idioma efectivo (`idiomas.<id>` del JSON). */
    fun config(ctx: Context): JSONObject = datos(ctx).getJSONObject("idiomas").getJSONObject(resolver(ctx))

    /** Datos del pais del SIM (emergencias, prefijo...), o el comodin `*`. */
    fun datosPais(ctx: Context): JSONObject {
        val paises = datos(ctx).getJSONObject("paises")
        return paises.optJSONObject(Emergencia.pais(ctx)) ?: paises.getJSONObject("*")
    }

    /** Aplica el idioma efectivo a un contexto, para que los textos salgan del values/values-en correcto. */
    fun envolver(base: Context): Context {
        val loc = locale(base)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        return base.createConfigurationContext(cfg)
    }

    // ---------- Configuracion inicial (idiomas.<id>.inicial) ----------

    fun inicial(ctx: Context): JSONObject = config(ctx).getJSONObject("inicial")

    /** Configuracion de un idioma concreto (por ejemplo "es" para lo que Aidly DICE en voz alta en el nivel B). */
    fun configDe(ctx: Context, id: String): JSONObject? = datos(ctx).getJSONObject("idiomas").optJSONObject(id)

    /** Hora del aviso diario por defecto, en minutos desde medianoche ("09:00" -> 540). */
    fun horaCheckinMin(ctx: Context): Int {
        val h = inicial(ctx).getJSONObject("checkin").optString("hora", "09:00").split(":")
        return (h.getOrNull(0)?.toIntOrNull() ?: 9) * 60 + (h.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** Aplica los valores iniciales que no dependen del circulo (se llama al terminar la bienvenida). */
    fun aplicarInicial(ctx: Context) {
        Preferencias.fijarReducirMovimiento(ctx, inicial(ctx).getJSONObject("accesibilidad").optBoolean("reducir_movimiento", false))
    }

    /** "Restablecer valores del idioma": vuelve a los valores iniciales, tambien los del circulo si esta activo. */
    fun restablecer(ctx: Context) {
        aplicarInicial(ctx)
        if (Circulo.ajustes(ctx).activo) {
            val cir = inicial(ctx).getJSONObject("circulo")
            Circulo.editar(ctx) {
                putBoolean("ubicacion", cir.optBoolean("compartir_ubicacion", false))
                putBoolean("actividad", cir.optBoolean("compartir_actividad", true))
                putBoolean("medicinas", cir.optBoolean("compartir_medicinas", true))
                putBoolean("checkin", inicial(ctx).getJSONObject("checkin").optBoolean("activo", true))
                putInt("horaMin", horaCheckinMin(ctx))
            }
            Circulo.programarCheckin(ctx)
            Circulo.sincronizarAsync(ctx)
        }
    }

    /** Texto para la VOZ: hoy siempre en espanol (nivel B), aunque la pantalla este en otro idioma. */
    fun vozTexto(ctx: Context, @androidx.annotation.StringRes id: Int, vararg args: Any): String {
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale("es"))
        return ctx.createConfigurationContext(cfg).getString(id, *args)
    }

    /** Saludo segun la hora (del JSON: `saludos`). */
    fun saludo(ctx: Context, hora: Int): String {
        val s = config(ctx).getJSONObject("saludos")
        return s.getString(when { hora < 12 -> "manana"; hora < 19 -> "tarde"; else -> "noche" })
    }
}

/**
 * Base de todas las pantallas: aplica el idioma elegido (Compose lee los textos del contexto envuelto).
 * Las pantallas con datos personales (medicinas, papeles, familia, conversaciones) se marcan como [protegida]:
 * en la version final no salen en capturas de pantalla, grabaciones ni en la vista de apps recientes.
 * En desarrollo (debug) no se aplica, para poder tomar capturas.
 */
open class ActividadAidly : ComponentActivity() {
    protected open val protegida: Boolean get() = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (protegida && !BuildConfig.DEBUG) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Idioma.envolver(newBase))
    }
}
