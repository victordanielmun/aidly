package com.aidly.app

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/**
 * Cerebro de "Hablar con Aidly" (tipo Siri/Alexa): decide que hacer con lo que dijo la persona.
 *  - analizar (camara), leer medicina, correo, mensajes, abrir apps: reglas locales (instantaneas, sin red,
 *    sin enviar tu correo a la nube);
 *  - todo lo demas: conversacion corta con el modelo hibrido (AsistenteVoz).
 * Las acciones que abren otras apps solo funcionan con Aidly al frente (Android 10+ lo exige).
 */
object Agente {
    /** [despues] se ejecuta cuando Aidly termina de hablar (por ejemplo, abrir otra app). */
    class Respuesta(val texto: String, val fuente: String, val ms: Long, val despues: (() -> Unit)? = null)

    /** [busca]: la app acepta una busqueda o destino; si no, solo se abre. */
    private enum class App(val nombre: String, val pkg: String, val busca: Boolean) {
        SPOTIFY("Spotify", "com.spotify.music", true),
        YOUTUBE("YouTube", "com.google.android.youtube", true),
        NETFLIX("Netflix", "com.netflix.mediaclient", true),
        MAPS("Google Maps", "com.google.android.apps.maps", true),
        PRIME("Prime Video", "com.amazon.avod.thirdpartyclient", false),
        DISNEY("Disney Plus", "com.disney.disneyplus", false),
        WHATSAPP("WhatsApp", "com.whatsapp", false),
        FACEBOOK("Facebook", "com.facebook.katana", false),
    }

    private fun t(ctx: Context, id: Int, vararg a: Any) = Idioma.vozTexto(ctx, id, *a)

