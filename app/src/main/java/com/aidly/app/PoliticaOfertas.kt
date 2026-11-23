package com.aidly.app

/**
 * Cuando puede Aidly ofrecer por voz algo de "Para ti" (en el modo dock). Pura, sin Android, para probarla. Es solo una politica de "no molestar":
 * nunca ofrece si la persona no lo acepto, si dijo "no mas por hoy", en horas de silencio, mientras pregunta "¿estas bien?",
 * si la persona no esta presente o hablo hace poco, ni mas de [Config.maxPorDia] veces al dia ni antes de [Config.entreOfertasS] desde la ultima.
 */
class PoliticaOfertas(private val cfg: Config = Config()) {
    class Config(
        val maxPorDia: Int = 3,
        /** La persona lleva al menos esto sin hablar con Aidly (para no interrumpir una conversacion). */
        val reposoS: Int = 120,
        val entreOfertasS: Int = 1800,
        /** Horas de silencio: [silencioDesde] (incluida) a [silencioHasta] (no incluida), con paso por la medianoche. */
        val silencioDesde: Int = 21,
        val silencioHasta: Int = 9,
    )

    private var dia = -1
    private var hoy = 0
    private var ultima = Long.MIN_VALUE / 2

    private fun enSilencio(hora: Int) =
        if (cfg.silencioDesde <= cfg.silencioHasta) hora in cfg.silencioDesde until cfg.silencioHasta
        else hora >= cfg.silencioDesde || hora < cfg.silencioHasta

    /** [dia]: numero de dia (cambia a medianoche); [hora]: 0..23; [ultimaInteraccionMs]: la ultima vez que la persona hablo con Aidly. */
    fun puedeOfrecer(
        ms: Long, dia: Int, hora: Int, visible: Boolean, ultimaInteraccionMs: Long, bienestarEnCurso: Boolean, perfil: Perfil,
    ): Boolean {
        if (!perfil.propuestas || perfil.elegidos.isEmpty()) return false
        if (ms < perfil.noMasHasta || enSilencio(hora)) return false
        if (!visible || bienestarEnCurso) return false
        if (ms - ultimaInteraccionMs < cfg.reposoS * 1000L) return false
        if (ms - ultima < cfg.entreOfertasS * 1000L) return false
        val ofrecidasHoy = if (dia == this.dia) hoy else 0
        return ofrecidasHoy < cfg.maxPorDia
    }

    fun ofrecida(ms: Long, dia: Int) {
        if (dia != this.dia) { this.dia = dia; hoy = 0 }
        hoy++; ultima = ms
    }
}
