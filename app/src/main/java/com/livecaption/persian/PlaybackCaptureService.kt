package com.livecaption.persian

import android.app.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Build
import android.util.Log

/** Captures eligible app playback PCM on Android 10+.
 * Important: PCM is not transcribed yet; an ASR engine (e.g. Vosk/Whisper) must consume it. */
class PlaybackCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = Notification.Builder(this, "capture")
            .setContentTitle("LiveCaption Persian")
            .setContentText("دریافت صدای داخلی فعال است")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(7, notification)
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("projectionData")
        if (resultCode != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val p = projection ?: return
        val sampleRate = 16000
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val config = AudioPlaybackCaptureConfiguration.Builder(p)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(min * 2)
            .setAudioPlaybackCaptureConfig(config).build()
        running = true
        recorder?.startRecording()
        Thread {
            val buffer = ShortArray(2048)
            while (running) {
                val count = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (count > 0) {
                    // TODO: send PCM frames to integrated speech-recognition engine.
                    Log.d("LiveCaption", "Captured $count PCM samples")
                }
            }
        }.start()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("capture", "Live captions", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onDestroy() {
        running = false
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        projection?.stop(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