    suspend fun procesar(ctx: Context, frase: String): Respuesta {
        val n = normalizar(frase)
        val L = Lexico.de(ctx)
        val r: Respuesta = when {
            L.cancelarRec.containsMatchIn(n) && L.objRec.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=cancelar recordatorio"); cancelarRecordatorio(ctx, n, L) }
            L.crearRec.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=crear recordatorio"); crearRecordatorio(ctx, frase) }
            L.objRec.containsMatchIn(n) && L.listarRec.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=listar recordatorios"); listarRecordatorios(ctx) }
            L.sos.containsMatchIn(n) && !L.llamar.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=pedir ayuda"); pedirAyuda(ctx) }
            L.estoyBien.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=estoy bien"); estoyBien(ctx) }
            L.tomado.containsMatchIn(n) && MedicinaValidador.respuestaSegura(ctx, frase) == null -> { SpikeLog.log("AGENTE: ruta=ya lo hice"); yaLoHice(ctx) }
            L.crearCita.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=agendar cita"); crearCita(ctx, frase) }
            L.verAgenda.containsMatchIn(n) && L.cuandoAgenda.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=ver agenda"); verAgenda(ctx, n) }
            L.horaFecha.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=hora/fecha"); horaFecha(ctx, n, L) }
            L.modoDock.containsMatchIn(n) -> {
                SpikeLog.log("AGENTE: ruta=modo dock")
                pantalla(ctx, ModoDockActivity::class.java, t(ctx, R.string.v_modo_dock))
            }
            L.medicina.containsMatchIn(n) && L.leerAlgo.containsMatchIn(n) -> {
                SpikeLog.log("AGENTE: ruta=medicina")
                pantalla(ctx, MedicinaActivity::class.java, t(ctx, R.string.v_pantalla_medicina))
            }
            L.analiza.containsMatchIn(n) -> {
                SpikeLog.log("AGENTE: ruta=analiza")
                pantalla(ctx, AnalizaActivity::class.java, t(ctx, R.string.v_pantalla_analiza))
            }
            L.llamar.containsMatchIn(n) || (L.abrirExpl.containsMatchIn(n) && L.telefono.containsMatchIn(n)) -> {
                SpikeLog.log("AGENTE: ruta=llamar"); llamar(ctx, n, L)
            }
            L.camara.containsMatchIn(n) && (L.abrirExpl.containsMatchIn(n) || L.fotoVerbo.containsMatchIn(n)) -> {
                SpikeLog.log("AGENTE: ruta=camara")
                Respuesta(t(ctx, R.string.v_camara_abriendo), "local", 0) {
                    try { ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { SpikeLog.log("AGENTE: no pude abrir la camara: ${e.message}") }
                }
            }
            L.correo.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=correo"); leerCorreo(ctx) }
            L.abrirExpl.containsMatchIn(n) && "whatsapp" in n && !L.mensajes.containsMatchIn(n.replace("whatsapp", "")) -> {
                SpikeLog.log("AGENTE: ruta=app WhatsApp")
                abrir(ctx, App.WHATSAPP, "")
            }
            L.mensajes.containsMatchIn(n) ->
                if (L.enviar.containsMatchIn(n)) {
                    SpikeLog.log("AGENTE: ruta=enviar (no soportado)")
                    Respuesta(t(ctx, R.string.v_enviar_no), "local", 0)
                } else { SpikeLog.log("AGENTE: ruta=mensajes"); leerMensajes(ctx) }
            else -> detectarApp(ctx, n, L)?.let { (app, q) ->
                SpikeLog.log("AGENTE: ruta=app ${app.nombre} busqueda='$q'")
                abrir(ctx, app, q)
            } ?: MedicinaValidador.respuestaSegura(ctx, frase)?.let { segura ->
                // Sobredosis, alergia, sintomas o dosis olvidada: nunca los responde el modelo.
                SpikeLog.log("AGENTE: ruta=medicina riesgo (respuesta fija)")
                Respuesta(segura, "local", 0)
            } ?: if (L.buscarWeb.containsMatchIn(n)) {
                SpikeLog.log("AGENTE: ruta=busqueda web")
                if (!Privacidad.nubeActiva()) return Respuesta(t(ctx, R.string.v_busqueda_sin_nube), "local", 0)
                try {
                    val b = Busqueda.responder(ctx, frase)
                    Respuesta(b.texto, "web", b.ms)
                } catch (e: Exception) {
                    // Cuota agotada o sin internet: se abre Google con la pregunta, en lugar de quedarse sin respuesta.
                    SpikeLog.log("AGENTE: busqueda fallo (${e::class.simpleName}); abro Google")
                    Respuesta(t(ctx, R.string.v_busqueda_fallo), "local", 0) {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(frase)}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (ex: Exception) { SpikeLog.log("AGENTE: no pude abrir Google: ${ex.message}") }
                    }
                }
            } else run {
                SpikeLog.log("AGENTE: ruta=conversacion")
                val c = try {
                    AsistenteVoz.responder(ctx, frase)
                } catch (e: Exception) {
                    if (Privacidad.nubeActiva()) throw e
                    SpikeLog.log("AGENTE: conversacion sin nube y sin modelo local (${e::class.simpleName})")
                    return Respuesta(t(ctx, R.string.v_conv_sin_nube), "local", 0)
                }
                Respuesta(c.texto, c.fuente, c.ms)
            }
        }
        return r
    }

