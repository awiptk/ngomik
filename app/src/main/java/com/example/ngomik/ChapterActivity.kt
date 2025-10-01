package com.example.ngomik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

class ChapterActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: PageAdapter

    private var chapterUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = PageAdapter(emptyList())
        recycler.adapter = adapter

        // Ambil URL dari intent
        chapterUrl = intent.getStringExtra("url") ?: ""
        if (chapterUrl.isEmpty()) {
            Toast.makeText(this, "Chapter URL tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Fetch dan parse chapter
        fetchChapter(chapterUrl)
    }

    private fun fetchChapter(url: String) {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).get()
                val elements = doc.select("#readerarea img")
                val urls = elements.map { "https://images.weserv.nl/?w=300&q=70&url=${it.absUrl("src")}" }

                withContext(Dispatchers.Main) {
                    setPages(urls)
                    progress.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ChapterActivity,
                        "Gagal memuat chapter",
                        Toast.LENGTH_SHORT
                    ).show()
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun setPages(pages: List<String>) {
        adapter = PageAdapter(pages)
        recycler.adapter = adapter
    }

    class PageAdapter(private val pages: List<String>) :
        RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val url = pages[position]
            holder.image.setImageDrawable(null)

            // Lazy loading menggunakan Glide
            Glide.with(holder.image.context)
                .load(url)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate()
                )
                .into(holder.image)
        }

        override fun getItemCount(): Int = pages.size
    }
}