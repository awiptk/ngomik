package com.example.ngomik

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
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
import androidx.recyclerview.widget.GridLayoutManager
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

data class MangaItem(val title: String, val href: String, val cover: String, val type: String)

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBottom: ProgressBar
    private var mangas: MutableList<MangaItem> = mutableListOf()
    private lateinit var adapter: MangaAdapter
    private var nextPageUrl: String? = null
    private var isLoading = false
    private var currentMode = ViewMode.LIBRARY

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    enum class ViewMode {
        LIBRARY, BROWSE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark mode default
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("manga_prefs", MODE_PRIVATE)

        // Setup toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Ambil menu TextView manual
        val navLibrary: TextView = findViewById(R.id.nav_library)
        val navBrowse: TextView = findViewById(R.id.nav_browse)

        navLibrary.setOnClickListener {
            loadLibrary()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navBrowse.setOnClickListener {
            loadBrowse()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        recyclerView = findViewById(R.id.recyclerView)
        progressBottom = findViewById(R.id.progress_bottom)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = MangaAdapter(mangas)
        recyclerView.adapter = adapter

        loadLibrary()
    }

    private fun loadLibrary() {
        currentMode = ViewMode.LIBRARY
        supportActionBar?.title = "Library"

        val bookmarksJson = prefs.getString("bookmarks", "[]")
        val type = object : TypeToken<List<MangaItem>>() {}.type
        val bookmarks: List<MangaItem> = gson.fromJson(bookmarksJson, type) ?: emptyList()

        mangas.clear()
        mangas.addAll(bookmarks)
        adapter.notifyDataSetChanged()

        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "Library kosong. Browse manga untuk menambahkan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadBrowse() {
        currentMode = ViewMode.BROWSE
        supportActionBar?.title = "Browse"
        nextPageUrl = "https://id.ngomik.cloud/manga/?order=update"
        mangas.clear()
        adapter.notifyDataSetChanged()

        recyclerView.clearOnScrollListeners()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && nextPageUrl != null &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                    firstVisibleItemPosition >= 0) {
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
                val doc = Jsoup.connect(nextPageUrl!!).userAgent("Mozilla/5.0").get()
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
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    inner class MangaAdapter(private val items: List<MangaItem>) :
        RecyclerView.Adapter<MangaViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_manga_grid, parent, false)
            return MangaViewHolder(view)
        }

        override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
            val item = items[position]
            holder.titleTv.text = item.title

            Glide.with(holder.coverIv.context)
                .load(item.cover)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                )
                .into(holder.coverIv)

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, MangaDetailActivity::class.java)
                intent.putExtra("url", item.href)
                startActivity(intent)
            }

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
}