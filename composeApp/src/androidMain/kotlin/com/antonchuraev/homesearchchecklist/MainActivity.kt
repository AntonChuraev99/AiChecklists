package com.antonchuraev.homesearchchecklist

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.antonchuraev.homesearchchecklist.di.AppContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppContextHolder.init(applicationContext)
        enableEdgeToEdge(
            //statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT , Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val context = LocalContext.current
    AppContextHolder.init(context)
    App()
}