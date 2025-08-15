package one.makenzi.iptv.ui
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.*
import one.makenzi.iptv.R
import one.makenzi.iptv.data.Channel
import one.makenzi.iptv.data.Playlist
import one.makenzi.iptv.data.Store
import one.makenzi.iptv.util.M3uParser
import one.makenzi.iptv.util.Net
import one.makenzi.iptv.util.Xmltv
import java.util.UUID
import java.util.zip.ZipInputStream
class TvMainActivity : AppCompatActivity() {
  private val scope = MainScope()
  private lateinit var store: Store
  private lateinit var list: RecyclerView
  private lateinit var adapter: ChannelsAdapter
  private lateinit var search: EditText
  private lateinit var favsOnly: CheckBox
  private lateinit var sort: Spinner
  private lateinit var groupSpinner: Spinner
  private lateinit var groupChips: ChipGroup
  private lateinit var epgBtn: Button
  private lateinit var importBtn: Button
  private lateinit var settingsBtn: Button
  private var channels: MutableList<Channel> = mutableListOf()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState); setContentView(R.layout.activity_tv_main)
    store = Store(this); store.load()
    list = findViewById(R.id.list); list.layoutManager = LinearLayoutManager(this)
    adapter = ChannelsAdapter { ch, action -> when (action) { "play" -> play(ch); "fav" -> toggleFav(ch) } }; list.adapter = adapter
    search = findViewById(R.id.search); favsOnly = findViewById(R.id.favsOnly); sort = findViewById(R.id.sort)
    groupSpinner = findViewById(R.id.groupSpinner); groupChips = findViewById(R.id.groupChips)
    epgBtn = findViewById(R.id.btnEpg); importBtn = findViewById(R.id.btnImport); settingsBtn = findViewById(R.id.btnSettings)
    sort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("name","addedAt","lastPlayed"))
    search.setOnEditorActionListener { _, i, _ -> if (i == EditorInfo.IME_ACTION_SEARCH) { applyFilter(); true } else false }
    favsOnly.setOnCheckedChangeListener { _, _ -> applyFilter() }
    sort.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { applyFilter() }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    importBtn.setOnClickListener { showImportChooser() }
    epgBtn.setOnClickListener { importEpg() }
    settingsBtn.setOnClickListener { openSettingsDialog() }
    refreshChannels()
    val groups = (channels.mapNotNull { it.group }.toSet().toList().sorted())
    val allGroups = listOf("Все") + groups
    groupSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, allGroups)
    groupSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { applyFilter() }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    buildGroupChips()
  }
  private fun refreshChannels() { channels = store.state.playlists.flatMap { it.channels }.toMutableList(); applyFilter() }
  private fun buildGroupChips() {
    groupChips.removeAllViews()
    val groups = (channels.mapNotNull { it.group }.toSet().toList().sorted()); val all = listOf("Все") + groups
    for (g in all) {
      val chip = Chip(this).apply { text = g; isCheckable = true; isChecked = g == "Все"; setOnClickListener { chipClick(g) } }
      groupChips.addView(chip)
    }
  }
  private fun chipClick(group: String) {
    val idx = (0 until (groupSpinner.adapter?.count ?: 0)).firstOrNull { (groupSpinner.adapter.getItem(it) as String) == group } ?: 0
    groupSpinner.setSelection(idx); applyFilter()
  }
  private fun applyFilter() {
    var list = channels.toList()
    val q = search.text.toString().trim().lowercase()
    val selectedGroup = (groupSpinner.selectedItem as? String) ?: "Все"
    if (q.isNotEmpty()) list = list.filter { it.name.lowercase().contains(q) || (it.group?:"").lowercase().contains(q) }
    if (favsOnly.isChecked) list = list.filter { it.fav }
    if (selectedGroup != "Все") list = list.filter { (it.group ?: "") == selectedGroup }
    when (sort.selectedItem as String) {
      "name" -> list = list.sortedBy { it.name.lowercase() }
      "addedAt" -> list = list.sortedByDescending { it.addedAt }
      "lastPlayed" -> list = list.sortedByDescending { it.lastPlayed }
    }
    adapter.submit(list)
  }
  private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    uri ?: return@registerForActivityResult
    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); importFromUri(uri)
  }
  private fun showImportChooser() {
    openDocument.launch(arrayOf("application/octet-stream","application/zip","application/x-mpegURL","application/vnd.apple.mpegurl","text/plain","application/xml"))
  }
  private fun importFromUri(uri: Uri) {
    scope.launch {
      val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Playlist"
      val data = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
      val channels = if (name.endsWith(".zip", true)) parseZip(data) else parseM3uOrXml(data)
      if (channels.isNotEmpty()) {
        val pl = Playlist(UUID.randomUUID().toString(), name, channels)
        store.state = store.state.copy(playlists = store.state.playlists + pl); store.save(); refreshChannels()
        Toast.makeText(this@TvMainActivity, "Импортировано ${channels.size}", Toast.LENGTH_SHORT).show()
      } else Toast.makeText(this@TvMainActivity, "Не найдено каналов", Toast.LENGTH_SHORT).show()
    }
  }
  private suspend fun parseZip(bytes: ByteArray): List<Channel> = withContext(Dispatchers.IO) {
    val zis = ZipInputStream(bytes.inputStream()); var entry = zis.nextEntry; var channels = emptyList<Channel>()
    while (entry != null) { val name = entry.name.lowercase(); val content = zis.readBytes()
      if (name.endsWith(".m3u") || name.endsWith(".m3u8")) channels = M3uParser.parse(String(content)); entry = zis.nextEntry }
    channels
  }
  private suspend fun parseM3uOrXml(bytes: ByteArray): List<Channel> { val text = String(bytes); return M3uParser.parse(text) }
  private fun importEpg() {
    val input = EditText(this); input.hint = "https://.../guide.xml(.gz)"
    AlertDialog.Builder(this).setTitle("Обновить EPG").setView(input)
      .setPositiveButton("Загрузить") { _, _ ->
        val url = input.text.toString().trim()
        if (url.isNotEmpty()) {
          scope.launch {
            runCatching {
              val xml = Net.fetch(url).toString(Charsets.UTF_8)
              val epg = Xmltv.parse(xml, store.state.epgOffsetHours)
              val json = kotlinx.serialization.json.Json.encodeToString(epg)
              openFileOutput("epg.json", MODE_PRIVATE).use { it.write(json.toByteArray()) }
              store.state = store.state.copy(epgUrl = url); store.save()
              Toast.makeText(this@TvMainActivity, "EPG обновлён", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(this@TvMainActivity, "Ошибка EPG: ${it.message}", Toast.LENGTH_LONG).show() }
          }
        }
      }.setNegativeButton("Отмена", null).show()
  }
  private fun openSettingsDialog() {
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,8) }
    fun et(h:String, t:String="") = EditText(this).apply { hint = h; setText(t) }
    val ua = et("User-Agent", store.state.userAgent ?: "")
    val connect = et("Connect timeout ms", store.state.connectTimeoutMs.toString())
    val read = et("Read timeout ms", store.state.readTimeoutMs.toString())
    val minB = et("Min buffer ms", store.state.minBufferMs.toString())
    val maxB = et("Max buffer ms", store.state.maxBufferMs.toString())
    val playB = et("Play buffer ms", store.state.playBufferMs.toString())
    val rebuf = et("Rebuffer ms", store.state.rebufferMs.toString())
    val preferSW = CheckBox(this).apply { text = "Предпочитать SW декодер"; isChecked = store.state.preferSoftwareDecoders }
    listOf(ua, connect, read, minB, maxB, playB, rebuf).forEach { layout.addView(it) }; layout.addView(preferSW)
    AlertDialog.Builder(this).setTitle("Настройки плеера").setView(layout)
      .setPositiveButton("Сохранить") { _, _ ->
        store.state = store.state.copy(
          userAgent = ua.text.toString().ifBlank { null },
          connectTimeoutMs = connect.text.toString().toIntOrNull() ?: store.state.connectTimeoutMs,
          readTimeoutMs = read.text.toString().toIntOrNull() ?: store.state.readTimeoutMs,
          minBufferMs = minB.text.toString().toIntOrNull() ?: store.state.minBufferMs,
          maxBufferMs = maxB.text.toString().toIntOrNull() ?: store.state.maxBufferMs,
          playBufferMs = playB.text.toString().toIntOrNull() ?: store.state.playBufferMs,
          rebufferMs = rebuf.text.toString().toIntOrNull() ?: store.state.rebufferMs,
          preferSoftwareDecoders = preferSW.isChecked
        ); store.save(); Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
      }.setNegativeButton("Отмена", null).show()
  }
  private fun play(ch: Channel) { val i = Intent(this, PlayerActivity::class.java); i.putExtra("channelId", ch.id); startActivity(i) }
  private fun toggleFav(ch: Channel) {
    val updated = channels.map { if (it.id == ch.id) it.copy(fav = !it.fav) else it }
    val newPlaylists = store.state.playlists.map { pl -> pl.copy(channels = pl.channels.map { c -> updated.find { it.id == c.id } ?: c }) }
    store.state = store.state.copy(playlists = newPlaylists); store.save(); refreshChannels()
  }
  override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
