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

                val cover = doc.selectFirst(".thumb img, .cover img, .post-thumbnail img")?.absUrl("data-src")?.ifEmpty { doc.selectFirst(".thumb img, .cover img, .post-thumbnail img")?.absUrl("src") }?.trim().orEmpty()
                val title = doc.selectFirst("h1.entry-title, .post-title, .judul")?.text()?.trim().orEmpty()
                val author = doc.selectFirst(".author, .post-author, .meta a[rel=author]")?.text()?.trim().orEmpty()
                val genre = doc.selectFirst(".genres, .genre, .post-genre")?.text()?.trim().orEmpty()
                val type = doc.selectFirst(".type, .manga-type, .post-type")?.text()?.trim().orEmpty()
                val description = doc.selectFirst(".description, .sinopsis, .entry-content, .post-content")?.text()?.trim().orEmpty()

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    val coverIv = findViewById<ImageView>(R.id.cover)
                    val titleTv = findViewById<TextView>(R.id.title)
                    val authorTv = findViewById<TextView>(R.id.author)
                    val genreTv = findViewById<TextView>(R.id.genre)
                    val typeTv = findViewById<TextView>(R.id.type)
                    val descTv = findViewById<TextView>(R.id.description)

                    titleTv.text = title
                    authorTv.text = "Author: ${author.ifEmpty { "-" }}"
                    genreTv.text = "Genre: ${genre.ifEmpty { "-" }}"
                    typeTv.text = "Type: ${type.ifEmpty { "-" }}"
                    descTv.text = description

                    Thread {
                        try {
                            val bmp: Bitmap? = if (cover.isNotEmpty()) ImageUtils.downloadAndDownsample(cover, 640) else null
                            coverIv.post { coverIv.setImageBitmap(bmp) }
                        } catch (e: Exception) { e.printStackTrace() }
                    }.start()

                    val chapters = doc.select(".chapter-list a, .chapters a, .list-chapter a").map { it.absUrl("href") to it.text().trim() }
                    val listView = ListView(this@MangaDetailActivity)
                    listView.adapter = android.widget.ArrayAdapter(this@MangaDetailActivity, android.R.layout.simple_list_item_1, chapters.map { it.second })
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
