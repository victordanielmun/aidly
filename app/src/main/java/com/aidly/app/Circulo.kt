package com.aidly.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Circulo familiar. La persona (o un familiar con su telefono en la mano) crea un codigo; la familia abre
 * la pagina web, escribe el codigo y ve lo que la persona decidio compartir.
 *  - Todo apagado por defecto salvo el check-in diario; cada dato (ubicacion, ultima actividad, medicinas)
 *    tiene su interruptor, y "dejar de compartir" borra todo en la nube.
 *  - El codigo es largo y aleatorio (12 caracteres, ~60 bits): quien lo tiene, ve; no se puede adivinar ni listar.
 *  - Una emergencia (boton SOS o "auxilio") incluye la ubicacion aunque no se comparta normalmente.
 *  - Solo se envia al usar el telefono (abrir Aidly, contestar un aviso, SOS); no hay rastreo continuo.
 */
object Circulo {
    const val URL = "https://aidly-d108b.web.app"
    const val ID_CHECKIN = "checkin-diario"
    private const val PREFS = "circulo"
    private const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val LARGO = 12
    private const val SOS_VIGENCIA_MS = 24L * 60 * 60 * 1000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class Ajustes(
        val activo: Boolean, val codigo: String, val nombre: String,
        val ubicacion: Boolean, val actividad: Boolean, val medicinas: Boolean,
        val checkin: Boolean, val horaMin: Int, val bienestar: Boolean = true,
    ) {
        /** "ABCD-EFGH-JKLM" para leerlo en voz alta o dictarlo. */
        val codigoBonito get() = codigo.chunked(4).joinToString("-")
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ajustes(ctx: Context): Ajustes = prefs(ctx).let {
        Ajustes(
            it.getBoolean("activo", false), Cifrado.abrir(it.getString("codigo", "")).orEmpty(), Cifrado.abrir(it.getString("nombre", "")).orEmpty(),
            it.getBoolean("ubicacion", false), it.getBoolean("actividad", true), it.getBoolean("medicinas", true),
            it.getBoolean("checkin", true), it.getInt("horaMin", 9 * 60), it.getBoolean("bienestar", true),
        )
    }

    fun editar(ctx: Context, bloque: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs(ctx).edit().apply(bloque).apply()
    }

    fun tieneUbicacion(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---------- Crear / borrar ----------

    private fun generarCodigo(): String {
        val r = SecureRandom()
        return (1..LARGO).map { ALFABETO[r.nextInt(ALFABETO.length)] }.joinToString("")
    }

    /** Crea el circulo y devuelve el codigo. Lanza excepcion si no hay internet (no deja nada a medias). */
    suspend fun activar(ctx: Context, nombre: String): String {
        val codigo = generarCodigo()
        editar(ctx) {
            putBoolean("activo", true); putString("codigo", Cifrado.cerrar(codigo)); putString("nombre", Cifrado.cerrar(nombre.trim()))
            val cir = Idioma.inicial(ctx).getJSONObject("circulo")
            putBoolean("ubicacion", cir.optBoolean("compartir_ubicacion", false))
            putBoolean("actividad", cir.optBoolean("compartir_actividad", true))
            putBoolean("medicinas", cir.optBoolean("compartir_medicinas", true))
            putBoolean("bienestar", cir.optBoolean("compartir_bienestar", true))
            putBoolean("checkin", Idioma.inicial(ctx).getJSONObject("checkin").optBoolean("activo", true))
            putInt("horaMin", Idioma.horaCheckinMin(ctx))
            putLong("toque", System.currentTimeMillis()); remove("sosHora")
        }
        try {
            sincronizar(ctx)
        } catch (e: Exception) {
            editar(ctx) { clear() }
            throw e
        }
        programarCheckin(ctx)
        SpikeLog.log("CIRCULO: activado (codigo ${codigo.take(2)}**********)")
        return codigo
    }

    /** Deja de compartir: borra el documento en la nube y los ajustes locales. */
    suspend fun desactivar(ctx: Context) {
        val a = ajustes(ctx)
        if (a.activo && a.codigo.isNotBlank()) {
            try {
                withTimeout(15_000) { Firebase.firestore.collection("circulos").document(a.codigo).delete().aguardar() }
            } catch (e: Exception) {
                SpikeLog.log("CIRCULO: no pude borrar en la nube (${e::class.simpleName}: ${e.message})")
            }
        }
        editar(ctx) { clear() }
        Recordatorios.cancelar(ctx, ID_CHECKIN)
        SpikeLog.log("CIRCULO: desactivado")
    }

    /** Pone (o quita) la alarma diaria del check-in segun los ajustes. */
    fun programarCheckin(ctx: Context) {
        Recordatorios.cancelar(ctx, ID_CHECKIN)
        val a = ajustes(ctx)
        if (!a.activo || !a.checkin) return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.horaMin / 60); set(Calendar.MINUTE, a.horaMin % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 30_000) add(Calendar.DAY_OF_MONTH, 1)
        }
        Recordatorios.agregar(ctx, Recordatorios.Item(ID_CHECKIN, "cómo te sientes hoy", Recordatorios.Tipo.CHECKIN, c.timeInMillis, 1440)) // i18n-ok
    }

