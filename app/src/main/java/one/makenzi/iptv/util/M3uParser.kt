package one.makenzi.iptv.util
import one.makenzi.iptv.data.Channel
import java.util.UUID
object M3uParser {
  fun parse(text: String): List<Channel> {
    val lines = text.split('\n'); val out = mutableListOf<Channel>()
    var name: String? = null; var tvgId: String? = null; var logo: String? = null; var group: String? = null
    for (raw in lines) {
      val line = raw.trim()
      if (line.startsWith("#EXTINF", true)) {
        name = line.substringAfter(",").trim()
        tvgId = Regex("tvg-id=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1)
        logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1)
        group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1)
      } else if (name != null && line.isNotEmpty() && !line.startsWith("#")) {
        out += Channel(UUID.randomUUID().toString(), name!!, line, tvgId, logo, group)
        name = null; tvgId = null; logo = null; group = null
      }
    }
    return out
  }
}