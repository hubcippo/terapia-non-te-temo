package com.carletto.terapianontetemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carletto.terapianontetemo.ui.AppRoot
import com.carletto.terapianontetemo.ui.theme.TerapiaTheme

/**
 * Unica Activity dell'app (single-Activity + Compose Navigation).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TerapiaTheme {
                AppRoot()
            }
        }
    }
}
