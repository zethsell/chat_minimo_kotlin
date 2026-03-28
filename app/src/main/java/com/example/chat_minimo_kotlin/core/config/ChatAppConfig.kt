package com.example.chat_minimo_kotlin.core.config

import android.content.Context
import com.example.chat_minimo_kotlin.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defaults de URL e demo (matrícula / objeto) vindos de recursos.
 */
@Singleton
class ChatAppConfig @Inject constructor(
    @ApplicationContext context: Context,
) {
    val defaultBffBaseUrl: String =
        context.getString(R.string.chat_bff_base_url).trim().trimEnd('/')

    val demoFallbackUserId: String =
        context.getString(R.string.chat_demo_matricula).trim()

    val demoCodigosObjeto: List<String> =
        listOf(context.getString(R.string.chat_demo_codigo_objeto).trim())
}
