package com.example.noteapp

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.model.Note
import com.example.noteapp.api.ApiService.ShareRequest
import com.example.noteapp.api.ApiService.ShareResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*


class NoteAdapter(
    private val context: Context,
    private var notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private val api = ApiClient.instance.create(ApiService::class.java)
    private val token = context.getSharedPreferences("NoteApp", Context.MODE_PRIVATE)
        .getString("token", null)

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        val txtContent: TextView = itemView.findViewById(R.id.txtContent)
        val txtDates: TextView = itemView.findViewById(R.id.txtDates)

        val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]

        holder.txtTitle.text = if (note.important) "⭐ ${note.title}" else note.title
        holder.txtContent.text = note.content

        // 🕒 Format thời gian: DD-MM-YYYY HH:mm
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

        val created = note.createdAt?.let {
            try { outputFormat.format(inputFormat.parse(it)!!) } catch (_: Exception) { "-" }
        } ?: "-"

        val updated = note.updatedAt?.let {
            try { outputFormat.format(inputFormat.parse(it)!!) } catch (_: Exception) { "-" }
        } ?: "-"



        holder.txtDates.text = "Tạo: $created | Sửa: $updated"

        holder.itemView.setOnClickListener {
            onItemClick(note)
        }



        // 🟢 Khi nhấn nút chia sẻ
        holder.btnShare.setOnClickListener {
            if (token == null) {
                Toast.makeText(context, "Bạn cần đăng nhập trước khi chia sẻ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Hộp thoại nhập email
            val input = EditText(context)
            input.hint = "Nhập email người nhận"

            AlertDialog.Builder(context)
                .setTitle("Chia sẻ ghi chú")
                .setView(input)
                .setPositiveButton("Gửi") { _, _ ->
                    val email = input.text.toString().trim()
                    if (email.isNotEmpty()) {
                        shareNoteWithEmail(note.id, email)
                    } else {
                        Toast.makeText(context, "Vui lòng nhập email", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun shareNoteWithEmail(noteId: Int?, email: String) {
        if (noteId == null) return
        val body = ShareRequest(email)
        api.shareNote("Bearer $token", noteId, body).enqueue(object : Callback<ShareResponse> {
            override fun onResponse(call: Call<ShareResponse>, response: Response<ShareResponse>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, " Đã gửi lời mời chia sẻ! Người nhận cần chấp nhận.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, " Gửi lời mời thất bại", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ShareResponse>, t: Throwable) {
                Toast.makeText(context, "⚠️ Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun getItemCount(): Int = notes.size

    fun updateList(newList: List<Note>) {
        notes = newList
        notifyDataSetChanged()
    }
}
