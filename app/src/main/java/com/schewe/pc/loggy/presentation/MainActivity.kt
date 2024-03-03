package com.schewe.pc.loggy.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.schewe.pc.loggy.R
import com.schewe.pc.loggy.presentation.theme.LoggyTheme
import kotlinx.coroutines.Job


class MainActivity : ComponentActivity() {
    private lateinit var recordingServiceManager: RecordingServiceManager
    private val recordingService: RecordingService?
        get() = recordingServiceManager.recordingServiceFlow.value
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.POST_NOTIFICATIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        recordingServiceManager = RecordingServiceManager(application)

        setContent {
            WearApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        recordingService?.stopRecording()
        recordingService?.stopSelf()
    }

    @Composable
    fun WearApp() {
        val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            var permitted = true
            for(permission in permissions){
                if(it[permission] == false){
                    permitted = false
                }
            }
            if (permitted) {
                startRecording()
            }
        }
        var isRunning by remember { mutableStateOf(false) }

        LoggyTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        isRunning = !isRunning
                        if(isRunning){
                            checkPermission(requestPermissionLauncher) { startRecording() }
                        }else{
                            stopRecording()
                        }
                    },
                    modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                ) {
                    Icon(
                        painter = painterResource(id = if(isRunning) R.drawable.baseline_stop_24 else R.drawable.baseline_play_arrow_24),
                        contentDescription = "start",
                        modifier = Modifier
                            .size(ButtonDefaults.LargeButtonSize)
                            .wrapContentSize(align = Alignment.Center)
                    )
                }
            }
        }
    }

    private fun checkPermission(requestPermissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>, callback: () -> Unit){
        if(hasPermissions(application, permissions)){
            callback()
        }else{
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
        if (context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun startRecording(){
        Log.d("X", "startRecording: $recordingService")
        recordingService?.startRecording()
    }

    private fun stopRecording(){
        recordingService?.stopRecording()
    }

    @Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
    @Composable
    fun DefaultPreview() {
        WearApp()
    }
}