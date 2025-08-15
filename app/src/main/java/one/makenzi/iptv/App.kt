package one.makenzi.iptv
import android.app.Application
import androidx.work.*
import one.makenzi.iptv.work.EpgWorker
import java.util.concurrent.TimeUnit
class App : Application() {
  override fun onCreate() {
    super.onCreate()
    val request = PeriodicWorkRequestBuilder<EpgWorker>(24, TimeUnit.HOURS)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    WorkManager.getInstance(this).enqueueUniquePeriodicWork("epg_update", ExistingPeriodicWorkPolicy.KEEP, request)
  }
}