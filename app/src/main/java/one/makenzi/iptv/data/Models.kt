package one.makenzi.iptv.data
import kotlinx.serialization.Serializable
@Serializable data class Channel(
  val id: String, val name: String, val url: String,
  val tvgId: String? = null, val logo: String? = null, val group: String? = null,
  val addedAt: Long = System.currentTimeMillis(), val lastPlayed: Long = 0L, val fav: Boolean = false)
@Serializable data class Playlist(val id: String, val title: String, val channels: List<Channel>)
@Serializable data class EpgItem(val start: Long, val stop: Long, val title: String)
typealias Epg = Map<String, List<EpgItem>>