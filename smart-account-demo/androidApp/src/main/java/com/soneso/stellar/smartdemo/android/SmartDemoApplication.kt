package com.soneso.stellar.smartdemo.android

import android.app.Application
import android.util.Log
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.soneso.smartdemo.config.DemoConfig

/**
 * Application subclass that initializes Reown CoreClient and SignClient at app start.
 *
 * Both clients must be initialized before any Activity is created so that the
 * relay connection is available when the wallet connector is first used.
 * Initialization is skipped when REOWN_PROJECT_ID is blank to allow the app
 * to run without wallet connection support during development.
 */
class SmartDemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val projectId = DemoConfig.REOWN_PROJECT_ID
        if (projectId.isBlank()) {
            Log.w(TAG, "REOWN_PROJECT_ID is not set. Wallet connection via Freighter Mobile will not work.")
            return
        }

        CoreClient.initialize(
            application = this,
            projectId = projectId,
            metaData = Core.Model.AppMetaData(
                name = "Stellar Smart Account Demo",
                description = "Smart account demo app for the KMP Stellar SDK",
                url = "https://soneso.com",
                icons = listOf("https://soneso.com/icon.png"),
                redirect = "stellar-smartdemo://request"
            ),
            connectionType = ConnectionType.AUTOMATIC,
            onError = { error ->
                Log.e(TAG, "CoreClient initialization error: ${error.throwable.message}", error.throwable)
            }
        )

        SignClient.initialize(
            init = Sign.Params.Init(core = CoreClient),
            onSuccess = {
                Log.d(TAG, "SignClient initialized successfully")
            },
            onError = { error ->
                Log.e(TAG, "SignClient initialization error: ${error.throwable.message}", error.throwable)
            }
        )
    }

    private companion object {
        private const val TAG = "SmartDemoApplication"
    }
}
