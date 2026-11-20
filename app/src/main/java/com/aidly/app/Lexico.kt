package com.aidly.app

import android.content.Context

/**
 * Vocabulario de los comandos de voz en cada idioma. El texto que llega ya viene normalizado (minusculas, sin acentos ni signos,
 * por ejemplo "what's" llega como "what s"). El enrutador de [Agente] es el mismo para los dos idiomas; solo cambian estas listas.
 */
class Lexico(
    val id: String,
    val nombresApps: List<Pair<Regex, String>>,   // regex -> nombre de enum de app
    val correo: Regex, val mensajes: Regex, val enviar: Regex, val abrirExpl: Regex, val verboAbrir: Regex,
    val quiereMusica: Regex, val quiereVideo: Regex, val quierePeli: Regex, val quiereIr: Regex,
    val medicina: Regex, val leerAlgo: Regex, val analiza: Regex,
    val crearRec: Regex, val objRec: Regex, val listarRec: Regex, val cancelarRec: Regex, val tomado: Regex,
    val sos: Regex, val estoyBien: Regex,
    val crearCita: Regex, val verAgenda: Regex, val cuandoAgenda: Regex, val horaFecha: Regex, val preguntaHora: Regex,
    val buscarWeb: Regex, val llamar: Regex, val telefono: Regex, val camara: Regex, val fotoVerbo: Regex, val emergencia: Regex,
    val todos: Regex,
    val relleno: Set<String>,
    val ruidoCancelar: Set<String>,
    val ruidoLlamar: Set<String>,
    val nombreLlamar: Regex,
    val sufijoApp: Regex,
    val destinoMapa: Regex,
    val tituloBusqueda: Regex,
    val porFavor: Regex,
    val modoDock: Regex,
) {
    companion object {
        private fun r(s: String) = Regex(s)

        val ES = Lexico(
            id = "es",
            nombresApps = listOf(
                r("\\bspotify\\b") to "SPOTIFY", r("\\b(youtube|you tube)\\b") to "YOUTUBE", r("\\bnetflix\\b") to "NETFLIX",
                r("\\b(prime video|amazon prime|prime)\\b") to "PRIME", r("\\bdisney\\b") to "DISNEY",
                r("\\b(google maps|maps|mapa|mapas)\\b") to "MAPS", r("\\bfacebook\\b") to "FACEBOOK",
            ),
            correo = r("\\b(correo|correos|mail|mails|email|emails|gmail|outlook)\\b"),
            mensajes = r("\\b(mensaje|mensajes|whatsapp|wasap|guasap)\\b"),
            enviar = r("\\b(envia|enviar|manda|mandar|escribe|escribir|responde|responder|contesta|contestar)\\b"),
            abrirExpl = r("\\b(abre|abrir|ejecuta|inicia|iniciar)\\b"),
            verboAbrir = r("\\b(abre|abrir|pon|ponme|poner|busca|buscar|quiero|reproduce|reproducir|muestrame|ensename|escuchar|ver|mirar)\\b"),
            quiereMusica = r("\\b(musica|cancion|canciones|disco|cantante|playlist|escuchar)\\b"),
            quiereVideo = r("\\b(video|videos|tutorial|tutoriales|ver|mirar)\\b"),
            quierePeli = r("\\b(pelicula|peliculas|serie|series|novela|novelas)\\b"),
            quiereIr = r("\\b(llevame|navega|navegar|como llego|como llegar|ruta)\\b"),
            medicina = r("\\b(medicina|medicinas|medicamento|medicamentos|pastilla|pastillas|remedio|remedios)\\b"),
            leerAlgo = r("\\b(lee|leer|leeme|analiza|analizar|revisa|revisar|que dice|que es|para que sirve|que son)\\b"),
            analiza = r(
                "\\b(analiza|analizar|analizame|analicemos)\\b|\\b(que|cual) dice (esta|este|esto|el|la)\\b|" +
                    "\\b(que|cual) dice (aqui|ahi|alli|alla)\\b|\\bque (es|son) (esto|eso|estas cosas)\\b|" +
                    "\\b(es|sera|parece) (una )?estafa\\b|\\b(revisa|revisame|lee|leeme) (esta|este|esto|estos|estas)\\b",
            ),
            crearRec = r(
                "\\b(recuerdame|recordarme|recuerdamelo|ponme (una )?alarma|pon (una )?alarma|programa (una )?alarma|" +
                    "(crea|creame|agrega|agregame|anade|anademe|programa|pon|ponme) (un )?recordatorio)\\b|" +
                    "\\bavisame (a|en|manana|hoy|todos|cada|el|que|para|cuando sean)\\b",
            ),
            objRec = r("\\b(recordatorio|recordatorios|alarma|alarmas|aviso|avisos)\\b"),
            listarRec = r("\\b(que|cuales|cuantos|cuantas|lista|tengo|muestrame|dime|revisa)\\b"),
            cancelarRec = r("\\b(cancela|cancelar|borra|borrar|elimina|eliminar|quita|quitar)\\b"),
            tomado = r("\\b(ya (me )?(tome|tomo)|ya la tome|ya lo tome|ya las tome|ya los tome|ya lo hice|ya esta hecho)\\b"),
            sos = r(
                "\\b(me siento mal|no me siento bien|me senti mal|me cai|auxilio|socorro|emergencia|me duele el pecho|" +
                    "no puedo respirar|avisa a mi familia|avisale a mi familia|avisales a mis familiares)\\b",
            ),
            estoyBien = r("\\b(estoy bien|me siento bien|ya estoy bien)\\b"),
            crearCita = r(
                "\\b(agendame|agendar|agenda (una|un|la|el|mi)|apuntame (una|un|la|el)|anota (una|un|la|el) (cita|consulta|evento)|" +
                    "programame (una|un) (cita|consulta))\\b",
            ),
            verAgenda = r("\\b(que (tengo|hay)( agendado| pendiente| programado| para)?|mi agenda|mis citas|que citas tengo|tengo (alguna )?cita)\\b"),
            cuandoAgenda = r("\\b(hoy|manana|semana|agenda|citas|cita|agendado|pendiente|programado)\\b"),
            horaFecha = r("\\b(que hora es|que dia es|que fecha es|a cuanto estamos|que dia es hoy)\\b"),
            preguntaHora = r("\\bhora\\b"),
            buscarWeb = r(
                "\\b(internet|google|noticias|clima|pronostico|llover|lluvia|temperatura|dolar|euro|precio|cuanto cuesta|cuanto vale|" +
                    "resultado del|quien gano|horario de|donde queda|receta|que tiempo hace|averigua|investiga)\\b",
            ),
            llamar = r("\\b(llama|llamar|llamale|marca|marcar|comunicame|telefonea)\\b"),
            telefono = r("\\b(telefono|marcador|teclado)\\b"),
            camara = r("\\b(camara|foto|fotos|fotografia)\\b"),
            fotoVerbo = r("\\b(toma|tomar|tomame|saca|sacar|sacame|hacer|haz|quiero)\\b"),
            emergencia = r("\\b(emergencia|ambulancia|policia|bomberos|auxilio|socorro)\\b"),
            todos = r("\\b(todos|todas)\\b"),
            relleno = setOf(
                "abre", "abrir", "pon", "ponme", "poner", "busca", "buscar", "quiero", "reproduce", "reproducir",
                "escuchar", "ver", "mirar", "spotify", "youtube", "you", "tube", "netflix", "prime", "amazon", "disney",
                "maps", "mapa", "mapas", "google", "en", "un", "una", "unos", "unas", "musica", "cancion", "canciones",
                "video", "videos", "pelicula", "peliculas", "serie", "series", "por", "favor", "me", "puedes", "podrias",
                "aidly", "oye", "hola", "para", "mi", "algo", "muestrame", "ensename", "llevame", "navega", "ruta",
            ),
            ruidoCancelar = setOf(
                "cancela", "cancelar", "borra", "borrar", "elimina", "eliminar", "quita", "quitar", "el", "la", "los", "las", "de", "del", "mi", "mis",
                "un", "una", "recordatorio", "recordatorios", "alarma", "alarmas", "aviso", "avisos", "por", "favor", "aidly", "que", "para", "a",
            ),
            ruidoLlamar = setOf("mi", "mis", "la", "el", "a", "al", "un", "una", "numero", "telefono", "teclado", "marcador", "abre", "abrir"),
            nombreLlamar = r("\\b(?:llama|llamar|llamale|marca|marcar|comunicame con|telefonea)\\s+(?:a|al|con)?\\s*(.*)$"),
            sufijoApp = r("\\s+(en|con|por)\\s+(spotify|youtube|you tube|netflix|prime video|disney|maps|google maps)\\b.*$"),
            destinoMapa = r("\\b(?:llevame|navega|navegar|llegar|llego|ruta)\\s+(?:a|al|hasta|hacia|para)?\\s*(.+)$"),
            tituloBusqueda = r("\\b(?:de|del|sobre|llamad[ao]|titulad[ao]|que se llama)\\s+(.+)$"),
            porFavor = r("\\bpor favor\\b"),
            modoDock = r("\\b(modo dock|modo mesa|modo base|el dock)\\b"),
        )

        val EN = Lexico(
            id = "en",
            nombresApps = listOf(
                r("\\bspotify\\b") to "SPOTIFY", r("\\b(youtube|you tube)\\b") to "YOUTUBE", r("\\bnetflix\\b") to "NETFLIX",
                r("\\b(prime video|amazon prime|prime)\\b") to "PRIME", r("\\bdisney\\b") to "DISNEY",
                r("\\b(google maps|maps|map)\\b") to "MAPS", r("\\bfacebook\\b") to "FACEBOOK",
            ),
            correo = r("\\b(email|emails|e mail|mail|mails|gmail|outlook)\\b"),
            mensajes = r("\\b(message|messages|text|texts|whatsapp)\\b"),
            enviar = r("\\b(send|write|reply|respond|answer)\\b"),
            abrirExpl = r("\\b(open|launch|start|run)\\b"),
            verboAbrir = r("\\b(open|put on|play|search|search for|find|i want|i d like|id like|show me|watch|listen|listen to|look)\\b"),
            quiereMusica = r("\\b(music|song|songs|album|singer|playlist|listen)\\b"),
            quiereVideo = r("\\b(video|videos|tutorial|tutorials|watch|clip|clips)\\b"),
            quierePeli = r("\\b(movie|movies|film|films|series|show|shows|soap opera)\\b"),
            quiereIr = r("\\b(take me|navigate|directions|how do i get|how to get|route)\\b"),
            medicina = r("\\b(medicine|medicines|medication|medications|pill|pills|drug|drugs|prescription)\\b"),
            leerAlgo = r("\\b(read|analyze|analyse|check|what does|what is|what s|whats|what are|what do)\\b"),
            analiza = r(
                "\\b(analyze|analyse)\\b|\\bwhat (does|do) (this|that|it|these|those) say\\b|\\bwhat (is|are) (this|these|that|those)\\b|" +
                    "\\bwhat s (this|that)\\b|\\bwhats (this|that)\\b|\\b(is|could be|might be) (this )?(a )?scam\\b|" +
                    "\\b(check|read|look at) (this|these|that|those)\\b",
            ),
            crearRec = r(
                "\\b(remind me|set (a|an) (reminder|alarm)|create (a )?reminder|add (a )?reminder|wake me( up)?|let me know (at|in|tomorrow|every|when))\\b",
            ),
            objRec = r("\\b(reminder|reminders|alarm|alarms|alert|alerts)\\b"),
            listarRec = r("\\b(what|which|how many|list|show|tell me|do i have|have)\\b"),
            cancelarRec = r("\\b(cancel|delete|remove|erase|clear)\\b"),
            tomado = r("\\b(i (already )?took (it|them|my pills?|my medicine|my medication)|i already took|i have taken (it|them|my )|i did it|already took (it|them))\\b"),
            sos = r(
                "\\b(i fell|i have fallen|i need help|i feel (sick|bad|unwell|dizzy)|i dont feel well|i don t feel well|i do not feel well|" +
                    "chest pain|my chest hurts|i can t breathe|i cant breathe|emergency|tell my family|call my family|notify my family)\\b|" +
                    "\\bhelp me$|^help$|^help help",
            ),
            estoyBien = r("\\b(i am (fine|ok|okay|good|well|alright)|im (fine|ok|okay|good|well|alright)|i m (fine|ok|okay|good|well|alright)|i feel (fine|ok|okay|good|better))\\b"),
            crearCita = r(
                "\\b((schedule|book|add) (a|an|my|the) (appointment|meeting|visit|event|doctor)|(make|set up|set) (a|an) (appointment|meeting)|put (a|an) (appointment|event))\\b",
            ),
            verAgenda = r(
                "\\b(what do i have|what s on|whats on|what is on|my schedule|my agenda|my appointments|do i have (any )?(appointments|plans)|what appointments)\\b",
            ),
            cuandoAgenda = r("\\b(today|tomorrow|week|schedule|agenda|appointments|appointment|calendar|planned|scheduled)\\b"),
            horaFecha = r(
                "\\b(what time is it|what is the time|what s the time|whats the time|what day is it|what is the date|what s the date|whats the date|" +
                    "what is today|what s today|whats today|what day is today)\\b",
            ),
            preguntaHora = r("\\btime\\b"),
            buscarWeb = r(
                "\\b(internet|google|news|weather|forecast|rain|temperature|exchange rate|dollar|euro|price|how much does|how much is|who won|score|" +
                    "recipe|opening hours|look up|find out|search online|search the web)\\b",
            ),
            llamar = r("\\b(call|phone|dial|ring)\\b"),
            telefono = r("\\b(phone|dialer|keypad|keyboard)\\b"),
            camara = r("\\b(camera|photo|photos|picture|pictures|selfie)\\b"),
            fotoVerbo = r("\\b(take|snap|shoot|make|want)\\b"),
            emergencia = r("\\b(emergency|ambulance|police|firefighters|fire department|911|112|123)\\b"),
            todos = r("\\ball\\b"),
            relleno = setOf(
                "open", "put", "on", "play", "search", "for", "find", "i", "want", "id", "d", "like", "listen", "to", "watch", "show", "me",
                "spotify", "youtube", "you", "tube", "netflix", "prime", "amazon", "disney", "maps", "map", "google", "a", "an", "the", "some",
                "music", "song", "songs", "video", "videos", "movie", "movies", "series", "please", "can", "could", "aidly", "hey", "hello",
                "take", "navigate", "directions", "route", "how", "get", "of", "by", "in", "with",
            ),
            ruidoCancelar = setOf(
                "cancel", "delete", "remove", "erase", "clear", "the", "my", "a", "an", "reminder", "reminders", "alarm", "alarms", "alert",
                "alerts", "please", "aidly", "that", "for", "to", "of", "about",
            ),
            ruidoLlamar = setOf("my", "the", "a", "an", "to", "number", "phone", "dialer", "keypad", "keyboard", "open"),
            nombreLlamar = r("\\b(?:call|phone|dial|ring)\\s+(?:to\\s+)?(.*)$"),
            sufijoApp = r("\\s+(on|in|with|using)\\s+(spotify|youtube|you tube|netflix|prime video|disney|maps|google maps)\\b.*$"),
            destinoMapa = r("\\b(?:take me|navigate|directions|route|get)\\s+(?:to|toward|towards)?\\s*(.+)$"),
            tituloBusqueda = r("\\b(?:by|of|called|named|titled|about)\\s+(.+)$"),
            porFavor = r("\\bplease\\b"),
            modoDock = r("\\b(dock mode|table mode|desk mode|the dock)\\b"),
        )

        fun de(ctx: Context): Lexico = if (Idioma.resolver(ctx) == "en") EN else ES
    }
}