    // ---------- Actividad, sincronizacion y SOS ----------

    /** La persona uso el telefono: actualiza "ultima actividad" (como mucho cada 2 minutos). */
    fun tocar(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean("activo", false)) return
        val ahora = System.currentTimeMillis()
        if (ahora - p.getLong("toque", 0) < 2 * 60_000) return
        p.edit().putLong("toque", ahora).apply()
        sincronizarAsync(ctx)
    }

    fun sincronizarAsync(ctx: Context) {
        if (!prefs(ctx).getBoolean("activo", false)) return
        val app = ctx.applicationContext
        scope.launch { try { sincronizar(app) } catch (e: Exception) { SpikeLog.log("CIRCULO: sync fallo (${e::class.simpleName}: ${e.message})") } }
    }

    fun sosAsync(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { try { sos(app) } catch (e: Exception) { SpikeLog.log("CIRCULO: sos fallo (${e::class.simpleName}: ${e.message})") } }
    }

    /** Emergencia: marca el SOS con hora y, si hay permiso, ubicacion (aunque normalmente no se comparta). */
    suspend fun sos(ctx: Context) {
        if (!ajustes(ctx).activo) return
        val loc = Ubicacion.actual(ctx)
        editar(ctx) {
            putLong("sosHora", System.currentTimeMillis())
            if (loc != null) { putString("sosLat", Cifrado.cerrar(loc.latitude.toString())); putString("sosLng", Cifrado.cerrar(loc.longitude.toString())) }
            else { remove("sosLat"); remove("sosLng") }
        }
        SpikeLog.log("CIRCULO: SOS (ubicacion=${loc != null})")
        sincronizar(ctx)
    }

    /** Aidly pregunto "¿estas bien?" varias veces y nadie respondio: lo ve la familia (si la persona lo permitio). */
    fun bienestarSinConfirmar(ctx: Context) {
        if (!ajustes(ctx).activo) return
        editar(ctx) { putString("bienestarEstado", "sin_confirmar"); putLong("bienestarHora", System.currentTimeMillis()) }
        SpikeLog.log("CIRCULO: bienestar sin confirmar")
        sincronizarAsync(ctx)
    }

    /** La persona volvio o respondio despues de un "sin confirmar": se actualiza a "ok". */
    fun bienestarOk(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean("activo", false) || p.getString("bienestarEstado", "") != "sin_confirmar") return
        editar(ctx) { putString("bienestarEstado", "ok"); putLong("bienestarHora", System.currentTimeMillis()) }
        sincronizarAsync(ctx)
    }

    fun limpiarSos(ctx: Context) {
        if (!prefs(ctx).contains("sosHora")) return
        editar(ctx) { remove("sosHora"); remove("sosLat"); remove("sosLng") }
        sincronizarAsync(ctx)
    }

    private suspend fun sesion(): String {
        val auth = Firebase.auth
        auth.currentUser?.let { return it.uid }
        return withTimeout(15_000) { auth.signInAnonymously().aguardar() }.user?.uid ?: error("sin sesion")
    }

    /** Escribe en la nube SOLO lo que esta activado; lo demas se borra (el documento se reemplaza entero). */
    suspend fun sincronizar(ctx: Context) {
        val a = ajustes(ctx)
        if (!a.activo || a.codigo.isBlank()) return
        val uid = sesion()
        val p = prefs(ctx)
        val ahora = System.currentTimeMillis()
        val m = hashMapOf<String, Any?>(
            "owner" to uid, "nombre" to a.nombre, "actualizado" to ahora,
            "compartir" to mapOf("ubicacion" to a.ubicacion, "actividad" to a.actividad, "medicinas" to a.medicinas, "checkin" to a.checkin, "bienestar" to a.bienestar),
            "idioma" to Idioma.resolver(ctx),
        )
        if (a.actividad) m["ultimaActividad"] = p.getLong("toque", ahora)
        if (a.medicinas) {
            m["medicinas"] = Recordatorios.historial(ctx).filter { it.tipo == Recordatorios.Tipo.MEDICINA }.take(12).map {
                mapOf("texto" to it.texto, "estado" to it.estado.name.lowercase(), "hora" to it.hora, "programada" to it.programada)
            }
        }
        if (a.ubicacion) Ubicacion.actual(ctx)?.let {
            m["ubicacion"] = mapOf("lat" to it.latitude, "lng" to it.longitude, "hora" to ahora)
        }
        if (a.checkin) Recordatorios.historial(ctx).firstOrNull { it.tipo == Recordatorios.Tipo.CHECKIN }?.let {
            m["checkin"] = mapOf("estado" to when (it.estado) {
                Recordatorios.Estado.TOMADO -> "ok"
                Recordatorios.Estado.AYUDA -> "ayuda"
                Recordatorios.Estado.OMITIDO -> "sin_respuesta"
                else -> "pendiente"
            }, "hora" to it.hora, "programada" to it.programada)
        }
        // "No pude confirmar que esta bien": solo si ella lo permitio y de las ultimas 24 horas (ver ModoDockActivity).
        val bienHora = p.getLong("bienestarHora", 0)
        if (a.bienestar && bienHora > 0 && ahora - bienHora < SOS_VIGENCIA_MS) {
            m["bienestar"] = mapOf("estado" to p.getString("bienestarEstado", "sin_confirmar"), "hora" to bienHora)
        }
        val sosHora = p.getLong("sosHora", 0)
        if (sosHora > 0 && ahora - sosHora < SOS_VIGENCIA_MS) {
            val sos = hashMapOf<String, Any?>("hora" to sosHora)
            Cifrado.abrir(p.getString("sosLat", null))?.toDoubleOrNull()?.let { sos["lat"] = it }
            Cifrado.abrir(p.getString("sosLng", null))?.toDoubleOrNull()?.let { sos["lng"] = it }
            m["sos"] = sos
        }
        withTimeout(20_000) { Firebase.firestore.collection("circulos").document(a.codigo).set(m).aguardar() }
        p.edit().putLong("ultimaSync", ahora).apply()
        SpikeLog.log("CIRCULO: sincronizado (${m.keys.sorted()})")
    }

    fun ultimaSync(ctx: Context) = prefs(ctx).getLong("ultimaSync", 0)
}

/** Espera una Task de Google sin depender de kotlinx-coroutines-play-services. */
suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t ->
        if (t.isSuccessful) c.resume(t.result) else c.resumeWithException(t.exception ?: RuntimeException("tarea fallida"))
    }
}

/** Ubicacion aproximada, solo cuando se pide (sin servicios de Google ni rastreo en segundo plano). */
object Ubicacion {
    @SuppressLint("MissingPermission")
    suspend fun actual(ctx: Context): Location? {
        if (!Circulo.tieneUbicacion(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        val proveedores = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
            try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            for (p in proveedores) {
                val l = withTimeoutOrNull(7_000) {
                    suspendCancellableCoroutine<Location?> { c ->
                        lm.getCurrentLocation(p, null, ctx.mainExecutor) { c.resume(it) }
                    }
                }
                if (l != null) return l
            }
        }
        // Respaldo: la ultima conocida, si no es muy vieja.
        val limite = System.currentTimeMillis() - 6L * 60 * 60 * 1000
        return (proveedores + LocationManager.PASSIVE_PROVIDER).mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (e: Exception) { null }
        }.filter { it.time >= limite }.maxByOrNull { it.time }
    }
}
