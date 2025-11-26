package com.example.noteapp.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.noteapp.R
import com.example.noteapp.api.ApiService
import com.example.noteapp.api.ApiClient
import com.example.noteapp.model.Share
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ShareAdapter(
    private val context: Context,
    private val token: String,
    private val shares: MutableList<Share>,
    private val onAccepted: (Share) -> Unit
) : RecyclerView.Adapter<ShareAdapter.ShareViewHolder>() {

    // ✅ Sửa ở đây
    private val apiService: ApiService = ApiClient.instance.create(ApiService::class.java)

    inner class ShareViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        val tvFrom: TextView = itemView.findViewById(R.id.tvFrom)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShareViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_share, parent, false)
        return ShareViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShareViewHolder, position: Int) {
        val share = shares[position]

        holder.tvTitle.text = share.title ?: "Không có tiêu đề"
        holder.tvFrom.text = "Từ: ${share.fromEmail ?: "Không rõ"}"

        holder.btnAccept.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

            apiService.acceptShare("Bearer $token", share.id ?: 0)
                .enqueue(object : Callback<ApiService.ShareResponse> {
                    override fun onResponse(
                        call: Call<ApiService.ShareResponse>,
                        response: Response<ApiService.ShareResponse>
                    ) {
                        if (response.isSuccessful) {
                            Toast.makeText(context, "✅ Đã chấp nhận chia sẻ", Toast.LENGTH_SHORT).show()
                            onAccepted(share)
                            shares.removeAt(pos)
                            notifyItemRemoved(pos)
                        } else {
                            Toast.makeText(context, "❌ Không thể chấp nhận chia sẻ", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiService.ShareResponse>, t: Throwable) {
                        Toast.makeText(context, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        holder.btnReject.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

            apiService.rejectShare("Bearer $token", share.id ?: 0)
                .enqueue(object : Callback<ApiService.ShareResponse> {
                    override fun onResponse(
                        call: Call<ApiService.ShareResponse>,
                        response: Response<ApiService.ShareResponse>
                    ) {
                        if (response.isSuccessful) {
                            Toast.makeText(context, " Đã từ chối chia sẻ", Toast.LENGTH_SHORT).show()
                            shares.removeAt(pos)
                            notifyItemRemoved(pos)
                        } else {
                            Toast.makeText(context, " Không thể từ chối chia sẻ", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiService.ShareResponse>, t: Throwable) {
                        Toast.makeText(context, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    override fun getItemCount(): Int = shares.size
}
