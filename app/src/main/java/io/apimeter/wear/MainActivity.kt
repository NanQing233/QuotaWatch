package io.apimeter.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.apimeter.wear.ui.ApiMeterApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ApiMeterApplication).container
        setContent {
            ApiMeterApp(container)
        }
    }
}
