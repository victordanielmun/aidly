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

    /** Como se nombra cada app en voz (el texto ya viene sin acentos ni signos). */
    private val NOMBRES = listOf(
        Regex("\\bspotify\\b") to App.SPOTIFY,
        Regex("\\b(youtube|you tube)\\b") to App.YOUTUBE,
        Regex("\\bnetflix\\b") to App.NETFLIX,
        Regex("\\b(prime video|amazon prime|prime)\\b") to App.PRIME,
        Regex("\\bdisney\\b") to App.DISNEY,
        Regex("\\b(google maps|maps|mapa|mapas)\\b") to App.MAPS,
        Regex("\\bfacebook\\b") to App.FACEBOOK,
    )

    private val CORREO = Regex("\\b(correo|correos|mail|mails|email|emails|gmail|outlook)\\b")
    private val MENSAJES = Regex("\\b(mensaje|mensajes|whatsapp|wasap|guasap)\\b")
    private val ENVIAR = Regex("\\b(envia|enviar|manda|mandar|escribe|escribir|responde|responder|contesta|contestar)\\b")
    private val ABRIR_EXPL = Regex("\\b(abre|abrir|ejecuta|inicia|iniciar)\\b")
    private val VERBO_ABRIR = Regex("\\b(abre|abrir|pon|ponme|poner|busca|buscar|quiero|reproduce|reproducir|muestrame|ensename|escuchar|ver|mirar)\\b")
    private val QUIERE_MUSICA = Regex("\\b(musica|cancion|canciones|disco|cantante|playlist|escuchar)\\b")
    private val QUIERE_VIDEO = Regex("\\b(video|videos|tutorial|tutoriales|ver|mirar)\\b")
    private val QUIERE_PELI = Regex("\\b(pelicula|peliculas|serie|series|novela|novelas)\\b")
    private val QUIERE_IR = Regex("\\b(llevame|navega|navegar|como llego|como llegar|ruta)\\b")
    private val MEDICINA = Regex("\\b(medicina|medicinas|medicamento|medicamentos|pastilla|pastillas|remedio|remedios)\\b")
    private val LEER_ALGO = Regex("\\b(lee|leer|leeme|analiza|analizar|revisa|revisar|que dice|que es|para que sirve|que son)\\b")
    private val ANALIZA = Regex(
        "\\b(analiza|analizar|analizame|analicemos)\\b|\\b(que|cual) dice (esta|este|esto|el|la)\\b|" +
            "\\b(que|cual) dice (aqui|ahi|alli|alla)\\b|\\bque (es|son) (esto|eso|estas cosas)\\b|" +
            "\\b(es|sera|parece) (una )?estafa\\b|\\b(revisa|revisame|lee|leeme) (esta|este|esto|estos|estas)\\b",
    )
    private val CREAR_REC = Regex(
        "\\b(recuerdame|recordarme|recuerdamelo|ponme (una )?alarma|pon (una )?alarma|programa (una )?alarma|" +
            "(crea|creame|agrega|agregame|anade|anademe|programa|pon|ponme) (un )?recordatorio)\\b|" +
            "\\bavisame (a|en|manana|hoy|todos|cada|el|que|para|cuando sean)\\b",
    )
    private val OBJ_REC = Regex("\\b(recordatorio|recordatorios|alarma|alarmas|aviso|avisos)\\b")
    private val LISTAR_REC = Regex("\\b(que|cuales|cuantos|cuantas|lista|tengo|muestrame|dime|revisa)\\b")
    private val CANCELAR_REC = Regex("\\b(cancela|cancelar|borra|borrar|elimina|eliminar|quita|quitar)\\b")
    private val TOMADO = Regex("\\b(ya (me )?(tome|tomo)|ya la tome|ya lo tome|ya las tome|ya los tome|ya lo hice|ya esta hecho)\\b")
    private val SOS = Regex(
        "\\b(me siento mal|no me siento bien|me senti mal|me cai|auxilio|socorro|emergencia|me duele el pecho|" +
            "no puedo respirar|avisa a mi familia|avisale a mi familia|avisales a mis familiares)\\b",
    )
    private val ESTOY_BIEN = Regex("\\b(estoy bien|me siento bien|ya estoy bien)\\b")
    private val CREAR_CITA = Regex(
        "\\b(agendame|agendar|agenda (una|un|la|el|mi)|apuntame (una|un|la|el)|anota (una|un|la|el) (cita|consulta|evento)|" +
            "programame (una|un) (cita|consulta))\\b",
    )
    private val VER_AGENDA = Regex("\\b(que (tengo|hay)( agendado| pendiente| programado| para)?|mi agenda|mis citas|que citas tengo|tengo (alguna )?cita)\\b")
    private val CUANDO_AGENDA = Regex("\\b(hoy|manana|semana|agenda|citas|cita|agendado|pendiente|programado)\\b")
    private val HORA_FECHA = Regex("\\b(que hora es|que dia es|que fecha es|a cuanto estamos|que dia es hoy)\\b")
    private val BUSCAR_WEB = Regex(
        "\\b(internet|google|noticias|clima|pronostico|llover|lluvia|temperatura|dolar|euro|precio|cuanto cuesta|cuanto vale|" +
            "resultado del|quien gano|horario de|donde queda|receta|que tiempo hace|averigua|investiga)\\b",
    )
    private val LLAMAR = Regex("\\b(llama|llamar|llamale|marca|marcar|comunicame|telefonea)\\b")
    private val TELEFONO = Regex("\\b(telefono|marcador|teclado)\\b")
    private val CAMARA = Regex("\\b(camara|foto|fotos|fotografia)\\b")
    private val FOTO_VERBO = Regex("\\b(toma|tomar|tomame|saca|sacar|sacame|hacer|haz|quiero)\\b")
    private val EMERGENCIA = Regex("\\b(emergencia|ambulancia|policia|bomberos|auxilio|socorro)\\b")
    private val RELLENO = setOf(
        "abre", "abrir", "pon", "ponme", "poner", "busca", "buscar", "quiero", "reproduce", "reproducir",
        "escuchar", "ver", "mirar", "spotify", "youtube", "you", "tube", "netflix", "prime", "amazon", "disney",
        "maps", "mapa", "mapas", "google", "en", "un", "una", "unos", "unas", "musica", "cancion", "canciones",
        "video", "videos", "pelicula", "peliculas", "serie", "series", "por", "favor", "me", "puedes", "podrias",
        "aidly", "oye", "hola", "para", "mi", "algo", "muestrame", "ensename", "llevame", "navega", "ruta",
    )

    suspend fun procesar(ctx: Context, frase: String): Respuesta {
        val n = normalizar(frase)
        val r: Respuesta = when {
            CANCELAR_REC.containsMatchIn(n) && OBJ_REC.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=cancelar recordatorio"); cancelarRecordatorio(ctx, n) }
            CREAR_REC.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=crear recordatorio"); crearRecordatorio(ctx, frase) }
            OBJ_REC.containsMatchIn(n) && LISTAR_REC.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=listar recordatorios"); listarRecordatorios(ctx) }
            SOS.containsMatchIn(n) && !LLAMAR.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=pedir ayuda"); pedirAyuda(ctx) }
            ESTOY_BIEN.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=estoy bien"); estoyBien(ctx) }
            TOMADO.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=ya lo hice"); yaLoHice(ctx) }
            CREAR_CITA.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=agendar cita"); crearCita(ctx, frase) }
            VER_AGENDA.containsMatchIn(n) && CUANDO_AGENDA.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=ver agenda"); verAgenda(ctx, n) }
            HORA_FECHA.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=hora/fecha"); horaFecha(n) }
            MEDICINA.containsMatchIn(n) && LEER_ALGO.containsMatchIn(n) -> {
                SpikeLog.log("AGENTE: ruta=medicina")
                pantalla(ctx, MedicinaActivity::class.java, "Claro. Vamos a leer tu medicina.")
            }
            ANALIZA.containsMatchIn(n) -> {
                SpikeLog.log("AGENTE: ruta=analiza")
                pantalla(ctx, AnalizaActivity::class.java, "Claro. Apunta la cámara a lo que quieres que lea.")
            }
            LLAMAR.containsMatchIn(n) || (ABRIR_EXPL.containsMatchIn(n) && TELEFONO.containsMatchIn(n)) -> {
                SpikeLog.log("AGENTE: ruta=llamar"); llamar(ctx, n)
            }
            CAMARA.containsMatchIn(n) && (ABRIR_EXPL.containsMatchIn(n) || FOTO_VERBO.containsMatchIn(n)) -> {
                SpikeLog.log("AGENTE: ruta=camara")
                Respuesta("Abriendo la cámara.", "local", 0) {
                    try { ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { SpikeLog.log("AGENTE: no pude abrir la camara: ${e.message}") }
                }
            }
            CORREO.containsMatchIn(n) -> { SpikeLog.log("AGENTE: ruta=correo"); leerCorreo(ctx) }
            ABRIR_EXPL.containsMatchIn(n) && "whatsapp" in n && "mensaje" !in n -> {
                SpikeLog.log("AGENTE: ruta=app WhatsApp")
                abrir(ctx, App.WHATSAPP, "")
            }
            MENSAJES.containsMatchIn(n) ->
                if (ENVIAR.containsMatchIn(n)) {
                    SpikeLog.log("AGENTE: ruta=enviar (no soportado)")
                    Respuesta(
                        "Por ahora solo puedo leerte los mensajes. Cuando te llegue uno, te pregunto si quieres contestarlo.",
                        "local", 0,
                    )
                } else { SpikeLog.log("AGENTE: ruta=mensajes"); leerMensajes(ctx) }
            else -> detectarApp(ctx, n)?.let { (app, q) ->
                SpikeLog.log("AGENTE: ruta=app ${app.nombre} busqueda='$q'")
                abrir(ctx, app, q)
            } ?: MedicinaValidador.respuestaSegura(ctx, frase)?.let { segura ->
                // Sobredosis, alergia, sintomas o dosis olvidada: nunca los responde el modelo.
                SpikeLog.log("AGENTE: ruta=medicina riesgo (respuesta fija)")
                Respuesta(segura, "local", 0)
            } ?: if (BUSCAR_WEB.containsMatchIn(n)) {
                SpikeLog.log("AGENTE: ruta=busqueda web")
                if (!Privacidad.nubeActiva()) return Respuesta(
                    "Para buscar en internet necesito la nube de Google, y la tienes apagada. Puedes encenderla en Ajustes, en Privacidad.",
                    "local", 0,
                )
                try {
                    val b = Busqueda.responder(frase)
                    Respuesta(b.texto, "web", b.ms)
                } catch (e: Exception) {
                    // Cuota agotada o sin internet: se abre Google con la pregunta, en lugar de quedarse sin respuesta.
                    SpikeLog.log("AGENTE: busqueda fallo (${e::class.simpleName}); abro Google")
                    Respuesta("No pude buscarlo yo ahorita. Te abro Google con tu pregunta.", "local", 0) {
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
                    AsistenteVoz.responder(frase)
                } catch (e: Exception) {
                    if (Privacidad.nubeActiva()) throw e
                    SpikeLog.log("AGENTE: conversacion sin nube y sin modelo local (${e::class.simpleName})")
                    return Respuesta(
                        "Para conversar necesito la nube de Google, y la tienes apagada. Puedes encenderla en Ajustes, en Privacidad.",
                        "local", 0,
                    )
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
        if (p.isEmpty()) return Respuesta("No tienes mensajes nuevos.", "local", 0)
        val lote = p.take(5).reversed()                      // los 5 mas recientes, en orden cronologico
        val encabezado = if (p.size == 1) "Tienes un mensaje nuevo." else "Tienes ${p.size} mensajes nuevos."
        val cuerpo = lote.joinToString(" ") { "${it.remitente.ifBlank { "Alguien" }} dice: ${it.texto}." }
        val resto = if (p.size > lote.size) " Y ${p.size - lote.size} mas." else ""
        Bandeja.marcarLeidos(ctx, p.map { it.id })
        return Respuesta("$encabezado $cuerpo$resto", "local", 0)
    }

    private fun leerCorreo(ctx: Context): Respuesta {
        val p = Bandeja.pendientes(ctx, Bandeja.Tipo.CORREO)
        if (p.isEmpty()) return Respuesta("No tienes correos nuevos.", "local", 0)
        val lote = p.take(5).reversed()
        val encabezado = if (p.size == 1) "Tienes un correo nuevo." else "Tienes ${p.size} correos nuevos."
        val cuerpo = lote.joinToString(" ") { c ->
            val de = c.remitente.ifBlank { "un remitente desconocido" }
            val extra = if (lote.size == 1 && c.detalle.isNotBlank()) " ${c.detalle.take(200)}" else ""
            "De $de: ${c.texto}.$extra"
        }
        val resto = if (p.size > lote.size) " Y ${p.size - lote.size} mas." else ""
        Bandeja.marcarLeidos(ctx, p.map { it.id })
        return Respuesta("$encabezado $cuerpo$resto", "local", 0)
    }

    // ---------- Recordatorios y medicinas ----------

    private suspend fun crearRecordatorio(ctx: Context, frase: String): Respuesta {
        val t0 = System.currentTimeMillis()
        val item = Recordatorios.interpretar(frase)
            ?: return Respuesta(
                "No entendí cuándo quieres que te avise. Dime, por ejemplo: recuérdame tomar la pastilla a las 8 de la noche.",
                "local", System.currentTimeMillis() - t0,
            )
        Recordatorios.agregar(ctx, item)
        val sinPermiso = android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val aviso = if (sinPermiso) " Ojo: activa las notificaciones de Aidly en Ajustes para que el aviso se vea." else ""
        return Respuesta("Listo. Te recuerdo ${item.texto} ${Recordatorios.describir(item)}.$aviso", "local", System.currentTimeMillis() - t0)
    }

    private fun listarRecordatorios(ctx: Context): Respuesta {
        val l = Recordatorios.listar(ctx)
        if (l.isEmpty()) return Respuesta("No tienes recordatorios.", "local", 0)
        val cuerpo = l.take(5).joinToString(" ") { "${it.texto} ${Recordatorios.describir(it)}." }
        val resto = if (l.size > 5) " Y ${l.size - 5} más." else ""
        return Respuesta("Tienes ${if (l.size == 1) "un recordatorio" else "${l.size} recordatorios"}. $cuerpo$resto", "local", 0)
    }

    private fun cancelarRecordatorio(ctx: Context, n: String): Respuesta {
        val l = Recordatorios.listar(ctx)
        if (l.isEmpty()) return Respuesta("No tienes recordatorios que cancelar.", "local", 0)
        if (Regex("\\b(todos|todas)\\b").containsMatchIn(n)) {
            l.forEach { Recordatorios.cancelar(ctx, it.id) }
            return Respuesta("Listo. Borré todos tus recordatorios.", "local", 0)
        }
        val tonterias = setOf("cancela", "cancelar", "borra", "borrar", "elimina", "eliminar", "quita", "quitar", "el", "la", "los", "las", "de", "del", "mi", "mis",
            "un", "una", "recordatorio", "recordatorios", "alarma", "alarmas", "aviso", "avisos", "por", "favor", "aidly", "que", "para", "a")
        val palabras = n.split(" ").filter { it.length > 2 && it !in tonterias }
        val coinciden = if (palabras.isEmpty()) (if (l.size == 1) l else emptyList())
        else l.filter { r -> val t = normalizar(r.texto); palabras.any { it in t } }
        if (coinciden.isEmpty()) {
            val cuales = l.take(4).joinToString(", ") { it.texto }
            return Respuesta("No supe cuál borrar. Tienes: $cuales. Dime, por ejemplo: borra el recordatorio de la pastilla.", "local", 0)
        }
        coinciden.forEach { Recordatorios.cancelar(ctx, it.id) }
        return Respuesta("Listo. Borré ${coinciden.joinToString(" y ") { it.texto }}.", "local", 0)
    }

    /** "Auxilio", "me siento mal": avisa a la familia (con ubicacion) y deja el 911 a un toque del boton verde. */
    private fun pedirAyuda(ctx: Context): Respuesta {
        if (!Circulo.ajustes(ctx).activo) {
            return Respuesta(
                "Todavía no has configurado a tu familia, así que no puedo avisarles. Te abro el teléfono con el ${Emergencia.habla(ctx)}. Toca el botón verde para llamar.",
                "local", 0,
            ) { marcador(ctx, Emergencia.numero(ctx)) }
        }
        val checkins = Recordatorios.pendientes(ctx).filter { it.tipo == Recordatorios.Tipo.CHECKIN }
        if (checkins.isEmpty()) Circulo.sosAsync(ctx)
        else checkins.forEach { Recordatorios.marcar(ctx, it.id, Recordatorios.Estado.AYUDA) }   // esto tambien avisa
        return Respuesta(
            "Ya avisé a tu familia. Si es una emergencia, toca el botón verde para llamar al ${Emergencia.habla(ctx)}.", "local", 0,
        ) { marcador(ctx, Emergencia.numero(ctx)) }
    }

    private fun estoyBien(ctx: Context): Respuesta {
        Recordatorios.pendientes(ctx).filter { it.tipo == Recordatorios.Tipo.CHECKIN }
            .forEach { Recordatorios.marcar(ctx, it.id, Recordatorios.Estado.TOMADO) }
        Circulo.limpiarSos(ctx)
        return Respuesta("Qué bueno que estés bien. Me alegra mucho.", "local", 0)
    }

    private fun yaLoHice(ctx: Context): Respuesta {
        val p = Recordatorios.pendientes(ctx)
        if (p.isEmpty()) return Respuesta("No tengo ningún aviso pendiente ahorita.", "local", 0)
        p.forEach { Recordatorios.marcar(ctx, it.id, Recordatorios.Estado.TOMADO) }
        return Respuesta("Muy bien. Ya lo anoté.", "local", 0)
    }

    // ---------- Calendario, fecha y hora ----------

    private suspend fun crearCita(ctx: Context, frase: String): Respuesta {
        val t0 = System.currentTimeMillis()
        val e = Agenda.interpretar(frase)
            ?: return Respuesta(
                "No entendí cuándo es la cita. Dime, por ejemplo: agéndame cita con el doctor el martes a las 10 de la mañana.",
                "local", System.currentTimeMillis() - t0,
            )
        val guardada = Agenda.guardar(ctx, e)
        val aviso = Agenda.avisoPrevio(ctx, e)?.also { Recordatorios.agregar(ctx, it) }
        val cuando = Recordatorios.describir(Recordatorios.Item("", e.titulo, Recordatorios.Tipo.GENERAL, e.inicio, 0))
        val ms = System.currentTimeMillis() - t0
        val avisoTxt = if (aviso != null) " Te aviso antes." else ""
        return if (guardada) {
            Respuesta("Listo. Agendé ${e.titulo} $cuando.$avisoTxt", "local", ms)
        } else {
            Respuesta(
                "Te abro el calendario con ${e.titulo} $cuando. Toca guardar.$avisoTxt Si quieres que yo lo guarde solo, dame permiso del calendario en el menú.",
                "local", ms,
            ) {
                try { ctx.startActivity(Agenda.intentInsertar(e)) }
                catch (ex: Exception) { SpikeLog.log("AGENTE: no pude abrir el calendario: ${ex.message}") }
            }
        }
    }

    private fun verAgenda(ctx: Context, n: String): Respuesta {
        val (desde, hasta, etiqueta) = Agenda.rango(n)
        val citas = Agenda.citas(ctx, desde, hasta)
        val avisos = Recordatorios.listar(ctx).filter { it.proxima in desde until hasta }
        fun cuando(t: Long) = Recordatorios.describir(Recordatorios.Item("", "", Recordatorios.Tipo.GENERAL, t, 0))
        val partes = citas.take(6).map { "${it.titulo} ${cuando(it.inicio)}" } +
            avisos.take(6).map { "recordatorio: ${it.texto} ${cuando(it.proxima)}" }
        val sinPermiso = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        val nota = if (sinPermiso) " Para leer tu calendario necesito permiso. Dámelo en el menú de Aidly." else ""
        if (partes.isEmpty()) return Respuesta("No tienes nada agendado $etiqueta.$nota", "local", 0)
        return Respuesta("Tienes ${partes.size} cosas $etiqueta. ${partes.joinToString(". ")}.$nota", "local", 0)
    }

    private fun horaFecha(n: String): Respuesta {
        val c = java.util.Calendar.getInstance()
        if ("hora" in n) {
            val h = Recordatorios.horaHablada(c).replaceFirst("a las", "Son las").replaceFirst("a la", "Es la")
            return Respuesta("$h.", "local", 0)
        }
        val f = java.text.SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(c.time)
        return Respuesta("Hoy es $f.", "local", 0)
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

    private fun llamar(ctx: Context, n: String): Respuesta {
        if (EMERGENCIA.containsMatchIn(n)) {
            return Respuesta("Abriendo el marcador con el ${Emergencia.habla(ctx)}. Toca el botón verde para llamar.", "local", 0) { marcador(ctx, Emergencia.numero(ctx)) }
        }
        Regex("\\d[\\d ]*\\d").find(n)?.value?.replace(" ", "")?.takeIf { it.length >= 3 }?.let { num ->
            return Respuesta("Abriendo el marcador con el ${num.toList().joinToString(" ")}. Toca el botón verde para llamar.", "local", 0) { marcador(ctx, num) }
        }
        val nombre = Regex("\\b(?:llama|llamar|llamale|marca|marcar|comunicame con|telefonea)\\s+(?:a|al|con)?\\s*(.*)$").find(n)
            ?.groupValues?.get(1).orEmpty()
            .replace(Regex("\\bpor favor\\b"), "")
            .split(" ").filter { it.isNotBlank() && it !in setOf("mi", "mis", "la", "el", "a", "al", "un", "una", "numero", "telefono", "teclado", "marcador", "abre", "abrir") }
            .joinToString(" ")
        if (nombre.isBlank()) return Respuesta("Abriendo el teclado para marcar.", "local", 0) {
            ctx.startActivity(Intent(ctx, MarcarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        Favoritos.buscar(ctx, nombre, ::normalizar)?.let { f ->
            return Respuesta("Abriendo el teléfono con ${f.nombre}. Toca el botón verde para llamar.", "local", 0) { marcador(ctx, f.numero) }
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Respuesta(
                "Para llamar a $nombre necesito permiso para ver tus contactos. Dáselo en el menú de Aidly. Mientras tanto, abro el teléfono.",
                "local", 0,
            ) { marcador(ctx, "") }
        }
        val c = buscarContacto(ctx, nombre)
            ?: return Respuesta("No encontré a $nombre en tus contactos. Abro el teléfono para que lo busques.", "local", 0) { marcador(ctx, "") }
        return Respuesta("Abriendo el teléfono con ${c.first}. Toca el botón verde para llamar.", "local", 0) { marcador(ctx, c.second) }
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

    private fun detectarApp(ctx: Context, n: String): Pair<App, String>? {
        val app = NOMBRES.firstOrNull { it.first.containsMatchIn(n) }?.second
            ?: when {
                QUIERE_IR.containsMatchIn(n) -> App.MAPS
                VERBO_ABRIR.containsMatchIn(n) && QUIERE_MUSICA.containsMatchIn(n) -> App.SPOTIFY
                VERBO_ABRIR.containsMatchIn(n) && QUIERE_PELI.containsMatchIn(n) ->
                    if (instalada(ctx, App.NETFLIX)) App.NETFLIX else App.YOUTUBE
                VERBO_ABRIR.containsMatchIn(n) && QUIERE_VIDEO.containsMatchIn(n) -> App.YOUTUBE
                else -> return null
            }
        return app to extraerBusqueda(n, app)
    }

    private fun extraerBusqueda(n: String, app: App): String {
        if (!app.busca) return ""
        val texto = n.replace(Regex("\\s+(en|con|por)\\s+(spotify|youtube|you tube|netflix|prime video|disney|maps|google maps)\\b.*$"), "")
            .replace(Regex("\\s+por favor$"), "")
        if (app == App.MAPS) {
            Regex("\\b(?:llevame|navega|navegar|llegar|llego|ruta)\\s+(?:a|al|hasta|hacia|para)?\\s*(.+)$").find(texto)?.let {
                return it.groupValues[1].trim()
            }
        }
        Regex("\\b(?:de|del|sobre|llamad[ao]|titulad[ao]|que se llama)\\s+(.+)$").find(texto)?.let {
            return it.groupValues[1].trim()
        }
        return texto.split(" ").filter { it.isNotBlank() && it !in RELLENO }.joinToString(" ")
    }

    private fun abrir(ctx: Context, app: App, busqueda: String): Respuesta {
        if (!instalada(ctx, app)) {
            return Respuesta("No tienes ${app.nombre} instalado en este teléfono.", "local", 0)
        }
        val texto = when {
            busqueda.isBlank() -> "Abriendo ${app.nombre}."
            app == App.MAPS -> "Te llevo a $busqueda con ${app.nombre}."
            else -> "Buscando $busqueda en ${app.nombre}."
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
