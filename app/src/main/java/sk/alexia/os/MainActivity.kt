package sk.alexia.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sk.alexia.os.system.WidgetHost
import sk.alexia.os.ui.AlexiaOS
import sk.alexia.os.ui.theme.AlexiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { AlexiaTheme { AlexiaOS() } }
    }

    override fun onStart() {
        super.onStart()
        WidgetHost.start(this)
    }

    override fun onStop() {
        super.onStop()
        WidgetHost.stop(this)
    }
}
