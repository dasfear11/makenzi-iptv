package one.makenzi.iptv.util
import android.util.Xml
import one.makenzi.iptv.data.Epg
import one.makenzi.iptv.data.EpgItem
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
object Xmltv {
  private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
  fun parse(xml: String, offsetHours: Int = 0): Epg {
    val parser = Xml.newPullParser(); parser.setInput(xml.reader())
    val epg = mutableMapOf<String, MutableList<EpgItem>>()
    var event = parser.eventType; var currentChannel: String? = null; var start = 0L; var stop = 0L; var title = ""
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    fun parseTime(raw: String?): Long {
      if (raw.isNullOrBlank()) return 0L
      val norm = if (raw.contains(" ")) raw else "$raw +0000"
      val ms = runCatching { fmt.parse(norm)?.time ?: 0L }.getOrElse { 0L }
      return ms + offsetHours * 3600_000L
    }
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> when (parser.name) {
          "programme" -> { currentChannel = parser.getAttributeValue(null, "channel"); start = parseTime(parser.getAttributeValue(null, "start")); stop = parseTime(parser.getAttributeValue(null, "stop")); title = "" }
          "title" -> { title = parser.nextText() }
        }
        XmlPullParser.END_TAG -> if (parser.name == "programme" && currentChannel != null) {
          val list = epg.getOrPut(currentChannel!!) { mutableListOf() }; list += EpgItem(start, stop, title); currentChannel = null
        }
      }
      event = parser.next()
    }
    return epg
  }
}