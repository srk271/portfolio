package dev.bidu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Setup, settings and history. Bidu itself runs in the background service; this screen is optional. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val factory = biduViewModelFactory(this)
        setContent {
            BiduTheme {
                BiduApp(factory)
            }
        }
    }
}
