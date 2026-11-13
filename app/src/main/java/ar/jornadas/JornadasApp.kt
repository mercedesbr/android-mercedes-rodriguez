package ar.jornadas

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class JornadasApp : Application() {
    val servicios by lazy { Servicios(this) }
}

/** Para las fábricas de ViewModel: obtiene las dependencias compartidas de la app. */
fun CreationExtras.servicios(): Servicios = (checkNotNull(this[APPLICATION_KEY]) as JornadasApp).servicios