class ChannelsAdapter(private val onAction: (Channel, String) -> Unit) : RecyclerView.Adapter<ChannelsAdapter.VH>() {
  private val items = mutableListOf<Channel>()
  fun submit(list: List<Channel>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
  override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
    val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false); return VH(v)
  }
  override fun onBindViewHolder(holder: VH, position: Int) {
    val ch = items[position]
    holder.name.text = ch.name; holder.group.text = ch.group ?: ""; holder.meta.text = if (ch.fav) "★" else ""
    if (!ch.logo.isNullOrBlank()) holder.logo.load(ch.logo)
    holder.btnFav.setOnClickListener { onAction(ch, "fav") }; holder.btnPlay.setOnClickListener { onAction(ch, "play") }
    holder.itemView.isFocusable = true; holder.itemView.isFocusableInTouchMode = true
  }
  override fun getItemCount(): Int = items.size
  class VH(v: android.view.View): RecyclerView.ViewHolder(v) {
    val logo: ImageView = v.findViewById(R.id.logo); val name: TextView = v.findViewById(R.id.name)
    val group: TextView = v.findViewById(R.id.group); val meta: TextView = v.findViewById(R.id.meta)
    val btnFav: Button = v.findViewById(R.id.btnFav); val btnPlay: Button = v.findViewById(R.id.btnPlay)
  }
}