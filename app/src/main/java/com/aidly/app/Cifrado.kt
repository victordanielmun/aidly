package com.aidly.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado en reposo de lo sensible que Aidly guarda en el telefono (mensajes recibidos, recordatorios e historial de
 * medicinas, favoritos, codigo y datos del circulo familiar). AES-256-GCM con una clave del Android Keystore que no
 * sale del aparato y no se puede copiar con el archivo de datos.
 *  - Protege contra la copia del almacenamiento de la app (p. ej. extraer los archivos); no contra alguien que ya
 *    tiene el telefono desbloqueado ni contra un telefono con root que ejecute la propia app.
 *  - Formato: "c1:" + base64(iv + cifrado). Un valor sin ese prefijo es un dato antiguo sin cifrar: se lee tal cual y se
 *    cifra la proxima vez que se guarde (migracion automatica).
 *  - Si la clave se perdio (p. ej. datos restaurados en otro telefono), [abrir] devuelve null y el dato cuenta como vacio.
 */
object Cifrado {
    private const val ALIAS = "aidly_datos_v1"
    private const val PREFIJO = "c1:"
    private const val IV_BYTES = 12

    @Synchronized
    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun cerrar(texto: String): String = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, clave()) }
        PREFIJO + Base64.encodeToString(c.iv + c.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    } catch (e: Exception) {
        // Mejor guardar el dato que perderlo si el Keystore falla (poco comun): se cifrara en el siguiente guardado.
        SpikeLog.log("CIFRADO: no pude cifrar (${e::class.simpleName})"); texto
    }

    /** Devuelve el texto claro, o null si el valor es null o no se puede descifrar. */
    fun abrir(valor: String?): String? {
        if (valor == null) return null
        if (!valor.startsWith(PREFIJO)) return valor
        return try {
            val b = Base64.decode(valor.removePrefix(PREFIJO), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, b, 0, IV_BYTES))
            }
            String(c.doFinal(b, IV_BYTES, b.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            SpikeLog.log("CIFRADO: no pude descifrar (${e::class.simpleName})"); null
        }
    }

    fun existe(valor: String?) = valor != null && valor.startsWith(PREFIJO)
}
