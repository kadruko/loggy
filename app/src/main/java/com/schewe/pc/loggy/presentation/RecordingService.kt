package com.schewe.pc.loggy.presentation

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import com.schewe.pc.loggy.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.wear.ongoing.Status

class RecordingService : LifecycleService() {
    private val localBinder = LocalBinder()
    private lateinit var audioController: AudioController
    private var recordingJob: Job? = null

    inner class LocalBinder : Binder() {
        internal val recordingService: RecordingService
            get() = this@RecordingService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return localBinder
    }

    override fun onCreate() {
        super.onCreate()
        audioController = AudioController(application)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        Log.d("X","startRecording")

        val notification = generateNotification()
        startService(Intent(applicationContext, RecordingService::class.java))
        startForeground(1, notification)

        // do i need to change dispatcher?
        recordingJob = lifecycleScope.launch {
            audioController.record()
        }
    }

    fun stopRecording(): String? {
        Log.d("X","stopRecording")
        recordingJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        return ""
    }

    private fun generateNotification(): Notification {
        createNotificationChannel()

        val launchActivityIntent = Intent(this, MainActivity::class.java)

        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchActivityIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, "loggy")
                .setContentTitle("Loggy")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .addAction(
                    R.drawable.baseline_mic_24,
                    "Open",
                    activityPendingIntent,
                )

        val ongoingActivityStatus = Status.Builder()
            .addTemplate("Loggy")
            .build()

        val ongoingActivity =
            OngoingActivity.Builder(applicationContext, 1, notificationBuilder)
                .setStaticIcon(R.drawable.baseline_mic_24)
                .setTouchIntent(activityPendingIntent)
                .setStatus(ongoingActivityStatus)
                .build()

        ongoingActivity.apply(applicationContext)
        return notificationBuilder.build()
    }

    private fun createNotificationChannel() {
        val name = "Notification channel name"
        val descriptionText = "Notification channel description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("loggy", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}