package com.example.ngomik

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("manga_prefs", MODE_PRIVATE)

        val editDomain = findViewById<EditText>(R.id.edit_domain)
        val btnSave = findViewById<Button>(R.id.btn_save_domain)

        val current = prefs.getString("base_domain", "https://id.ngomik.cloud")
        editDomain.setText(current)

        btnSave.setOnClickListener {
            var raw = editDomain.text.toString().trim()
            if (raw.endsWith("/")) raw = raw.removeSuffix("/")
            if (!raw.startsWith("http")) {
                Toast.makeText(this, "Masukkan domain lengkap (https://...)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("base_domain", raw).apply()
            Toast.makeText(this, "Domain disimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}