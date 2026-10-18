package app.meteorite

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MeteoViewModel by viewModels()

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) vm.addMyLocation() else vm.locationDenied()
        }

    private val backgroundPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.recheckPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keeps "Ma position" up to date while the app is on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.trackMyLocation() }
        }
        setContent {
            MeteoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MeteoRoot(vm, onAllowBackground = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }, onLocate = {
                        if (vm.hasLocationPermission()) {
                            vm.addMyLocation()
                        } else {
                            locationPermission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                )
                            )
                        }
                    })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.ensureLoaded()
        vm.recheckPermissions()
    }
}
