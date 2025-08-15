package com.example.makenziiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)

        // Настраиваем HTTP источник с таймаутами и User-Agent
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000) // 15 сек
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("MAKENZI-IPTV/1.0")

        // Основная фабрика источников данных
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        // Фабрика медиа-источников
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Создаём плеер
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerView.player = player

        // Получаем ссылку на видео из Intent
        val videoUrl = intent.getStringExtra("video_url") ?: return

        val mediaItem = MediaItem.fromUri(videoUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
