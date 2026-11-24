package com.aidly.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Mi Aidly": elegir cual de los 6 avatares se ve en todas las pantallas. */
class AvatarActivity : ActividadAidly() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AidlyTema {
                Column(
                    Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EncabezadoPantalla(stringResource(R.string.av_titulo)) { finish() }
                    Text(stringResource(R.string.av_sub), fontSize = 20.sp, color = Tema.TextoSuave)
                    SelectorAvatar()
                }
            }
        }
    }
}
