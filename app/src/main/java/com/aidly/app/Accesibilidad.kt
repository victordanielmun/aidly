package com.aidly.app

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Estado y preferencias de la "Ayuda con otras apps" (servicio de accesibilidad [AidlyAccesibilidad]). */
object Accesibilidad {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** La persona la encendio en Ajustes > Accesibilidad (el servicio esta conectado o figura como activado). */
    fun activa(ctx: Context): Boolean {
        if (AidlyAccesibilidad.instancia != null) return true
        val activos = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return activos.split(':').any { it.equals("${ctx.packageName}/${AidlyAccesibilidad::class.java.name}", ignoreCase = true) || it.endsWith("/.AidlyAccesibilidad") && it.startsWith(ctx.packageName) }
    }

    /** Boton flotante: encendido por defecto una vez activada la ayuda; la persona lo puede quitar y volver a poner. */
    fun botonActivo(ctx: Context) = prefs(ctx).getBoolean("botonFlotante", true)
    fun fijarBoton(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("botonFlotante", v).apply()
        AidlyAccesibilidad.instancia?.actualizarBoton()
    }

    fun abrirAjustes(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun abrirPantallaDeAyuda(ctx: Context) = ctx.startActivity(Intent(ctx, AyudaOtrasAppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Una orden de la persona sobre la app que esta usando. [accion]: atras, inicio, bajar, subir o tocar (con [texto]). false si no se pudo. */
    fun ejecutar(accion: String, texto: String = ""): Boolean {
        val s = AidlyAccesibilidad.instancia ?: return false
        return when (accion) {
            "atras" -> s.atras()
            "inicio" -> s.inicio()
            "bajar" -> s.desplazar(true)
            "subir" -> s.desplazar(false)
            "tocar" -> texto.isNotBlank() && s.tocarTexto(texto)
            else -> false
        }
    }
}

/** Apps instaladas con icono de lanzamiento, para "abre <nombre>" con cualquiera (no solo las de una lista fija). */
object AppsInstaladas {
    fun lanzables(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.filter { it.second != ctx.packageName }
    }

    /** La app cuyo nombre coincide con [pedido] (sin tildes ni mayusculas): exacto, que empiece por el o que lo contenga, en ese orden. */
    fun buscar(ctx: Context, pedido: String): Pair<String, String>? {
        val q = normalizarTexto(pedido)
        if (q.length < 3) return null
        val apps = lanzables(ctx).map { Triple(normalizarTexto(it.first), it.first, it.second) }
        return (apps.firstOrNull { it.first == q } ?: apps.firstOrNull { it.first.startsWith(q) } ?: apps.firstOrNull { it.first.contains(q) })?.let { it.second to it.third }
    }

    fun lanzar(ctx: Context, paquete: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }
}
