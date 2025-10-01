// MainActivity.kt with Glide for caching thumbnails (no reload on scroll), improved layout, black title text, smaller cards, and dark mode support

package com.example.ngomik

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import android.widget.ArrayAdapter
import android.widget.Switch
import android.widget.CompoundButton

data class MangaItem(val title: String, val href: String, val cover: String, val type: String)

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var progress: ProgressBar
    private var mangas: List<MangaItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listView = findViewById(R.id.listView)
        progress = findViewById(R.id.progress)

        // Tambah switch untuk dark mode (asumsi ditambahkan di activity_main.xml)
        val darkModeSwitch: Switch? = findViewById(R.id.dark_mode_switch)
        darkModeSwitch?.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        darkModeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            recreate() // Restart activity untuk apply theme
        }

        fetchMangaList()
    }

    private fun fetchMangaList() {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = "https://id.ngomik.cloud"
                val path = "/manga/"
                val param = "?order=update"
                val fullUrl = baseUrl + path + param

                val doc = Jsoup.connect(fullUrl).userAgent("Mozilla/5.0").get()

                // Selector sesuai dengan struktur HTML yang Anda berikan
                val items = doc.select(".listupd .bs")

                mangas = items.map { el ->
                    val a = el.selectFirst("a")
                    val title = el.selectFirst(".tt")?.text()?.trim() ?: ""
                    val href = a?.absUrl("href") ?: ""
                    val cover = el.selectFirst("img")?.absUrl("src") ?: ""
                    val type = el.selectFirst(".type")?.text()?.trim() ?: ""

                    MangaItem(title, href, cover, type)
                }.filter { it.title.isNotEmpty() && it.href.isNotEmpty() }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (mangas.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Tidak menemukan daftar. Periksa selector.", Toast.LENGTH_LONG).show()
                    } else {
                        listView.adapter = MangaAdapter(this@MainActivity, mangas)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Gagal mengambil daftar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class MangaAdapter(context: android.content.Context, private val items: List<MangaItem>) :
        ArrayAdapter<MangaItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_manga, parent, false)
            val coverIv = view.findViewById<ImageView>(R.id.cover)
            val titleTv = view.findViewById<TextView>(R.id.title)
            val typeTv = view.findViewById<TextView>(R.id.type)
            val item = items[position]
            titleTv.text = item.title
            titleTv.setTextColor(android.graphics.Color.BLACK) // Judul hitam
            typeTv.text = item.type

            // Gunakan Glide untuk caching thumbnail, no reload on scroll
            Glide.with(coverIv.context)
                .load(item.cover)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(120, 180) // Ukuran lebih kecil untuk card lebih kecil
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                )
                .into(coverIv)

            view.setOnClickListener {
                val intent = Intent(this@MainActivity, MangaDetailActivity::class.java)
                intent.putExtra("url", item.href)
                startActivity(intent)
            }
            return view
        }
    }
}