package com.example.noteapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.noteapp.adapter.ShareAdapter
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.databinding.ActivityShareBinding
import com.example.noteapp.model.Share
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    private lateinit var adapter: ShareAdapter
    private val shares = mutableListOf<Share>()

    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy token
        token = intent.getStringExtra("token")
        if (token.isNullOrEmpty()) {
            token = getSharedPreferences("NoteApp", MODE_PRIVATE)
                .getString("token", null)
        }

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Không tìm thấy token đăng nhập", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // RecyclerView setup
        binding.rvShares.layoutManager = LinearLayoutManager(this)
        adapter = ShareAdapter(this, token!!, shares) { share ->
            val intent = Intent(this, NoteActivity::class.java)
            intent.putExtra("noteId", share.noteId)
            startActivity(intent)
        }
        binding.rvShares.adapter = adapter

        // Nút quay lại
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, NoteActivity::class.java)
            startActivity(intent)
            finish()
        }

        loadShares()
    }

    private fun loadShares() {
        val api = ApiClient.instance.create(ApiService::class.java)
        api.getPendingShares("Bearer $token").enqueue(object : Callback<List<Share>> {
            override fun onResponse(call: Call<List<Share>>, response: Response<List<Share>>) {
                if (response.isSuccessful) {
                    shares.clear()
                    response.body()?.let { shares.addAll(it) }
                    adapter.notifyDataSetChanged()

                    if (shares.isEmpty()) {
                        Toast.makeText(this@ShareActivity, "Không có ghi chú chia sẻ", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ShareActivity, "Lỗi lấy danh sách chia sẻ", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Share>>, t: Throwable) {
                Toast.makeText(this@ShareActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
