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
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AudioController(private val context: Context) {
    companion object {
        const val RECORDING_RATE = 16000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record() {
        val bufferSize = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT) * 3
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, RECORDING_RATE, CHANNEL_IN, FORMAT, bufferSize)
        audioRecord.startRecording()

        try {
            // Datenpuffer Größe auf etwa 50 KB einstellen
            val sendThreshold = 1024 * 50
            val dataBuffer = ByteArrayOutputStream()

            val data = ByteArray(bufferSize)
            while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord.read(data, 0, data.size)

                if (read > 0) {
                    dataBuffer.write(data, 0, read)
                }

                // Wenn die Puffergröße größer oder gleich 100KB ist, senden Sie die Daten
                if (dataBuffer.size() >= sendThreshold) {
                    Log.d("AudioController", "Send")
                    sendAudioData(dataBuffer.toByteArray())
                    dataBuffer.reset()
                }
            }

            // die verbleibenden Daten senden, wenn die Aufnahme gestoppt wird
            if (dataBuffer.size() > 0) {
                sendAudioData(dataBuffer.toByteArray())
                dataBuffer.reset()
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    private suspend fun sendAudioData(audioData: ByteArray) {

        // Erstelle DataMap, das die Audiodaten enthält
        val dataMap = DataMap().apply {
            putByteArray("audio_data", audioData)
            putLong("timestamp", Date().time)
        }

        // Erstelle eine Anforderung zur Platzierung eines DataItem mit eindeutigem Pfad in der Data Layer
        val putDataReq = PutDataMapRequest.create("/path/to/audio/data/${UUID.randomUUID()}")
        putDataReq.dataMap.putAll(dataMap)

        val request = putDataReq.asPutDataRequest().setUrgent()

        // Sende das DataItem
        val dataClient = Wearable.getDataClient(context)

        withContext(Dispatchers.IO) {
           Tasks.await(dataClient.putDataItem(request))

            // Wir haben in diesem Fall kein enum "isSuccess" oder "isSuccessful" zur Verfügung.
            // Die erfolgreiche Veröffentlichung des DataItem sollte nicht zu einer Exception führen.
            Log.d("AudioController", "Data sent successfully")
        }
    }
}