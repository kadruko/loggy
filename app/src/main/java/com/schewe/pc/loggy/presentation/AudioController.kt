package com.schewe.pc.loggy.presentation

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.arthenica.mobileffmpeg.FFmpeg
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import java.util.UUID

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
            // Datenpuffer Größe auf etwa 500 KB einstellen
            val sendThreshold = 1024 * 500
            val dataBuffer = ByteArrayOutputStream()

            val data = ByteArray(bufferSize)
            while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord.read(data, 0, data.size)

                if (read > 0) {
                    dataBuffer.write(data, 0, read)
                }

                // Wenn die Puffergröße größer oder gleich Schwellwert ist, senden Sie die Daten
                if (dataBuffer.size() >= sendThreshold) {
                    Log.d("AudioController", "Send")
                    sendAudioData(convertToMp3(dataBuffer.toByteArray()))
                    dataBuffer.reset()
                }
            }

            // die verbleibenden Daten senden, wenn die Aufnahme gestoppt wird
            if (dataBuffer.size() > 0) {
                sendAudioData(convertToMp3(dataBuffer.toByteArray()))
                dataBuffer.reset()
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    private fun convertToMp3(audioData: ByteArray): ByteArray {
        Log.d("X", "Convert to mp3")
        val pcmFile = File(context.cacheDir, "audio.pcm")
        pcmFile.writeBytes(audioData)
        val mp3File = File(context.cacheDir, "audio.mp3")
        val command = "-y -f s16le -ar 16k -ac 1 -i ${pcmFile.absolutePath} ${mp3File.absolutePath}"
        FFmpeg.execute(command)
        val result = mp3File.readBytes()
        Log.d("X", "Converted PCM audio of size ${audioData.size / 1024}KB to MP3 with size ${result.size / 1024}KB.")
        pcmFile.delete()
        mp3File.delete()
        return result
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