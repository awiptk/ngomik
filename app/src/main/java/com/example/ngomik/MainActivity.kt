package com.example.ngomik

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.net.URLEncoder

data class MangaItem(val title: String, val href: String, val cover: String, val type: String)

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBottom: ProgressBar
    private var mangas: MutableList<MangaItem> = mutableListOf()
    private var visibleMangas: MutableList<MangaItem> = mutableListOf()
    private lateinit var adapter: MangaAdapter
    private var nextPageUrl: String? = null
    private var isLoading = false
    private var currentMode = ViewMode.LIBRARY

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var doubleBackToExit = false // untuk back press 2x

    // job untuk search debounce / cancel previous
    private var currentSearchJob: Job? = null

    enum class ViewMode {
        LIBRARY, BROWSE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("manga_prefs", MODE_PRIVATE)

        // Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Menu Drawer
        val navLibrary: TextView = findViewById(R.id.nav_library)
        val navBrowse: TextView = findViewById(R.id.nav_browse)
        val navSettings: TextView? = findViewById(R.id.nav_settings)
        val navAbout: TextView? = findViewById(R.id.nav_about)

        navLibrary.setOnClickListener {
            loadLibrary()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        navBrowse.setOnClickListener {
            loadBrowse()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        navSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        navAbout?.setOnClickListener {
            Toast.makeText(this, "About: Ngomik Reader v1.0", Toast.LENGTH_LONG).show()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        progressBottom = findViewById(R.id.progress_bottom)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MangaAdapter(visibleMangas) { item ->
            // klik item -> buka detail (kamu punya MangaDetailActivity)
            val intent = Intent(this@MainActivity, MangaDetailActivity::class.java)
            intent.putExtra("url", item.href)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Load awal
        loadLibrary()
    }

    // --- Menu (Search + Overflow with web options) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.queryHint = "Search..."
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim().orEmpty()
                if (currentMode == ViewMode.BROWSE) {
                    performSearchWithCancel(q)
                } else {
                    filterList(q)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val q = newText?.trim().orEmpty()
                if (currentMode == ViewMode.BROWSE) {
                    if (q.isBlank()) {
                        currentSearchJob?.cancel()
                        loadBrowse()
                    } else if (q.length >= 3) {
                        performSearchWithCancel(q)
                    }
                } else {
                    filterList(q)
                }
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // Open in internal WebView
            R.id.action_open_web -> {
                val url = prefs.getString("base_domain", "https://id.ngomik.cloud") ?: "https://id.ngomik.cloud"
                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("url", url)
                startActivity(intent)
                true
            }
            // Open in external browser
            R.id.action_open_in_browser -> {
                val url = prefs.getString("base_domain", "https://id.ngomik.cloud") ?: "https://id.ngomik.cloud"
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(Intent.createChooser(i, "Open with"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // helper: cancel previous job and start a new debounced search
    private fun performSearchWithCancel(query: String) {
        currentSearchJob?.cancel()
        currentSearchJob = lifecycleScope.launch {
            delay(250) // debounce 250ms
            searchOnline(query)
        }
    }

    private fun filterList(q: String) {
        val qLower = q.trim().lowercase()
        visibleMangas.clear()
        if (qLower.isEmpty()) {
            visibleMangas.addAll(mangas)
        } else {
            visibleMangas.addAll(mangas.filter { it.title.lowercase().contains(qLower) })
        }
        adapter.notifyDataSetChanged()
    }

    // --- LOAD LIBRARY ---
    private fun loadLibrary() {
        currentMode = ViewMode.LIBRARY
        supportActionBar?.title = "Library"

        // hentikan behaviour browse
        recyclerView.clearOnScrollListeners()
        nextPageUrl = null
        isLoading = false

        val bookmarksJson = prefs.getString("bookmarks", "[]")
        val type = object : TypeToken<List<MangaItem>>() {}.type
        val bookmarks: List<MangaItem> = gson.fromJson(bookmarksJson, type) ?: emptyList()

        mangas.clear()
        mangas.addAll(bookmarks)

        visibleMangas.clear()
        visibleMangas.addAll(mangas)
        adapter.notifyDataSetChanged()

        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "Library kosong. Browse manga untuk menambahkan.", Toast.LENGTH_LONG).show()
        }
    }

    // --- LOAD BROWSE ---
    private fun loadBrowse() {
        currentMode = ViewMode.BROWSE
        supportActionBar?.title = "Browse"

        // reset list & prepare infinite scroll
        mangas.clear()
        visibleMangas.clear()
        adapter.notifyDataSetChanged()

        // build base domain from prefs
        val baseDomain = prefs.getString("base_domain", "https://id.ngomik.cloud")!!
        nextPageUrl = "$baseDomain/manga/?order=update"

        // add infinite scroll listener
        recyclerView.clearOnScrollListeners()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val layoutManager = rv.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // trigger load when reaching end
                if (!isLoading && nextPageUrl != null &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                    firstVisibleItemPosition >= 0
                ) {
                    fetchMangaList()
                }
            }
        })

        fetchMangaList()
    }

    private fun fetchMangaList() {
        if (nextPageUrl == null) return
        progressBottom.visibility = View.VISIBLE
        isLoading = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(nextPageUrl).userAgent("Mozilla/5.0").get()
                // page listing selector
                val items = doc.select(".listupd .bs")
                val newMangasAll = items.map { el ->
                    val a = el.selectFirst("a")
                    val title = el.selectFirst(".tt")?.text()?.trim() ?: a?.attr("title") ?: a?.text() ?: ""
                    val href = a?.absUrl("href") ?: ""
                    val cover = el.selectFirst("img")?.absUrl("src") ?: ""
                    val type = el.selectFirst(".type")?.text()?.trim() ?: ""
                    MangaItem(title, href, cover, type)
                }.filter { it.title.isNotEmpty() && it.href.isNotEmpty() }

                // filter out duplicates already present in mangas (prevent doubling)
                val newMangas = newMangasAll.filter { nm -> mangas.none { it.href == nm.href } }

                nextPageUrl = doc.selectFirst("a.next.page-numbers")?.absUrl("href")

                withContext(Dispatchers.Main) {
                    val start = mangas.size
                    mangas.addAll(newMangas)
                    visibleMangas.addAll(newMangas)
                    if (newMangas.isNotEmpty()) {
                        adapter.notifyItemRangeInserted(start, newMangas.size)
                    } else {
                        // jika tidak ada item baru, notify full (safe)
                        adapter.notifyDataSetChanged()
                    }
                    progressBottom.visibility = View.GONE
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBottom.visibility = View.GONE
                    isLoading = false
                    Toast.makeText(this@MainActivity, "Gagal ambil data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Search online (untuk Browse) ---
    private suspend fun searchOnlineBlocking(query: String): List<MangaItem> {
        return withContext(Dispatchers.IO) {
            try {
                val baseDomain = prefs.getString("base_domain", "https://id.ngomik.cloud")!!
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "$baseDomain/?s=$encoded"
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()

                // kumpulkan candidate elements (fallback beberapa selector)
                val candidates = mutableListOf<org.jsoup.nodes.Element>()
                candidates.addAll(doc.select(".listupd .bs"))
                candidates.addAll(doc.select(".bsx .bs"))
                candidates.addAll(doc.select(".bsx"))
                candidates.addAll(doc.select(".search .result"))
                if (candidates.isEmpty()) {
                    doc.select("a:has(img)").forEach { candidates.add(it) }
                }

                // gunakan LinkedHashMap untuk deduplikasi berdasarkan href (preserve order)
                val map = linkedMapOf<String, MangaItem>()
                for (el in candidates) {
                    val a = el.selectFirst("a[href]") ?: continue
                    val href = a.absUrl("href").ifBlank { continue }
                    // jika sudah ada, skip
                    if (map.containsKey(href)) continue

                    val title = el.selectFirst(".tt")?.text()?.trim()
                        ?: a.attr("title").takeIf { it.isNotBlank() }
                        ?: a.text().takeIf { it.isNotBlank() }
                        ?: el.selectFirst("img")?.attr("alt") ?: ""
                    val cover = el.selectFirst("img")?.absUrl("src") ?: ""

                    if (title.isBlank()) continue

                    map[href] = MangaItem(title, href, cover, "")
                }

                map.values.toList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun searchOnline(query: String) {
        // cancel infinite scroll while showing search results
        nextPageUrl = null
        progressBottom.visibility = View.VISIBLE
        isLoading = true

        lifecycleScope.launch {
            val results = searchOnlineBlocking(query)
            progressBottom.visibility = View.GONE
            isLoading = false

            if (results.isEmpty()) {
                Toast.makeText(this@MainActivity, "Tidak ada hasil untuk \"$query\"", Toast.LENGTH_SHORT).show()
            }

            mangas.clear()
            mangas.addAll(results)
            visibleMangas.clear()
            visibleMangas.addAll(results)
            adapter.notifyDataSetChanged()
        }
    }

    // --- Bookmarks ---
    fun isBookmarked(item: MangaItem): Boolean {
        val bookmarksJson = prefs.getString("bookmarks", "[]")
        val type = object : TypeToken<List<MangaItem>>() {}.type
        val bookmarks: MutableList<MangaItem> = gson.fromJson(bookmarksJson, type) ?: mutableListOf()
        return bookmarks.any { it.href == item.href }
    }

    fun toggleBookmark(item: MangaItem) {
        val bookmarksJson = prefs.getString("bookmarks", "[]")
        val type = object : TypeToken<List<MangaItem>>() {}.type
        val bookmarks: MutableList<MangaItem> = gson.fromJson(bookmarksJson, type) ?: mutableListOf()

        val existing = bookmarks.indexOfFirst { it.href == item.href }
        if (existing >= 0) {
            bookmarks.removeAt(existing)
            Toast.makeText(this, "Dihapus dari library", Toast.LENGTH_SHORT).show()
        } else {
            bookmarks.add(item)
            Toast.makeText(this, "Ditambahkan ke library", Toast.LENGTH_SHORT).show()
        }

        prefs.edit().putString("bookmarks", gson.toJson(bookmarks)).apply()

        if (currentMode == ViewMode.LIBRARY) {
            loadLibrary()
        } else {
            adapter.notifyDataSetChanged()
        }
    }

    // --- Double back to exit ---
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            if (doubleBackToExit) {
                super.onBackPressed()
                return
            }
            doubleBackToExit = true
            Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
            android.os.Handler(mainLooper).postDelayed({
                doubleBackToExit = false
            }, 2000)
        }
    }

    // --- Adapter ---
    inner class MangaAdapter(
        private val items: List<MangaItem>,
        private val onItemClick: (MangaItem) -> Unit
    ) : RecyclerView.Adapter<MangaViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_manga_list, parent, false)
            return MangaViewHolder(view)
        }

        override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
            val item = items[position]
            holder.titleTv.text = item.title

            val coverUrl = try {
                // kalau cover kosong, biarkan kosong (Glide akan handle)
                if (item.cover.isBlank()) ""
                else "https://images.weserv.nl/?w=300&q=70&url=" + URLEncoder.encode(item.cover, "UTF-8")
            } catch (e: Exception) {
                item.cover
            }

            Glide.with(holder.coverIv.context)
                .load(if (coverUrl.isBlank()) null else coverUrl)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                )
                .into(holder.coverIv)

            if (isBookmarked(item)) {
                holder.overlay.visibility = View.VISIBLE
                holder.titleTv.alpha = 0.6f
            } else {
                holder.overlay.visibility = View.GONE
                holder.titleTv.alpha = 1.0f
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.itemView.setOnLongClickListener {
                toggleBookmark(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}

class MangaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val coverIv: ImageView = view.findViewById(R.id.cover)
    val titleTv: TextView = view.findViewById(R.id.title)
    val overlay: View = view.findViewById(R.id.dim_overlay)
}