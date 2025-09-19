package com.example.ngomik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ngomik.util.ImageUtils

class ChapterActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: PageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = PageAdapter(emptyList()) // awalnya kosong
        recycler.adapter = adapter

        // TODO: panggil fungsi parsing chapter di sini lalu isi adapter dengan setPages()
    }

    fun setPages(pages: List<String>) {
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

            Thread {
                try {
                    val bmp = ImageUtils.downloadAndDownsample(url, 1080)
                    holder.image.post { holder.image.setImageBitmap(bmp) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        override fun getItemCount(): Int = pages.size
    }
}