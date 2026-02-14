package com.soneso.stellar.smartdemo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soneso.smartdemo.App
import com.soneso.smartdemo.platform.initAndroidClipboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initAndroidClipboard(this)

        setContent {
            App()
        }
    }
}