    private fun pantalla(ctx: Context, destino: Class<*>, texto: String) =
        Respuesta(texto, "local", 0) {
            ctx.startActivity(Intent(ctx, destino).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

    // ---------- Mensajes y correo (bandeja local) ----------

    private fun leerMensajes(ctx: Context): Respuesta {
        val p = Bandeja.pendientes(ctx, Bandeja.Tipo.MENSAJE)
        if (p.isEmpty()) return Respuesta(t(ctx, R.string.v_msj_ninguno), "local", 0)
        val lote = p.take(5).reversed()                      // los 5 mas recientes, en orden cronologico
        val encabezado = if (p.size == 1) t(ctx, R.string.v_msj_uno) else t(ctx, R.string.v_msj_varios, p.size)
        val alguien = t(ctx, R.string.v_alguien)
        val cuerpo = lote.joinToString(" ") { t(ctx, R.string.v_msj_item, it.remitente.ifBlank { alguien }, it.texto) }
        val resto = if (p.size > lote.size) " " + t(ctx, R.string.v_y_mas, p.size - lote.size) else ""
        Bandeja.marcarLeidos(ctx, p.map { it.id })
        return Respuesta("$encabezado $cuerpo$resto", "local", 0)
    }

    private fun leerCorreo(ctx: Context): Respuesta {
        val p = Bandeja.pendientes(ctx, Bandeja.Tipo.CORREO)
        if (p.isEmpty()) return Respuesta(t(ctx, R.string.v_correo_ninguno), "local", 0)
        val lote = p.take(5).reversed()
        val encabezado = if (p.size == 1) t(ctx, R.string.v_correo_uno) else t(ctx, R.string.v_correo_varios, p.size)
        val desconocido = t(ctx, R.string.v_remitente_desconocido)
        val cuerpo = lote.joinToString(" ") { c ->
            val extra = if (lote.size == 1 && c.detalle.isNotBlank()) " ${c.detalle.take(200)}" else ""
            t(ctx, R.string.v_correo_item, c.remitente.ifBlank { desconocido }, c.texto) + extra
        }
        val resto = if (p.size > lote.size) " " + t(ctx, R.string.v_y_mas, p.size - lote.size) else ""
        Bandeja.marcarLeidos(ctx, p.map { it.id })
        return Respuesta("$encabezado $cuerpo$resto", "local", 0)
    }

    // ---------- Recordatorios y medicinas ----------

    private suspend fun crearRecordatorio(ctx: Context, frase: String): Respuesta {
        val t0 = System.currentTimeMillis()
        val item = Recordatorios.interpretar(frase)
            ?: return Respuesta(t(ctx, R.string.v_rec_no_entendi), "local", System.currentTimeMillis() - t0)
        Recordatorios.agregar(ctx, item)
        val sinPermiso = android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val aviso = if (sinPermiso) " " + t(ctx, R.string.v_rec_aviso_notif) else ""
        return Respuesta(t(ctx, R.string.v_rec_listo, item.texto, Recordatorios.describir(item)) + aviso, "local", System.currentTimeMillis() - t0)
    }

    private fun listarRecordatorios(ctx: Context): Respuesta {
        val l = Recordatorios.listar(ctx)
        if (l.isEmpty()) return Respuesta(t(ctx, R.string.v_rec_ninguno), "local", 0)
        val cuerpo = l.take(5).joinToString(" ") { t(ctx, R.string.v_rec_item, it.texto, Recordatorios.describir(it)) }
        val resto = if (l.size > 5) " " + t(ctx, R.string.v_y_mas, l.size - 5) else ""
        val cabeza = if (l.size == 1) t(ctx, R.string.v_rec_tienes_uno) else t(ctx, R.string.v_rec_tienes_n, l.size)
        return Respuesta("$cabeza $cuerpo$resto", "local", 0)
    }

    private fun cancelarRecordatorio(ctx: Context, n: String, L: Lexico): Respuesta {
        val l = Recordatorios.listar(ctx)
        if (l.isEmpty()) return Respuesta(t(ctx, R.string.v_rec_cancelar_ninguno), "local", 0)
        if (L.todos.containsMatchIn(n)) {
            l.forEach { Recordatorios.cancelar(ctx, it.id) }
            return Respuesta(t(ctx, R.string.v_rec_borre_todos), "local", 0)
        }
        val palabras = n.split(" ").filter { it.length > 2 && it !in L.ruidoCancelar }
        val coinciden = if (palabras.isEmpty()) (if (l.size == 1) l else emptyList())
        else l.filter { r -> val tx = normalizar(r.texto); palabras.any { it in tx } }
        if (coinciden.isEmpty()) {
            val cuales = l.take(4).joinToString(", ") { it.texto }
            return Respuesta(t(ctx, R.string.v_rec_cual, cuales), "local", 0)
        }
        coinciden.forEach { Recordatorios.cancelar(ctx, it.id) }
        return Respuesta(t(ctx, R.string.v_rec_borre, coinciden.joinToString(t(ctx, R.string.v_y)) { it.texto }), "local", 0)
    }

    /** "Auxilio", "me siento mal": avisa a la familia (con ubicacion) y deja el numero de emergencias a un toque del boton verde. */
    private fun pedirAyuda(ctx: Context): Respuesta {
        if (!Circulo.ajustes(ctx).activo) {
            return Respuesta(t(ctx, R.string.v_ayuda_sin_familia, Emergencia.habla(ctx)), "local", 0) { marcador(ctx, Emergencia.numero(ctx)) }
        }
        val checkins = Recordatorios.pendientes(ctx).filter { it.tipo == Recordatorios.Tipo.CHECKIN }
        if (checkins.isEmpty()) Circulo.sosAsync(ctx)
        else checkins.forEach { Recordatorios.marcar(ctx, it.id, Recordatorios.Estado.AYUDA) }   // esto tambien avisa
        return Respuesta(t(ctx, R.string.v_ayuda_listo, Emergencia.habla(ctx)), "local", 0) { marcador(ctx, Emergencia.numero(ctx)) }
    }

    private fun estoyBien(ctx: Context): Respuesta {
        Recordatorios.pendientes(ctx).filter { it.tipo == Recordatorios.Tipo.CHECKIN }
            .forEach { Recordatorios.marcar(ctx, it.id, Recordatorios.Estado.TOMADO) }
        Circulo.limpiarSos(ctx)
        return Respuesta(t(ctx, R.string.v_estoy_bien), "local", 0)
    }

    private fun yaLoHice(ctx: Context): Respuesta {
        val p = Recordatorios.pendientes(ctx)
        if (p.isEmpty()) return Respuesta(t(ctx, R.string.v_nada_pendiente), "local", 0)
        p.forEach { Recordatorios.marcar(ctx, it.id, Recordatorios.Estado.TOMADO) }
        return Respuesta(t(ctx, R.string.v_anotado), "local", 0)
    }

    // ---------- Calendario, fecha y hora ----------

    private suspend fun crearCita(ctx: Context, frase: String): Respuesta {
        val t0 = System.currentTimeMillis()
        val e = Agenda.interpretar(frase)
            ?: return Respuesta(t(ctx, R.string.v_cita_no_entendi), "local", System.currentTimeMillis() - t0)
        val guardada = Agenda.guardar(ctx, e)
        val aviso = Agenda.avisoPrevio(ctx, e)?.also { Recordatorios.agregar(ctx, it) }
        val cuando = Recordatorios.describir(Recordatorios.Item("", e.titulo, Recordatorios.Tipo.GENERAL, e.inicio, 0))
        val ms = System.currentTimeMillis() - t0
        val avisoTxt = if (aviso != null) " " + t(ctx, R.string.v_cita_aviso) else ""
        return if (guardada) {
            Respuesta(t(ctx, R.string.v_cita_listo, e.titulo, cuando) + avisoTxt, "local", ms)
        } else {
            Respuesta(t(ctx, R.string.v_cita_abre, e.titulo, cuando, avisoTxt), "local", ms) {
                try { ctx.startActivity(Agenda.intentInsertar(e)) }
                catch (ex: Exception) { SpikeLog.log("AGENTE: no pude abrir el calendario: ${ex.message}") }
            }
        }
    }

    private fun verAgenda(ctx: Context, n: String): Respuesta {
        val (desde, hasta, etiqueta) = Agenda.rango(ctx, n)
        val citas = Agenda.citas(ctx, desde, hasta)
        val avisos = Recordatorios.listar(ctx).filter { it.proxima in desde until hasta }
        fun cuando(tm: Long) = Recordatorios.describir(Recordatorios.Item("", "", Recordatorios.Tipo.GENERAL, tm, 0))
        val partes = citas.take(6).map { "${it.titulo} ${cuando(it.inicio)}" } +
            avisos.take(6).map { t(ctx, R.string.v_agenda_recordatorio, it.texto, cuando(it.proxima)) }
        val sinPermiso = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        val nota = if (sinPermiso) " " + t(ctx, R.string.v_agenda_permiso) else ""
        if (partes.isEmpty()) return Respuesta(t(ctx, R.string.v_agenda_ninguna, etiqueta) + nota, "local", 0)
        return Respuesta(t(ctx, R.string.v_agenda_tienes, partes.size, etiqueta, partes.joinToString(". ")) + nota, "local", 0)
    }

    private fun horaFecha(ctx: Context, n: String, L: Lexico): Respuesta {
        val c = java.util.Calendar.getInstance()
        if (L.preguntaHora.containsMatchIn(n)) {
            val h = Recordatorios.horaHablada(c)
            val dicho = if (L.id == "es") h.replaceFirst("a las", "Son las").replaceFirst("a la", "Es la") + "." else t(ctx, R.string.v_hora, h)
            return Respuesta(dicho, "local", 0)
        }
        val f = if (L.id == "es") java.text.SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(c.time)
        else java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(c.time)
        return Respuesta(t(ctx, R.string.v_hoy_es, f), "local", 0)
    }

    // ---------- Telefono ----------

    /** Abre el marcador con el numero ya escrito: la persona toca el boton verde (nunca se llama sola). */
    private fun marcador(ctx: Context, numero: String) {
        try {
            val uri = if (numero.isBlank()) Uri.parse("tel:") else Uri.fromParts("tel", numero, null)
            ctx.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            SpikeLog.log("AGENTE: no pude abrir el marcador: ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun llamar(ctx: Context, n: String, L: Lexico): Respuesta {
        if (L.emergencia.containsMatchIn(n)) {
            return Respuesta(t(ctx, R.string.v_llamar_emergencia, Emergencia.habla(ctx)), "local", 0) { marcador(ctx, Emergencia.numero(ctx)) }
        }
        Regex("\\d[\\d ]*\\d").find(n)?.value?.replace(" ", "")?.takeIf { it.length >= 3 }?.let { num ->
            return Respuesta(t(ctx, R.string.v_llamar_emergencia, num.toList().joinToString(" ")), "local", 0) { marcador(ctx, num) }
        }
        val nombre = L.nombreLlamar.find(n)?.groupValues?.get(1).orEmpty()
            .replace(L.porFavor, "")
            .split(" ").filter { it.isNotBlank() && it !in L.ruidoLlamar }
            .joinToString(" ")
        if (nombre.isBlank()) return Respuesta(t(ctx, R.string.v_llamar_teclado), "local", 0) {
            ctx.startActivity(Intent(ctx, MarcarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        Favoritos.buscar(ctx, nombre, ::normalizar)?.let { f ->
            return Respuesta(t(ctx, R.string.v_llamar_fav, f.nombre), "local", 0) { marcador(ctx, f.numero) }
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Respuesta(t(ctx, R.string.v_llamar_permiso, nombre), "local", 0) { marcador(ctx, "") }
        }
        val c = buscarContacto(ctx, nombre)
            ?: return Respuesta(t(ctx, R.string.v_llamar_no_encontre, nombre), "local", 0) { marcador(ctx, "") }
        return Respuesta(t(ctx, R.string.v_llamar_fav, c.first), "local", 0) { marcador(ctx, c.second) }
    }

    /** Devuelve (nombre, numero) del contacto cuyo nombre contiene todas las palabras pedidas; el mas corto gana. */
    private fun buscarContacto(ctx: Context, pedido: String): Pair<String, String>? {
        val palabras = pedido.split(" ").filter { it.isNotBlank() }
        var mejor: Pair<String, String>? = null
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val nom = cur.getString(0).orEmpty()
                    val num = cur.getString(1).orEmpty()
                    val nn = normalizar(nom)
                    if (num.isNotBlank() && palabras.all { it in nn } && (mejor == null || nom.length < mejor!!.first.length)) {
                        mejor = nom to num
                    }
                }
            }
        } catch (e: Exception) {
            SpikeLog.log("AGENTE: error leyendo contactos: ${e::class.simpleName}: ${e.message}")
        }
        return mejor
    }

    // ---------- Abrir apps ----------

    private fun instalada(ctx: Context, app: App) = ctx.packageManager.getLaunchIntentForPackage(app.pkg) != null

    private fun detectarApp(ctx: Context, n: String, L: Lexico): Pair<App, String>? {
        val app = L.nombresApps.firstOrNull { it.first.containsMatchIn(n) }?.second?.let { App.valueOf(it) }
            ?: when {
                L.quiereIr.containsMatchIn(n) -> App.MAPS
                L.verboAbrir.containsMatchIn(n) && L.quiereMusica.containsMatchIn(n) -> App.SPOTIFY
                L.verboAbrir.containsMatchIn(n) && L.quierePeli.containsMatchIn(n) ->
                    if (instalada(ctx, App.NETFLIX)) App.NETFLIX else App.YOUTUBE
                L.verboAbrir.containsMatchIn(n) && L.quiereVideo.containsMatchIn(n) -> App.YOUTUBE
                else -> return null
            }
        return app to extraerBusqueda(n, app, L)
    }

    private fun extraerBusqueda(n: String, app: App, L: Lexico): String {
        if (!app.busca) return ""
        val texto = n.replace(L.sufijoApp, "").replace(Regex("\\s+(" + L.porFavor.pattern.removePrefix("\\b").removeSuffix("\\b") + ")$"), "")
        if (app == App.MAPS) {
            L.destinoMapa.find(texto)?.let { return it.groupValues[1].trim() }
        }
        L.tituloBusqueda.find(texto)?.let { return it.groupValues[1].trim() }
        return texto.split(" ").filter { it.isNotBlank() && it !in L.relleno }.joinToString(" ")
    }

    private fun abrir(ctx: Context, app: App, busqueda: String): Respuesta {
        if (!instalada(ctx, app)) {
            return Respuesta(t(ctx, R.string.v_app_no_instalada, app.nombre), "local", 0)
        }
        val texto = when {
            busqueda.isBlank() -> t(ctx, R.string.v_abriendo, app.nombre)
            app == App.MAPS -> t(ctx, R.string.v_te_llevo, busqueda, app.nombre)
            else -> t(ctx, R.string.v_buscando, busqueda, app.nombre)
        }
        return Respuesta(texto, "local", 0) { lanzar(ctx, app, busqueda) }
    }

    private fun lanzar(ctx: Context, app: App, q: String) {
        val nueva = Intent.FLAG_ACTIVITY_NEW_TASK
        val abrirApp = { ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.addFlags(nueva)?.let(ctx::startActivity) }
        try {
            if (q.isBlank() || !app.busca) { abrirApp(); return }
            val intent = when (app) {
                App.SPOTIFY -> Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(SearchManager.QUERY, q)
                }
                App.YOUTUBE -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))
                App.NETFLIX -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/search?q=${Uri.encode(q)}"))
                App.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(q)}"))
                else -> { abrirApp(); return }
            }
            ctx.startActivity(intent.setPackage(app.pkg).addFlags(nueva))
            SpikeLog.log("AGENTE: lanzada ${app.nombre} con busqueda '$q'")
        } catch (e: ActivityNotFoundException) {
            SpikeLog.log("AGENTE: ${app.nombre} no acepto la busqueda; abro la app (${e.message})")
            try { abrirApp() } catch (_: Exception) {}
        } catch (e: Exception) {
            SpikeLog.log("AGENTE: no pude abrir ${app.nombre}: ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun normalizar(s: String) =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
}
