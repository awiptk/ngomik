package com.example.ngomik

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ngomik.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MangaDetailActivity : AppCompatActivity() {
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        progress = findViewById(R.id.progress)

        val url = intent.getStringExtra("url")
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "URL manga tidak ditemukan", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fetchDetail(url)
    }

    private fun fetchDetail(url: String) {
        progress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()

                val cover = doc.selectFirst(".thumb img")?.absUrl("src") ?: ""
                val title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
                val author = doc.select(".tsinfo .imptdt").find { it.text().contains("Author") }?.selectFirst("i")?.text()?.trim() ?: ""
                val artist = doc.select(".tsinfo .imptdt").find { it.text().contains("Artist") }?.selectFirst("i")?.text()?.trim() ?: ""
                val type = doc.select(".tsinfo .imptdt").find { it.text().contains("Type") }?.selectFirst("a")?.text()?.trim() ?: ""
                val status = doc.select(".tsinfo .imptdt").find { it.text().contains("Status") }?.selectFirst("i")?.text()?.trim() ?: ""
                val genres = doc.select(".mgen a").map { it.text().trim() }.joinToString(", ")
                val rating = doc.selectFirst(".rating .num")?.text()?.trim() ?: ""
                val description = doc.selectFirst(".entry-content-single")?.text()?.trim() ?: ""

                // Ambil chapter list — fallback selector jika struktur berbeda
                val chapterElements = doc.select(".eplister ul li")
                val chapters = chapterElements.map { li ->
                    val link = li.selectFirst("a")?.absUrl("href") ?: ""
                    val chapterTitle = li.selectFirst(".chapternum")?.text()?.trim() ?: li.selectFirst("a")?.text()?.trim() ?: ""
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
                    val chapterContainer = findViewById<LinearLayout>(R.id.chapterCard)

                    titleTv.text = title
                    authorTv.text = "Author: ${author.ifEmpty { "-" }} ${if (artist.isNotEmpty() && artist != author) "| Artist: $artist" else ""}"
                    genreTv.text = "Genre: ${genres.ifEmpty { "-" }}"
                    typeTv.text = "Type: ${type.ifEmpty { "-" }} | Status: ${status.ifEmpty { "-" }} | Rating: ${rating.ifEmpty { "-" }}"
                    descTv.text = description

                    // Load cover : gunakan ImageUtils jika tersedia, kalau tidak fallback
                    if (cover.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val bmp: Bitmap? = try {
                                // gunakan ImageUtils (jika ada)
                                ImageUtils::class.java // akses class utk memastikan import dipakai; (jika ImageUtils tersedia, kode berikut akan dipanggil)
                                try {
                                    // ini mengasumsikan ImageUtils punya fungsi downloadAndDownsample(url, maxDim)
                                    val img = ImageUtils.downloadAndDownsample(cover, 640)
                                    img
                                } catch (e: NoSuchMethodError) {
                                    // fallback jika method tidak ada di ImageUtils
                                    downloadBitmap(cover)
                                } catch (e: Throwable) {
                                    // fallback umum
                                    e.printStackTrace()
                                    downloadBitmap(cover)
                                }
                            } catch (e: Throwable) {
                                // jika ImageUtils tidak ada / error, pakai fallback
                                e.printStackTrace()
                                downloadBitmap(cover)
                            }

                            withContext(Dispatchers.Main) {
                                if (bmp != null) coverIv.setImageBitmap(bmp)
                            }
                        }
                    }

                    // Hapus children chapter (kecuali judul "Daftar Chapter" yang ada di child index 0)
                    if (chapterContainer.childCount > 1) {
                        chapterContainer.removeViews(1, chapterContainer.childCount - 1)
                    }

                    // Tambah chapter sebagai TextView
                    chapters.forEach { (link, chapTitle, chapDate) ->
                        val tv = TextView(this@MangaDetailActivity).apply {
                            text = if (chapDate.isNotEmpty()) "$chapTitle - $chapDate" else chapTitle
                            setPadding(12, 18, 12, 18)
                            setTextColor(Color.parseColor("#cccccc"))
                            textSize = 14f
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            isClickable = true
                            isFocusable = true
                        }
                        tv.setOnClickListener {
                            if (link.isNotEmpty()) {
                                val intent = Intent(this@MangaDetailActivity, ChapterActivity::class.java)
                                intent.putExtra("url", link)
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@MangaDetailActivity, "Link chapter tidak tersedia", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // divider
                        val divider = View(this@MangaDetailActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                            setBackgroundColor(Color.parseColor("#3a3a3a"))
                        }
                        chapterContainer.addView(tv)
                        chapterContainer.addView(divider)
                    }

                    if (chapters.isEmpty()) {
                        val tv = TextView(this@MangaDetailActivity).apply {
                            text = "Tidak ada chapter ditemukan."
                            setPadding(12, 12, 12, 12)
                            setTextColor(Color.parseColor("#cccccc"))
                            textSize = 14f
                        }
                        chapterContainer.addView(tv)
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

    // fallback simple downloader
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