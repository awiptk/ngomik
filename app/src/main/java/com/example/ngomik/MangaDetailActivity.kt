package com.example.ngomik

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import com.example.ngomik.util.ImageUtils
import android.graphics.Bitmap
import android.widget.Toast

class MangaDetailActivity : AppCompatActivity() {
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        progress = findViewById(R.id.progress) ?: ProgressBar(this)

        val url = intent.getStringExtra("url") ?: return
        fetchDetail(url)
    }

    private fun fetchDetail(url: String) {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()

                // Mengambil elemen sesuai HTML yang diberikan
                val cover = doc.selectFirst(".thumb img")?.absUrl("src") ?: ""
                val title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
                val altTitle = doc.selectFirst(".alternative")?.text()?.trim() ?: ""
                
                // Mengambil info dari .tsinfo
                val status = doc.select(".tsinfo .imptdt").find { it.text().contains("Status") }?.selectFirst("i")?.text()?.trim() ?: ""
                val type = doc.select(".tsinfo .imptdt").find { it.text().contains("Type") }?.selectFirst("a")?.text()?.trim() ?: ""
                val released = doc.select(".tsinfo .imptdt").find { it.text().contains("Released") }?.selectFirst("i")?.text()?.trim() ?: ""
                val author = doc.select(".tsinfo .imptdt").find { it.text().contains("Author") }?.selectFirst("i")?.text()?.trim() ?: ""
                val artist = doc.select(".tsinfo .imptdt").find { it.text().contains("Artist") }?.selectFirst("i")?.text()?.trim() ?: ""
                
                // Genre dari .mgen
                val genres = doc.select(".mgen a").map { it.text().trim() }.joinToString(", ")
                
                // Rating
                val rating = doc.selectFirst(".rating .num")?.text()?.trim() ?: ""
                
                // Deskripsi
                val description = doc.selectFirst(".entry-content-single")?.text()?.trim() ?: ""
                
                // Chapter list dari .eplister ul li
                val chapters = doc.select(".eplister ul li").map { li ->
                    val link = li.selectFirst("a")?.absUrl("href") ?: ""
                    val chapterTitle = li.selectFirst(".chapternum")?.text()?.trim() ?: ""
                    val chapterDate = li.selectFirst(".chapterdate")?.text()?.trim() ?: ""
                    Triple(link, chapterTitle, chapterDate)
                }.filter { it.first.isNotEmpty() }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    val coverIv = findViewById<ImageView>(R.id.cover)
                    val titleTv = findViewById<TextView>(R.id.title)
                    val authorTv = findViewById<TextView>(R.id.author)
                    val genreTv = findViewById<TextView>(R.id.genre)
                    val typeTv = findViewById<TextView>(R.id.type)
                    val descTv = findViewById<TextView>(R.id.description)

                    titleTv.text = title
                    authorTv.text = "Author: ${author.ifEmpty { "-" }} ${if (artist.isNotEmpty() && artist != author) "| Artist: $artist" else ""}"
                    genreTv.text = "Genre: ${genres.ifEmpty { "-" }}"
                    typeTv.text = "Type: ${type.ifEmpty { "-" }} | Status: ${status.ifEmpty { "-" }} | Rating: ${rating.ifEmpty { "-" }}"
                    descTv.text = description

                    // Load cover image
                    Thread {
                        try {
                            val bmp: Bitmap? = if (cover.isNotEmpty()) ImageUtils.downloadAndDownsample(cover, 640) else null
                            coverIv.post { coverIv.setImageBitmap(bmp) }
                        } catch (e: Exception) { e.printStackTrace() }
                    }.start()

                    // Setup chapter list
                    val listView = ListView(this@MangaDetailActivity)
                    val chapterTitles = chapters.map { "${it.second} - ${it.third}" }
                    listView.adapter = android.widget.ArrayAdapter(this@MangaDetailActivity, android.R.layout.simple_list_item_1, chapterTitles)
                    listView.setOnItemClickListener { _, _, pos, _ ->
                        val href = chapters[pos].first
                        val intent = Intent(this@MangaDetailActivity, ChapterActivity::class.java)
                        intent.putExtra("url", href)
                        startActivity(intent)
                    }
                    (descTv.parent as View).post {
                        val container = descTv.parent as android.view.ViewGroup
                        if (container.indexOfChild(listView) == -1) {
                            container.addView(listView)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@MangaDetailActivity, "Gagal mengambil detail: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}