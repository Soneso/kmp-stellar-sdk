package com.soneso.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soneso.demo.App
import com.soneso.demo.platform.initAndroidClipboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize clipboard with application context
        initAndroidClipboard(this)

        setContent {
            App()
        }
    }
}
