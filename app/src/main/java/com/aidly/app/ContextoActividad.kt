package com.aidly.app

/**
 * Lo que Aidly cree que la persona esta haciendo, a partir de los objetos que la camara ve cerca. Es solo un CONTEXTO PROBABLE (un detector de
 * objetos no sabe lo que alguien hace): nunca se afirma ("estas cocinando") sino que se ofrece en condicional ("si estas cocinando...").
 * Pura, sin Android. [tema]: tema de "Para ti" con el que se puede ofrecer algo; la tele no ofrece nada para no interrumpir.
 */
enum class Contexto(val tema: String?) {
    COCINA("cocina"), LEYENDO("historia"), ORDENADOR("tecnologia"), PLANTAS("jardin"), TELE(null);

    companion object {
        /** Etiquetas del detector (COCO) que cuentan para cada contexto, de mayor a menor prioridad. */
        private val ETIQUETAS = listOf(
            COCINA to setOf("oven", "microwave", "refrigerator", "sink", "knife", "spoon", "fork", "bowl"),
            LEYENDO to setOf("book"),
            ORDENADOR to setOf("laptop", "keyboard", "mouse"),
            PLANTAS to setOf("potted plant"),
            TELE to setOf("tv"),
        )
        /** Todas las etiquetas utiles: se le pide solo esas al detector. */
        val TODAS: List<String> = ETIQUETAS.flatMap { it.second }

        fun deEtiquetas(etiquetas: Collection<String>): Contexto? = ETIQUETAS.firstOrNull { (_, e) -> etiquetas.any { it in e } }?.first
    }
}

/** Estabiliza el contexto: hace falta verlo en [minimo] de las ultimas [ventana] mediciones para darlo por bueno (y lo mismo para dejarlo). */
class ContextoEstable(private val ventana: Int = 8, private val minimo: Int = 5) {
    private val ultimos = ArrayDeque<Contexto?>()

    /** Registra una medicion (objetos vistos; null si no se midio) y devuelve el contexto estable de ahora, o null si no hay. */
    fun observar(etiquetas: Collection<String>?): Contexto? {
        if (etiquetas != null) {
            ultimos.addLast(Contexto.deEtiquetas(etiquetas))
            while (ultimos.size > ventana) ultimos.removeFirst()
        }
        return actual()
    }

    fun actual(): Contexto? = ultimos.filterNotNull().groupingBy { it }.eachCount().filter { it.value >= minimo }.maxByOrNull { it.value }?.key

    fun reiniciar() = ultimos.clear()
}
