package com.neil1454.drawsomething

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neil1454.drawsomething.ui.LocalSaves
import com.neil1454.drawsomething.ui.SaveActions
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.neil1454.drawsomething.ui.DrawSomethingApp
import com.neil1454.drawsomething.ui.DrawSomethingTheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var afterPermissions: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val action = afterPermissions
            afterPermissions = null
            if (result.values.all { it }) {
                action?.invoke()
            } else {
                vm.showNotice(
                    "This app needs the Nearby/Bluetooth permissions to find your partner. " +
                        "Allow them when asked, or enable them in Settings > Apps > Neil & Teri's Time Killer Games.",
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DrawSomethingTheme {
                val saves by vm.saves.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalSaves provides SaveActions(saves, vm::resumeGame, vm::discardSave)) {
                    DrawSomethingApp(
                        vm,
                        onHost = { withPermissions { vm.host() } },
                        onJoin = { withPermissions { vm.join() } },
                    )
                }
            }
        }
    }

    override fun onStop() {
        // Going into the background (or being closed): make sure a long game in progress is saved.
        vm.flushSave()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        // Every time the app opens or comes back on screen: remove any downloaded update file that is no longer needed.
        vm.sweepUpdateFiles()
    }

    private fun withPermissions(action: () -> Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            afterPermissions = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /** What Nearby Connections needs on each Android version. */
    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
