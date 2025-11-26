package com.example.noteapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.databinding.ActivityRegisterBinding
import com.example.noteapp.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val api = ApiClient.instance.create(ApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val fullname = binding.edtFullname.text.toString().trim()
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            if (fullname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showMsg("Vui lòng nhập đầy đủ thông tin")
                return@setOnClickListener
            }

            val passwordPattern = Regex("^(?=.*[0-9])(?=.*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$")
            if (!password.matches(passwordPattern)) {
                showMsg("Mật khẩu phải từ 8 ký tự, chứa ít nhất 1 số và 1 ký tự đặc biệt")
                return@setOnClickListener
            }

            val user = User(fullname = fullname, email = email, password = password)

            api.register(user).enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        val message = body?.get("message")?.toString() ?: "Đăng ký thành công!"

                        showMsg(message)

                        // 👉 Quay về LoginActivity
                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    } else {
                        showMsg("Đăng ký thất bại: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    showMsg("Lỗi kết nối server: ${t.message}")
                }
            })
        }

        binding.txtLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun showMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
