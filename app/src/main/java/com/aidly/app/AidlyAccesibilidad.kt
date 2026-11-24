package com.aidly.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Ayuda de Aidly para otras aplicaciones. OPCIONAL: la persona la enciende en Ajustes > Accesibilidad (Android no deja encenderla desde la app).
 *
 *  - Dibuja el **boton flotante de Aidly** sobre cualquier app (capa de accesibilidad: no pide el permiso "mostrar sobre otras apps"). Se arrastra,
 *    y al tocarlo abre un panel con botones grandes: decir una orden, atras, inicio, bajar, subir, volver a Aidly y quitar el boton.
 *  - Ejecuta las ordenes que la persona pide: ir atras o al inicio, desplazar la pantalla y **tocar un boton por su texto**.
 *
 * Que ve y que no: el servicio no se suscribe a ningun evento (no recibe lo que se escribe ni lo que pasa en pantalla) y solo mira el texto de los
 * botones de la ventana de otra app en el momento en que la persona pide tocar algo. No guarda ni envia nada de lo que ve (docs/PRIVACY_DATA_FLOW.md, F19).
 */
class AidlyAccesibilidad : AccessibilityService() {
    companion object {
        @Volatile var instancia: AidlyAccesibilidad? = null
        private const val PANEL_MS = 15_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var boton: View? = null
    private var botonParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private val cierraPanel = Runnable { quitarPanel() }

    /** Solo versiones depurables: `adb shell am broadcast -a com.aidly.app.DEBUG_A11Y --es accion tocar --es texto Buscar` (para probar las ordenes sin hablar). */
    private val depuracion = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val ok = Accesibilidad.ejecutar(i.getStringExtra("accion").orEmpty(), i.getStringExtra("texto").orEmpty())
            SpikeLog.log("A11Y prueba: ${i.getStringExtra("accion")} ${i.getStringExtra("texto").orEmpty()} -> $ok")
        }
    }

    override fun onServiceConnected() {
        instancia = this
        wm = getSystemService(WindowManager::class.java)
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            ContextCompat.registerReceiver(this, depuracion, android.content.IntentFilter("com.aidly.app.DEBUG_A11Y"), ContextCompat.RECEIVER_EXPORTED)
        }
        actualizarBoton()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try { unregisterReceiver(depuracion) } catch (_: Exception) {}
        main.post { quitarPanel(); quitarBoton() }
        instancia = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}   // no se registra ningun evento (ver aidly_accesibilidad.xml)
    override fun onInterrupt() {}

    // ====================== Acciones (las usan el panel y las ordenes por voz) ======================

    /** Ventana de la app que la persona esta usando: la mas alta que no sea de Aidly (el boton y el panel son de otra capa). */
    private fun ventanaObjetivo(): AccessibilityWindowInfo? =
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName != packageName }.maxByOrNull { it.layer }

    private fun raizObjetivo(): AccessibilityNodeInfo? = ventanaObjetivo()?.root ?: rootInActiveWindow

    fun atras() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun inicio() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Desplaza el contenido: [haciaAbajo] = ver mas abajo (el dedo sube). */
    fun desplazar(haciaAbajo: Boolean): Boolean {
        val r = Rect()
        (ventanaObjetivo() ?: return false).getBoundsInScreen(r)
        val x = r.exactCenterX()
        val a = r.top + r.height() * 0.72f
        val b = r.top + r.height() * 0.28f
        return gesto(x, if (haciaAbajo) a else b, x, if (haciaAbajo) b else a, 350)
    }

    /** Toca el boton o texto que contiene [texto] en la app que se esta usando. true si lo encontro y lo toco. */
    fun tocarTexto(texto: String): Boolean {
        val raiz = raizObjetivo() ?: return false
        val candidatos = raiz.findAccessibilityNodeInfosByText(texto).orEmpty()
        for (n in candidatos) {
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        // Sin ancestro pulsable: se toca el centro del texto.
        val n = candidatos.firstOrNull() ?: return false
        val r = Rect(); n.getBoundsInScreen(r)
        return !r.isEmpty && gesto(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), 60)
    }

    private fun gesto(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val p = Path().apply { moveTo(x1, y1); if (x1 != x2 || y1 != y2) lineTo(x2, y2) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms.coerceAtLeast(1))).build()
        return dispatchGesture(g, null, null)
    }

    // ====================== Boton flotante y panel ======================

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
    private fun prefs() = getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun actualizarBoton() {
        main.post { if (Accesibilidad.botonActivo(this)) mostrarBoton() else { quitarPanel(); quitarBoton() } }
    }

    private fun mostrarBoton() {
        if (boton != null) return
        val tam = dp(72f)
        val v = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@AidlyAccesibilidad, R.drawable.mascota))
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Tema.Fondo.toArgb()); setStroke(dp(3f), Tema.Turquesa.toArgb()) }
            elevation = dp(6f).toFloat()
            contentDescription = getString(R.string.a11y_boton_desc)
            isClickable = true
        }
        val m = resources.displayMetrics
        val p = WindowManager.LayoutParams(
            tam, tam, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs().getInt("flotX", m.widthPixels - tam - dp(8f))
            y = prefs().getInt("flotY", m.heightPixels / 2)
        }
        val umbral = ViewConfiguration.get(this).scaledTouchSlop
        v.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0f; var y0 = 0f; var px = 0; var py = 0; var arrastrando = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; px = p.x; py = p.y; arrastrando = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - x0; val dy = e.rawY - y0
                        if (abs(dx) > umbral || abs(dy) > umbral) arrastrando = true
                        if (arrastrando) {
                            p.x = (px + dx).toInt().coerceIn(0, m.widthPixels - tam); p.y = (py + dy).toInt().coerceIn(0, m.heightPixels - tam)
                            wm?.updateViewLayout(view, p)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (arrastrando) prefs().edit().putInt("flotX", p.x).putInt("flotY", p.y).apply() else view.performClick()
                }
                return true
            }
        })
        v.setOnClickListener { alternarPanel() }
        try { wm?.addView(v, p); boton = v; botonParams = p } catch (e: Exception) { SpikeLog.log("A11Y: no pude mostrar el boton (${e::class.simpleName})") }
    }

    private fun quitarBoton() {
        boton?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        boton = null; botonParams = null
    }

    private fun alternarPanel() { if (panel != null) quitarPanel() else mostrarPanel() }

    private fun quitarPanel() {
        main.removeCallbacks(cierraPanel)
        panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private fun boton(texto: String, relleno: Int, letra: Int, alAbrir: () -> Unit): TextView = TextView(this).apply {
        text = texto; textSize = 22f; setTextColor(letra); typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        minHeight = dp(64f); setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(relleno) }
        isClickable = true; isFocusable = true
        setOnClickListener { quitarPanel(); alAbrir() }
    }

    private fun mostrarPanel() {
        val b = boton ?: return
        val bp = botonParams ?: return
        val m = resources.displayMetrics
        val fila = { a: TextView, c: TextView? ->
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4f), dp(4f), dp(4f), dp(4f)) })
                if (c != null) addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4f), dp(4f), dp(4f), dp(4f)) })
            }
        }
        val tarjeta = Tema.Tarjeta.toArgb(); val texto = Tema.Texto.toArgb(); val turquesa = Tema.Turquesa.toArgb(); val oscuro = Tema.Oscuro.toArgb()
        val contenido = object : LinearLayout(this) {
            override fun onTouchEvent(e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_OUTSIDE) quitarPanel(); return super.onTouchEvent(e) }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            background = GradientDrawable().apply { cornerRadius = dp(24f).toFloat(); setColor(Tema.Fondo.toArgb()); setStroke(dp(3f), turquesa) }
            addView(boton(getString(R.string.a11y_decir), turquesa, oscuro) { abrirOrden() }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4f), dp(4f), dp(4f), dp(4f)) })
            addView(fila(boton(getString(R.string.a11y_atras), tarjeta, texto) { atras() }, boton(getString(R.string.a11y_inicio), tarjeta, texto) { inicio() }))
            addView(fila(boton(getString(R.string.a11y_bajar), tarjeta, texto) { desplazar(true) }, boton(getString(R.string.a11y_subir), tarjeta, texto) { desplazar(false) }))
            addView(fila(boton(getString(R.string.a11y_volver_aidly), tarjeta, texto) { abrirAidly() }, boton(getString(R.string.a11y_quitar), tarjeta, Tema.Coral.toArgb()) { Accesibilidad.fijarBoton(this@AidlyAccesibilidad, false) }))
        }
        val ancho = (m.widthPixels * 0.92f).toInt()
        val alto = dp(360f)
        val p = WindowManager.LayoutParams(
            ancho, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (m.widthPixels - ancho) / 2
            // Debajo del boton si cabe; si no, encima.
            val limite = m.heightPixels - dp(72f)   // deja libre la barra de navegacion del sistema
            y = if (bp.y + b.height + alto < limite) bp.y + b.height + dp(8f) else (bp.y - alto - dp(8f)).coerceIn(dp(8f), (limite - alto).coerceAtLeast(dp(8f)))
        }
        try { wm?.addView(contenido, p); panel = contenido; main.postDelayed(cierraPanel, PANEL_MS) } catch (e: Exception) { SpikeLog.log("A11Y: no pude mostrar el panel (${e::class.simpleName})") }
    }

    private fun abrirAidly() = lanzar(Intent(this, HomeActivity::class.java))
    private fun abrirOrden() = lanzar(Intent(this, OrdenActivity::class.java))
    private fun lanzar(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) } catch (e: Exception) { SpikeLog.log("A11Y: no pude abrir Aidly (${e::class.simpleName})") }
    }
}
