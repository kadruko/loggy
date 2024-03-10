package com.schewe.pc.loggy.presentation

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream


class AudioController(val context: Context) {
    // https://github.com/android/wear-os-samples/blob/main/WearSpeakerSample/wear/src/main/java/com/example/android/wearable/speaker/SoundRecorder.kt
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun record(filenameCallback: (String) -> Unit) {
        val intSize = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT)
        val audioRecord =
            AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(
                AudioFormat.Builder().setSampleRate(RECORDING_RATE).setChannelMask(CHANNEL_IN)
                    .setEncoding(FORMAT).build()
            ).setBufferSizeInBytes(intSize * 3).build()

        val dataClient: DataClient = Wearable.getDataClient(context)

        var dataChunkFile = File(context.filesDir, generateFileName())
        // Initialize the fileOutputStream before entering the loop
        var fileOutputStream: OutputStream? = dataChunkFile.outputStream()

        filenameCallback(dataChunkFile.name)

        audioRecord.startRecording()

        try {
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(intSize)

                // Loop until coroutine is cancelled
                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)

                    // Check if adding more data exceeds the maximum size
                    if (dataChunkFile.length() + read > MAX_FILE_SIZE_BYTES) {
                        // If it does, close the current file, send it and start a new one
                        fileOutputStream?.close()

                        sendFileAsAsset(dataChunkFile, dataClient)

                        dataChunkFile = File(context.filesDir, generateFileName())
                        fileOutputStream = dataChunkFile.outputStream() // open new output stream
                        filenameCallback(dataChunkFile.name)
                    }

                    // Write audio data to the file
                    fileOutputStream?.write(buffer, 0, read)
                }

                // Don't forget to close and send the last piece of data that didn't reach the maximum size
                fileOutputStream?.close()
                sendFileAsAsset(dataChunkFile, dataClient)

            }
        } finally {
            audioRecord.release()
        }
    }

    private fun sendFileAsAsset(file: File, dataClient: DataClient) {
        val asset: Asset = Asset.createFromBytes(file.readBytes())
        val dataMap: PutDataMapRequest = PutDataMapRequest.create("/audio")
        dataMap.dataMap.putAsset("audioAsset", asset)
        val request: PutDataRequest = dataMap.asPutDataRequest()
        dataClient.putDataItem(request)
    }

    private fun generateFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "loggy_$timestamp.pcm"
    }

    companion object {
        const val RECORDING_RATE = 16000 // can go up to 44K, if needed
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024 // 10 MB
    }
}