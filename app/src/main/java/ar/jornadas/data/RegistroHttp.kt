package ar.jornadas.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Logging mínimo de red (sin agregar logging-interceptor): método, URL, código y duración. */
class RegistroHttp : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        val inicio = System.nanoTime()
        return try {
            chain.proceed(pedido).also {
                Log.d(TAG, "${pedido.method()} ${pedido.url()} → ${it.code()} (${milisDesde(inicio)} ms)")
            }
        } catch (e: IOException) {
            Log.w(TAG, "${pedido.method()} ${pedido.url()} falló tras ${milisDesde(inicio)} ms: ${e.message}")
            throw e
        }
    }

    private fun milisDesde(inicio: Long) = (System.nanoTime() - inicio) / 1_000_000

    private companion object {
        const val TAG = "JornadasHttp"
    }
}
