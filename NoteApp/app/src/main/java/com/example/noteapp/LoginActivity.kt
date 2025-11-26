package com.example.noteapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.databinding.ActivityLoginBinding
import com.example.noteapp.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val api = ApiClient.instance.create(ApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Nếu đã lưu đăng nhập trước đó (ghi nhớ) thì vào luôn NoteActivity
        val prefs = getSharedPreferences("NoteApp", MODE_PRIVATE)
        val savedToken = prefs.getString("token", null)
        val rememberLogin = prefs.getBoolean("rememberLogin", false)

        if (!savedToken.isNullOrEmpty() && rememberLogin) {
            startActivity(Intent(this, NoteActivity::class.java))
            finish()
            return
        }

        // 🔹 Hiển thị giao diện đăng nhập
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🟢 Nút Đăng nhập
        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()
            val remember = binding.chkRemember.isChecked

            if (email.isEmpty() || password.isEmpty()) {
                showMsg("Vui lòng nhập email và mật khẩu")
                return@setOnClickListener
            }

            val user = User(email = email, password = password)

            api.login(user).enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                    val body = response.body() ?: return showMsg("Lỗi server")
                    val token = body["token"]?.toString().orEmpty()
                    val message = body["message"]?.toString().orEmpty()

                    if (token.isNotEmpty()) {
                        val editor = getSharedPreferences("NoteApp", MODE_PRIVATE).edit()
                        editor.putString("token", token) // ✅ Luôn lưu token cho phiên hiện tại
                        editor.putBoolean("rememberLogin", remember) // Ghi nhớ hay không thì chỉ lưu cờ
                        editor.apply()


                        showMsg(message.ifEmpty { "Đăng nhập thành công" })

                        // 🔹 Chuyển sang NoteActivity
                        startActivity(Intent(this@LoginActivity, NoteActivity::class.java))
                        finish()
                    } else {
                        showMsg(message.ifEmpty { "Sai tài khoản hoặc mật khẩu" })
                    }
                }

                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    showMsg("Lỗi kết nối: ${t.message.orEmpty()}")
                }
            })
        }

        // 🔵 Chuyển sang màn hình đăng ký
        binding.txtRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun showMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
