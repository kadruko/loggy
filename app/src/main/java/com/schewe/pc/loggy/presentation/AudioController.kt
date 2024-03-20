package com.schewe.pc.loggy.presentation

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AudioController(private val context: Context) {
    companion object {
        const val RECORDING_RATE = 16000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHANNEL_PATH = "/audioChannel"
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record() {
        val bufferSize = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT) * 3
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, RECORDING_RATE, CHANNEL_IN, FORMAT, bufferSize)
        audioRecord.startRecording()

        val channel = withContext(Dispatchers.IO) { openAudioChannel() }
        if (channel == null) {
            Log.e("AudioController", "Failed to open audio channel")
            return
        }

        val channelClient = Wearable.getChannelClient(context)
        val outputStream = withContext(Dispatchers.IO) { Tasks.await(channelClient.getOutputStream(channel)) }

        try {
            val sendThreshold = 1024 * 1024 // 1MB
            val dataBuffer = ByteArrayOutputStream()

            outputStream.use { output ->
                val data = ByteArray(bufferSize)
                withContext(Dispatchers.IO) {
                    while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = audioRecord.read(data, 0, data.size)

                        if (read > 0) {
                            dataBuffer.write(data, 0, read)
                        }

                        // Wenn die Puffergröße größer/gleich 1MB ist, verschicken Sie die Daten
                        if (dataBuffer.size() >= sendThreshold) {
                            Log.d("AudioController", "Send")
                            output.write(dataBuffer.toByteArray())
                            dataBuffer.reset()
                        }
                    }
                }

                // Senden Sie die restlichen Daten, wenn die Aufnahme gestoppt wird
                if (dataBuffer.size() > 0) {
                    withContext(Dispatchers.IO) {
                        output.write(dataBuffer.toByteArray())
                    }
                    dataBuffer.reset()
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    private suspend fun openAudioChannel(): ChannelClient.Channel? {
        val nodes = suspendCoroutine<Result<MutableList<Node>>> { continuation ->
            Wearable.getNodeClient(context.applicationContext).connectedNodes
                .addOnSuccessListener {
                    continuation.resume(Result.success(it))
                }
                .addOnFailureListener {
                    continuation.resume(Result.failure(it))
                }
        }

        val node = nodes.getOrNull()?.firstOrNull()

        return node?.let {
            val channelClient = Wearable.getChannelClient(context)
            val openChannelTask = suspendCoroutine<Result<ChannelClient.Channel>> { continuation ->
                channelClient.openChannel(it.id, CHANNEL_PATH)
                    .addOnSuccessListener { channel ->
                        continuation.resume(Result.success(channel))
                    }
                    .addOnFailureListener { error ->
                        continuation.resume(Result.failure(error))
                    }
            }

            return openChannelTask.getOrNull()
        }
    }

    private suspend fun sendAudioData(audioData: ByteArray) {
        Log.d("X", "Send audio data")
        val nodes = suspendCoroutine<Result<MutableList<Node>>> { continuation ->
            Wearable.getNodeClient(context.applicationContext).connectedNodes
                .addOnSuccessListener {
                    continuation.resume(Result.success(it))
                }
                .addOnFailureListener {
                    continuation.resume(Result.failure(it))
                }
        }
        Log.d("X", "Found connected node")

        val node = nodes.getOrNull()?.firstOrNull()

        Log.d("X", "node $node")

        node?.let {
            val channelClient = Wearable.getChannelClient(context)
            val openChannelTask = channelClient.openChannel(it.id, CHANNEL_PATH)

            openChannelTask.addOnSuccessListener { channel ->
                val outputStreamTask = channelClient.getOutputStream(channel)
                outputStreamTask.addOnSuccessListener { outputStream ->
                    outputStream.use { it.write(audioData) }
                }
            }
        }
    }
}