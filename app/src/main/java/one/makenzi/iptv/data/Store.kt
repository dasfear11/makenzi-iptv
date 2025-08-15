package one.makenzi.iptv.data
import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
class Store(private val context: Context) {
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
  private val file get() = File(context.filesDir, "data.json")
  data class State(
    val playlists: List<Playlist> = emptyList(), val currentPlaylistId: String? = null, val currentChannelId: String? = null,
    val epgUrl: String? = null, val epgOffsetHours: Int = 0, val volume: Int = 80, val sort: String = "name", val favsOnly: Boolean = false,
    val preferSoftwareDecoders: Boolean = false, val minBufferMs: Int = 15000, val maxBufferMs: Int = 50000, val playBufferMs: Int = 2500, val rebufferMs: Int = 5000,
    val userAgent: String? = null, val connectTimeoutMs: Int = 10000, val readTimeoutMs: Int = 20000
  )
  var state: State = State()
  fun load() { if (file.exists()) runCatching { json.decodeFromString<State>(file.readText()) }.onSuccess { state = it } }
  fun save() { file.writeText(json.encodeToString(state)) }
}