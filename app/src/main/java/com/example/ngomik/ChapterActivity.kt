package com.example.ngomik

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.example.ngomik.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ChapterActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter) // pastikan file xml ada (di bawah aku sebutkan)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)

        recycler.layoutManager = LinearLayoutManager(this)

        val url = intent.getStringExtra("url")
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "URL chapter tidak ditemukan", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fetchChapter(url)
    }

    private fun fetchChapter(url: String) {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()

                // selector berlapis — jika satu tidak temukan, selector lain dicoba
                val imgs = doc.select("div#readerarea img")
                    .mapNotNull { it.absUrl("src").takeIf { s -> s.isNotEmpty() } }
                    .distinct()

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (imgs.isEmpty()) {
                        Toast.makeText(this@ChapterActivity, "Tidak menemukan gambar halaman", Toast.LENGTH_LONG).show()
                        finish()
                        return@withContext
                    }
                    recycler.adapter = ImageAdapter(imgs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@ChapterActivity, "Gagal membuka chapter: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private inner class ImageAdapter(private val urls: List<String>) : RecyclerView.Adapter<ImageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_page_image, parent, false)
            return ImageViewHolder(view.findViewById(R.id.pageImage))
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(urls[position])
        }

        override fun getItemCount(): Int = urls.size
    }

    private inner class ImageViewHolder(private val iv: ImageView) : RecyclerView.ViewHolder(iv) {
        fun bind(url: String) {
            iv.setImageDrawable(null)
            CoroutineScope(Dispatchers.IO).launch {
                val bmp: Bitmap? = try {
                    // coba pakai ImageUtils jika ada
                    try {
                        ImageUtils.downloadAndDownsample(url, 1080)
                    } catch (e: NoSuchMethodError) {
                        downloadBitmap(url)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        downloadBitmap(url)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    downloadBitmap(url)
                }

                withContext(Dispatchers.Main) {
                    if (bmp != null) iv.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()
            val input: InputStream = conn.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}