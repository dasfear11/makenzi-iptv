package one.makenzi.iptv.work
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.makenzi.iptv.data.Store
import one.makenzi.iptv.util.Net
import one.makenzi.iptv.util.Xmltv
class EpgWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val store = Store(applicationContext); store.load()
    val url = store.state.epgUrl ?: return Result.success()
    return runCatching {
      val xml = Net.fetch(url).toString(Charsets.UTF_8)
      val epg = Xmltv.parse(xml, store.state.epgOffsetHours)
      val json = Json.encodeToString(epg)
      applicationContext.openFileOutput("epg.json", Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
  }
}