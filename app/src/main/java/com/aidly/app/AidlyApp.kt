package com.aidly.app

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * App Check esta exigido en el proyecto. Para desarrollo se usa el proveedor de DEPURACION: la primera
 * vez imprime en logcat (tag DebugAppCheckProvider) un "debug secret" que hay que registrar en
 * Firebase Console > App Check > Apps > Aidly > Administrar tokens de depuracion.
 * Para produccion hay que cambiarlo por Play Integrity.
 */
class AidlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        Privacidad.cargar(this)
        ModeloLocal.iniciar()
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        AvisoRecordatorio.crearCanal(this)
        try { Recordatorios.reprogramarTodo(this) } catch (e: Exception) { SpikeLog.log("Recordatorios: ${e.message}") }
    }
}
