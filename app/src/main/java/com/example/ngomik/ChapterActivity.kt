package com.example.ngomik

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.Executors

class ChapterActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progressBottom: ProgressBar
    private lateinit var adapter: PageAdapter

    private var chapterUrl: String = ""
    private var nextChapterUrl: String? = null
    private var isLoading = false
    private val loadedChapters = mutableSetOf<String>() // Track loaded chapters

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter_updated)

        recycler = findViewById(R.id.recycler)
        progressBottom = findViewById(R.id.progress_bottom)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = PageAdapter(mutableListOf())
        recycler.adapter = adapter

        // Tambah infinite scroll listener
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && nextChapterUrl != null &&
                    !loadedChapters.contains(nextChapterUrl) &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                    firstVisibleItemPosition >= 0) {
                    loadNextChapter()
                }
            }
        })

        chapterUrl = intent.getStringExtra("url") ?: ""
        if (chapterUrl.isEmpty()) {
            Toast.makeText(this, "Chapter URL tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchChapter(chapterUrl)
    }

    private fun fetchChapter(url: String) {
        if (loadedChapters.contains(url)) return
        
        progressBottom.visibility = View.VISIBLE
        isLoading = true
        loadedChapters.add(url)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).get()
                val elements = doc.select("#readerarea img")
                val urls = elements.map { "https://images.weserv.nl/?w=800&q=85&url=${it.absUrl("src")}" }

                // Dapatkan next chapter URL
                nextChapterUrl = doc.selectFirst("a.ch-next-btn")?.absUrl("href")

                withContext(Dispatchers.Main) {
                    adapter.addPages(urls)
                    progressBottom.visibility = View.GONE
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ChapterActivity,
                        "Gagal memuat chapter",
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBottom.visibility = View.GONE
                    isLoading = false
                    loadedChapters.remove(url) // Remove dari set jika gagal
                }
            }
        }
    }

    private fun loadNextChapter() {
        nextChapterUrl?.let { url ->
            fetchChapter(url)
        }
    }

    class PageAdapter(private val pages: MutableList<String>) :
        RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

        private val executor = Executors.newSingleThreadExecutor()

        fun addPages(newPages: List<String>) {
            val start = pages.size
            pages.addAll(newPages)
            notifyItemRangeInserted(start, newPages.size)
        }

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.image)
            val progressBar: ProgressBar = view.findViewById(R.id.progress_image)

            private var scaleFactor = 1f
            private val maxScale = 5f
            private val minScale = 1f
            private var pivotX = 0f
            private var pivotY = 0f

            private val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (scaleFactor > minScale) {
                        // Reset zoom
                        scaleFactor = minScale
                    } else {
                        // Zoom in di posisi tap
                        scaleFactor = 2.5f
                        pivotX = e.x
                        pivotY = e.y
                        image.pivotX = pivotX
                        image.pivotY = pivotY
                    }
                    image.scaleX = scaleFactor
                    image.scaleY = scaleFactor
                    return true
                }
            })

            private val scaleGestureDetector = android.view.ScaleGestureDetector(itemView.context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    scaleFactor *= detector.scaleFactor
                    scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
                    
                    // Set pivot di posisi pinch
                    image.pivotX = detector.focusX
                    image.pivotY = detector.focusY
                    
                    image.scaleX = scaleFactor
                    image.scaleY = scaleFactor
                    return true
                }
            })

            init {
                image.setOnTouchListener { v, event ->
                    scaleGestureDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page_updated, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val url = pages[position]
            holder.image.setImageDrawable(null)
            holder.progressBar.visibility = View.VISIBLE
            holder.image.scaleX = 1f
            holder.image.scaleY = 1f

            executor.execute {
                Glide.with(holder.image.context)
                    .load(url)
                    .apply(
                        RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .dontAnimate()
                            .override(Target.SIZE_ORIGINAL)
                    )
                    .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?) {
                            holder.image.post {
                                holder.image.setImageDrawable(resource)
                                holder.progressBar.visibility = View.GONE
                            }
                        }

                        override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                            holder.progressBar.visibility = View.GONE
                        }

                        override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                            holder.progressBar.visibility = View.GONE
                        }
                    })
            }
        }

        override fun getItemCount(): Int = pages.size
    }
}