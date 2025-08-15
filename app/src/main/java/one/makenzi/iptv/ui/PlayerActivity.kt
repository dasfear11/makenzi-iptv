package one.makenzi.iptv.ui
import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.serialization.json.Json
import one.makenzi.iptv.R
import one.makenzi.iptv.data.Channel
import one.makenzi.iptv.data.Epg
import one.makenzi.iptv.data.Store
class PlayerActivity : AppCompatActivity() {
  private lateinit var store: Store
  private lateinit var player: ExoPlayer
  private lateinit var playerView: PlayerView
  private lateinit var title: TextView
  private lateinit var btnPrev: Button; private lateinit var btnNext: Button
  private lateinit var btnAspect: Button; private lateinit var btnFav: Button; private lateinit var btnPiP: Button
  private lateinit var volume: SeekBar
  private lateinit var nowTitle: TextView; private lateinit var nextTitle: TextView
  private var current: Channel? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState); setContentView(R.layout.activity_player)
    store = Store(this).also { it.load() }
    playerView = findViewById(R.id.playerView)
    title = findViewById(R.id.title)
    btnPrev = findViewById(R.id.btnPrev); btnNext = findViewById(R.id.btnNext)
    btnAspect = findViewById(R.id.btnAspect); btnFav = findViewById(R.id.btnFav); btnPiP = findViewById(R.id.btnPiP)
    nowTitle = findViewById(R.id.nowTitle); nextTitle = findViewById(R.id.nextTitle)
    volume = findViewById(R.id.volume)
    val btnTracks: Button = findViewById(R.id.btnTracks)
    val chId = intent.getStringExtra("channelId")
    val all = store.state.playlists.flatMap { it.channels }
    current = all.find { it.id == chId } ?: all.firstOrNull()
val httpFactory = DefaultHttpDataSource.Factory()
  .setConnectTimeoutMs(store.state.connectTimeoutMs)
  .setReadTimeoutMs(store.state.readTimeoutMs)
  .also { f -> store.state.userAgent?.let { f.setUserAgent(it) } }

// DataSource, который умеет и http/https, и rtmp (когда rtmp-расширение на classpath)
val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory)

val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
    val renderersFactory: RenderersFactory = DefaultRenderersFactory(this)
    val loadControl = DefaultLoadControl.Builder()
      .setBufferDurationsMs(store.state.minBufferMs, store.state.maxBufferMs, store.state.playBufferMs, store.state.rebufferMs).build()
    player = ExoPlayer.Builder(this).setRenderersFactory(renderersFactory).setMediaSourceFactory(mediaSourceFactory).setLoadControl(loadControl).build()
    playerView.player = player
    volume.max = 100; volume.progress = store.state.volume
    volume.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { player.volume = progress / 100f }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) { store.state = store.state.copy(volume = volume.progress); store.save() }
    })
    btnAspect.setOnClickListener { cycleAspect() }
    btnFav.setOnClickListener { toggleFav() }
    btnPrev.setOnClickListener { step(-1) }
    btnNext.setOnClickListener { step(+1) }
    btnPiP.setOnClickListener { enterPiP() }
    btnTracks.setOnClickListener { showTracksDialog() }
    playCurrent()
  }
  private fun playCurrent() {
    val ch = current ?: return
    title.text = ch.name
    val mediaItem = MediaItem.fromUri(Uri.parse(ch.url))
    player.setMediaItem(mediaItem); player.prepare(); player.play(); updateNowNext()
  }
  private fun cycleAspect() {
    val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    val idx = modes.indexOf(playerView.resizeMode); playerView.resizeMode = modes[(idx + 1) % modes.size]
  }
  private fun toggleFav() {
    val ch = current ?: return
    val updatedPlaylists = store.state.playlists.map { pl -> pl.copy(channels = pl.channels.map { if (it.id == ch.id) it.copy(fav = !it.fav) else it }) }
    store.state = store.state.copy(playlists = updatedPlaylists); store.save()
  }
  private fun step(delta: Int) {
    val all = store.state.playlists.flatMap { it.channels }
    val idx = all.indexOfFirst { it.id == current?.id }
    if (idx != -1) { current = all[(idx + delta + all.size) % all.size]; playCurrent() }
  }
  private fun enterPiP() { if (Build.VERSION.SDK_INT >= 26) enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build()) }
  private fun updateNowNext() {
    val ch = current ?: return; val tvg = ch.tvgId ?: return
    val f = getFileStreamPath("epg.json"); if (!f.exists()) return
    runCatching {
      val list = (Json.decodeFromString<Epg>(f.readText())[tvg]) ?: return
      val now = System.currentTimeMillis()
      val cur = list.find { now in it.start..it.stop } ?: return
      val next = list.firstOrNull { it.start >= cur.stop }
      val total = (cur.stop - cur.start).coerceAtLeast(1)
      val progress = ((now - cur.start) * 100 / total).toInt().coerceIn(0,100)
      nowTitle.text = "Сейчас — ${cur.title}"
      nextTitle.text = if (next != null) "Далее — ${next.title}" else "Далее — нет данных"
      findViewById<android.widget.ProgressBar>(R.id.nowProgress).progress = progress
    }.onFailure { }
  }
private fun showTracksDialog() {
  val tracks = player.currentTracks ?: return
  val ctx = this
  val items = mutableListOf<String>()
  val selectors = mutableListOf<Pair<Int, Int>>() // groupIndex, trackIndex

  for (gIndex in 0 until tracks.groups.size) {
    val g = tracks.groups[gIndex]
    val type = g.type
    for (tIndex in 0 until g.length) {
      val f = g.getTrackFormat(tIndex)
      val label = when (type) {
        androidx.media3.common.C.TRACK_TYPE_AUDIO -> "Аудио: " + (f.language ?: "unknown")
        androidx.media3.common.C.TRACK_TYPE_TEXT -> "Субтитры: " + (f.language ?: "unknown")
        else -> continue
      }
      items += label
      selectors += gIndex to tIndex
    }
  }

  if (items.isEmpty()) {
    android.widget.Toast.makeText(ctx, "Дорожки недоступны", android.widget.Toast.LENGTH_SHORT).show()
    return
  }

  android.app.AlertDialog.Builder(ctx)
    .setTitle("Выбор дорожки")
    .setItems(items.toTypedArray()) { _, which ->
      val (gIndex, tIndex) = selectors[which]
      val override = androidx.media3.common.TrackSelectionOverride(tracks.groups[gIndex].mediaTrackGroup, listOf(tIndex))
      val params = player.trackSelectionParameters.buildUpon().setOverrideForType(override).build()
      player.trackSelectionParameters = params
    }
    .setNegativeButton("Отмена", null)
    .show()
}

  override fun onStop() { super.onStop(); if (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode) return; player.pause() }
  override fun onDestroy() { super.onDestroy(); player.release() }
}
