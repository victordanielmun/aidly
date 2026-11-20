package com.aidly.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Puerto serie CDC-ACM por el USB-C (115200 8N1). Solo lectura/escritura de lineas; no decide nada. */
class PuertoUsb(private val conexion: UsbDeviceConnection, private val ifaces: List<UsbInterface>, private val entrada: UsbEndpoint, private val salida: UsbEndpoint) : PuertoDock {
    private val buffer = StringBuilder()
    private val bytes = ByteArray(256)

    override fun escribir(linea: String) {
        val datos = (linea.trim() + "\n").toByteArray(Charsets.US_ASCII)
        conexion.bulkTransfer(salida, datos, datos.size, 500)
    }

    override fun leer(timeoutMs: Int): String? {
        val limite = System.currentTimeMillis() + timeoutMs
        while (true) {
            val i = buffer.indexOf("\n")
            if (i >= 0) { val l = buffer.substring(0, i); buffer.delete(0, i + 1); return l.trim('\r') }
            val resto = (limite - System.currentTimeMillis()).toInt()
            if (resto <= 0) return null
            val n = conexion.bulkTransfer(entrada, bytes, bytes.size, minOf(resto, 200))
            if (n > 0) buffer.append(String(bytes, 0, n, Charsets.US_ASCII))
        }
    }

    override fun cerrar() { try { ifaces.forEach { conexion.releaseInterface(it) }; conexion.close() } catch (_: Exception) {} }
}

/** Encuentra y abre el dock por USB. Nunca manda movimiento sin que antes responda al saludo (ver [ClienteDock]). */
object DockUsb {
    private const val ACCION_PERMISO = "com.aidly.app.USB_PERMISO"

    /** Dispositivos con interfaz serie CDC (clase 2 de control + clase 10 de datos). */
    fun candidatos(ctx: Context): List<UsbDevice> {
        val um = ctx.getSystemService(UsbManager::class.java) ?: return emptyList()
        return um.deviceList.values.filter { d -> (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA } }
    }

    /** Pide el permiso de USB (la persona lo confirma en un dialogo del sistema) y devuelve el resultado. */
    fun pedirPermiso(ctx: Context, dev: UsbDevice, resultado: (Boolean) -> Unit) {
        val um = ctx.getSystemService(UsbManager::class.java)
        if (um.hasPermission(dev)) { resultado(true); return }
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                ctx.unregisterReceiver(this)
                resultado(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(ctx, receptor, IntentFilter(ACCION_PERMISO), ContextCompat.RECEIVER_NOT_EXPORTED)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ACCION_PERMISO).setPackage(ctx.packageName), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        um.requestPermission(dev, pi)
    }

    /** Abre el puerto serie de [dev] (con permiso ya concedido) o devuelve null. */
    fun abrir(ctx: Context, dev: UsbDevice): PuertoUsb? {
        val um = ctx.getSystemService(UsbManager::class.java)
        val con = um.openDevice(dev) ?: return null
        var datos: UsbInterface? = null
        val reclamadas = mutableListOf<UsbInterface>()
        for (k in 0 until dev.interfaceCount) {
            val f = dev.getInterface(k)
            if (f.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) datos = f
            if (f.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA || f.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                if (con.claimInterface(f, true)) reclamadas += f
            }
        }
        val d = datos ?: run { con.close(); return null }
        var ent: UsbEndpoint? = null; var sal: UsbEndpoint? = null
        for (e in 0 until d.endpointCount) {
            val ep = d.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) if (ep.direction == UsbConstants.USB_DIR_IN) ent = ep else sal = ep
        }
        if (ent == null || sal == null) { con.close(); return null }
        // SET_LINE_CODING 115200 8N1 y DTR+RTS activos (lo que esperan las placas con USB nativo).
        val linea = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08)
        con.controlTransfer(0x21, 0x20, 0, 0, linea, linea.size, 500)
        con.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 500)
        return PuertoUsb(con, reclamadas, ent, sal)
    }

    /**
     * Busca, pide permiso, abre y saluda. [listo] recibe el dock o null (en el hilo principal no se bloquea: el saludo corre en otro hilo).
     * [alEvento] recibe lo que el dock manda solo (por ejemplo "BTN" al pulsar el boton fisico).
     */
    fun conectar(ctx: Context, alEvento: (String) -> Unit, listo: (DockConectado?) -> Unit) {
        val dev = candidatos(ctx).firstOrNull()
        if (dev == null) { listo(null); return }
        pedirPermiso(ctx, dev) { ok ->
            if (!ok) { SpikeLog.log("DOCK USB: permiso denegado"); listo(null); return@pedirPermiso }
            Thread {
                val puerto = abrir(ctx, dev)
                if (puerto == null) { SpikeLog.log("DOCK USB: no pude abrir el puerto"); listo(null); return@Thread }
                val cliente = ClienteDock(puerto, alEvento)
                if (!cliente.saludar()) { SpikeLog.log("DOCK USB: el dispositivo no es un dock Aidly (no saludo)"); puerto.cerrar(); listo(null); return@Thread }
                SpikeLog.log("DOCK USB: conectado (protocolo ${cliente.version}, recorrido ${cliente.recorrido}°)")
                listo(DockConectado(cliente))
            }.start()
        }
    }
}

/**
 * Dock real por USB. Usa el mismo control suave del simulador para decidir el angulo y manda `GOTO` solo si cambia >= 2 grados y
 * como mucho 5 veces por segundo, desde un hilo propio (la camara no se bloquea). Si algo falla, manda STOP y deja de enviar.
 */
class DockConectado(private val cliente: ClienteDock, private val control: DockSimulado = DockSimulado(maxGradosPorS = 30f)) : Dock {
    override val nombre = "usb"
    override val angulo: StateFlow<Float> get() = control.angulo
    private val ejecutor = Executors.newSingleThreadExecutor()
    @Volatile private var ultimoEnviado = 0
    @Volatile private var ultimoMs = 0L
    @Volatile private var activo = true

    override fun seguir(x: Float?, ms: Long) {
        if (!activo) return
        control.seguir(x, ms)
        val a = control.angulo.value.roundToInt()
        if (abs(a - ultimoEnviado) >= 2 && ms - ultimoMs >= 200) {
            ultimoEnviado = a; ultimoMs = ms
            ejecutor.execute { if (!cliente.irA(a)) { SpikeLog.log("DOCK USB: la orden fallo; se detiene"); activo = false; cliente.parar() } }
        }
    }

    override fun detener() { control.detener(); ejecutor.execute { try { cliente.parar() } catch (_: Exception) {} } }

    fun cerrar() { activo = false; ejecutor.execute { try { cliente.parar() } catch (_: Exception) {}; cliente.cerrar() }; ejecutor.shutdown() }
}
