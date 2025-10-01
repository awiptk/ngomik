package com.example.ngomik

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val toggle = ActionBarDrawerToggle(
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
            val intent = Intent(this@MainActivity, MangaDetailActivity::class.java)
            intent.putExtra("url", item.href)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Load awal
        loadLibrary()
    }

    // --- Menu (Search + Open Web) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.queryHint = "Search..."
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterList(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText ?: "")
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_web -> {
                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("url", "https://id.ngomik.cloud")
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
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

        mangas.clear()
        visibleMangas.clear()
        adapter.notifyDataSetChanged()

        val baseDomain = prefs.getString("base_domain", "https://id.ngomik.cloud")!!
        nextPageUrl = "$baseDomain/manga/?order=update"

        recyclerView.clearOnScrollListeners()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val layoutManager = rv.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(nextPageUrl).userAgent("Mozilla/5.0").get()
                val items = doc.select(".listupd .bs")

                val newMangas = items.map { el ->
                    val a = el.selectFirst("a")
                    val title = el.selectFirst(".tt")?.text()?.trim() ?: ""
                    val href = a?.absUrl("href") ?: ""
                    val cover = el.selectFirst("img")?.absUrl("src") ?: ""
                    val type = el.selectFirst(".type")?.text()?.trim() ?: ""
                    MangaItem(title, href, cover, type)
                }.filter { it.title.isNotEmpty() && it.href.isNotEmpty() }

                nextPageUrl = doc.selectFirst("a.next.page-numbers")?.absUrl("href")

                withContext(Dispatchers.Main) {
                    val start = mangas.size
                    mangas.addAll(newMangas)
                    visibleMangas.addAll(newMangas)
                    adapter.notifyItemRangeInserted(start, newMangas.size)
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
                val encoded = URLEncoder.encode(item.cover, "UTF-8")
                "https://images.weserv.nl/?w=300&q=70&url=$encoded"
            } catch (e: Exception) {
                item.cover
            }

            Glide.with(holder.coverIv.context)
                .load(coverUrl)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
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