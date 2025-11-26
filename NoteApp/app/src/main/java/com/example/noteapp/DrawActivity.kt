package com.example.noteapp

import android.app.Activity
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.noteapp.view.DrawingView
import java.io.File
import android.graphics.Bitmap

class DrawActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var btnSaveDrawing: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_draw)

        drawingView = findViewById(R.id.drawingView)
        btnSaveDrawing = findViewById(R.id.btnSaveDrawing)

        btnSaveDrawing.setOnClickListener {
            saveDrawingAndReturn()
        }
    }

    private fun saveDrawingAndReturn() {
        try {
            // Lấy bitmap hiện tại từ DrawingView
            val bitmap = drawingView.getBitmap()

            // Tạo file tạm trong internal storage
            val file = File(filesDir, "drawing_${System.currentTimeMillis()}.png")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Trả về đường dẫn file cho AddNoteActivity
            val intent = intent.apply {
                putExtra("drawing_path", file.absolutePath)
            }
            setResult(Activity.RESULT_OK, intent)
            Toast.makeText(this, "Đã lưu bản vẽ", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi lưu bản vẽ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
