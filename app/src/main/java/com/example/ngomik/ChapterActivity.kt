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
                
                // Selector sesuai HTML yang diberikan
                val imgs = doc.select("#readerarea img.ts-main-image")
                val service = "https://images.weserv.nl/?w=300&q=70&url="
                
                val pages = imgs.mapNotNull { img ->
                    val imageUrl = img.attr("data-src").ifEmpty { img.absUrl("src") }
                    if (imageUrl.isNotEmpty()) service + imageUrl else null
                }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (pages.isEmpty()) {
                        Toast.makeText(this@ChapterActivity, "Gambar chapter tidak ditemukan.", Toast.LENGTH_LONG).show()
                    } else {
                        recycler.adapter = PagesAdapter(this@ChapterActivity, pages)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(
                        this@ChapterActivity,
                        "Gagal memuat chapter: ${e.message}",
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
        iv.scaleType = ImageView.ScaleType.FIT_WIDTH
        return Holder(iv)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) { 
        val url = pages[position]
        holder.bind(url) 
    }
    override fun getItemCount(): Int = pages.size
    
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val iv = view as ImageView
        fun bind(url: String) {
            iv.setImageDrawable(null)
            Thread {
                try {
                    val bmp = ImageUtils.downloadAndDownsample(url, 1080)
                    iv.post { iv.setImageBitmap(bmp) }
                } catch (e: Exception) { 
                    e.printStackTrace() 
                }
            }.start()
        }
    }
}