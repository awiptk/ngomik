package com.example.ngomik

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Switch

data class MangaItem(val title: String, val href: String, val cover: String, val type: String)

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progress: ProgressBar
    private var mangas: MutableList<MangaItem> = mutableListOf()
    private lateinit var adapter: MangaAdapter
    private var nextPageUrl: String? = "https://id.ngomik.cloud/manga/?order=update"
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recyclerView = findViewById(R.id.recyclerView)  // Ubah ID dari listView ke recyclerView di XML
        progress = findViewById(R.id.progress)

        // Tambah switch untuk dark mode
        val darkModeSwitch: Switch? = findViewById(R.id.dark_mode_switch)
        darkModeSwitch?.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        darkModeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            recreate()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MangaAdapter(mangas)
        recyclerView.adapter = adapter

        // Tambah infinite scroll listener
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
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
        progress.visibility = View.VISIBLE
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(nextPageUrl).userAgent("Mozilla/5.0").get()

                // Selector sesuai dengan struktur HTML
                val items = doc.select(".listupd .bs")

                val newMangas = items.map { el ->
                    val a = el.selectFirst("a")
                    val title = el.selectFirst(".tt")?.text()?.trim() ?: ""
                    val href = a?.absUrl("href") ?: ""
                    val cover = el.selectFirst("img")?.absUrl("src") ?: ""
                    val type = el.selectFirst(".type")?.text()?.trim() ?: ""

                    MangaItem(title, href, cover, type)
                }.filter { it.title.isNotEmpty() && it.href.isNotEmpty() }

                // Dapatkan next page URL
                nextPageUrl = doc.selectFirst("a.next.page-numbers")?.absUrl("href")

                withContext(Dispatchers.Main) {
                    val start = mangas.size
                    mangas.addAll(newMangas)
                    adapter.notifyItemRangeInserted(start, newMangas.size)
                    progress.visibility = View.GONE
                    isLoading = false
                    if (newMangas.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Tidak ada lagi data.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    isLoading = false
                    Toast.makeText(this@MainActivity, "Gagal mengambil daftar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class MangaAdapter(private val items: List<MangaItem>) :
        RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {

        class MangaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val coverIv: ImageView = view.findViewById(R.id.cover)
            val titleTv: TextView = view.findViewById(R.id.title)
            val typeTv: TextView = view.findViewById(R.id.type)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
            return MangaViewHolder(view)
        }

        override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
            val item = items[position]
            holder.titleTv.text = item.title
            holder.titleTv.setTextColor(android.graphics.Color.BLACK)
            holder.typeTv.text = item.type

            Glide.with(holder.coverIv.context)
                .load(item.cover)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(120, 180)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                )
                .into(holder.coverIv)

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, MangaDetailActivity::class.java)
                intent.putExtra("url", item.href)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}