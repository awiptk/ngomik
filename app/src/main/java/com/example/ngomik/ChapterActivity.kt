package com.example.ngomik

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import com.example.ngomik.util.ImageUtils

class ChapterActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        recycler.layoutManager = LinearLayoutManager(this)

        val url = intent.getStringExtra("url") ?: return
        parseChapter(url)
    }

    private fun parseChapter(url: String) {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()
                val imgs = doc.select("div#chapter_body img, .reader-area img, .chapter-content img, .page img")
                val prefs = getSharedPreferences("ngomik_prefs", Context.MODE_PRIVATE)
                val service = prefs.getString("resize_service_url", "") ?: ""
                val pages = imgs.map { img ->
                    val src = img.absUrl("data-src").ifEmpty { img.absUrl("src") }.trim()
                    if (service.isNotEmpty()) service + src else src
                }.filter { it.isNotEmpty() }
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (pages.isEmpty()) {
                        Toast.makeText(this@ChapterActivity, "Gambar chapter tidak ditemukan atau selector salah.", Toast.LENGTH_LONG).show()
                    }
                    recycler.adapter = PagesAdapter(this@ChapterActivity, pages)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(
                        this@ChapterActivity,
                        "Gagal memuat chapter: ${e.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

class PagesAdapter(private val ctx: Context, private val pages: List<String>) : RecyclerView.Adapter<PagesAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val iv = ImageView(parent.context)
        iv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        iv.adjustViewBounds = true
        return Holder(iv)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) { val url = pages[position]; holder.bind(url) }
    override fun getItemCount(): Int = pages.size
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val iv = view as ImageView
        fun bind(url: String) {
            iv.setImageDrawable(null)
            Thread {
                try {
                    val bmp = ImageUtils.downloadAndDownsample(url, iv.width.takeIf { it>0 } ?: 1080)
                    iv.post { iv.setImageBitmap(bmp) }
                } catch (e: Exception) { e.printStackTrace() }
            }.start()
        }
    }
}