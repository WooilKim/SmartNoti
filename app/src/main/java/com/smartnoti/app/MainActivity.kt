package com.smartnoti.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.smartnoti.app.navigation.AppNavHost
import com.smartnoti.app.ui.theme.SmartNotiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartNotiTheme {
                AppNavHost()
            }
        }
    }
}
