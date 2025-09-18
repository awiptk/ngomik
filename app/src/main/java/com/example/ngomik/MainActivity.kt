package com.example.ngomik

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import android.widget.Toast
import android.widget.SimpleAdapter
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Bitmap
import com.example.ngomik.util.ImageUtils

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

        fetchMangaList()
    }

    private fun fetchMangaList() {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = "https://id.ngomik.cloud"
                val path = "/manga/?order=update"
                val fullUrl = baseUrl + path
                
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
                        listView.adapter = object : SimpleAdapter(this@MainActivity, emptyList<Map<String,String>>().toMutableList(), 0, arrayOf(), intArrayOf()) {
                            override fun getCount(): Int = mangas.size
                            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                                val view = convertView ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_manga, parent, false)
                                val coverIv = view.findViewById<ImageView>(R.id.cover)
                                val titleTv = view.findViewById<TextView>(R.id.title)
                                val typeTv = view.findViewById<TextView>(R.id.type)
                                val item = mangas[position]
                                titleTv.text = item.title
                                typeTv.text = item.type
                                coverIv.setImageDrawable(null)
                                Thread {
                                    try {
                                        val bmp: Bitmap? = if (item.cover.isNotEmpty()) ImageUtils.downloadAndDownsample(item.cover, 240) else null
                                        coverIv.post { coverIv.setImageBitmap(bmp) }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }.start()
                                view.setOnClickListener {
                                    val intent = Intent(this@MainActivity, MangaDetailActivity::class.java)
                                    intent.putExtra("url", item.href)
                                    startActivity(intent)
                                }
                                return view
                            }
                        }
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
}