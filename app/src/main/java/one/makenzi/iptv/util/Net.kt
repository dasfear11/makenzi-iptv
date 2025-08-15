package one.makenzi.iptv.util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
object Net {
  suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10000; conn.readTimeout = 20000; conn.instanceFollowRedirects = true
    conn.inputStream.use { it.readBytes() }
  }
}