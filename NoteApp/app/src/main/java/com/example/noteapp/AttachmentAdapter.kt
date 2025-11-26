package com.example.noteapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.media.MediaPlayer
import java.util.concurrent.TimeUnit

class AttachmentAdapter(
    private val localFiles: MutableList<Uri>,
    private val serverFiles: MutableList<String>,
    private val onRemove: (position: Int, isLocal: Boolean) -> Unit
) : RecyclerView.Adapter<AttachmentAdapter.ViewHolder>() {

    private var currentPlayer: MediaPlayer? = null
    private var currentHolder: ViewHolder? = null
    private val handler = Handler(Looper.getMainLooper())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewImage: ImageView = view.findViewById(R.id.previewImage)
        val previewVideoIcon: ImageView = view.findViewById(R.id.previewVideoIcon)
        val txtFileName: TextView = view.findViewById(R.id.txtFileName)
        val btnPlayAudio: ImageButton = view.findViewById(R.id.btnPlayAudio)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
        val audioLayout: LinearLayout = view.findViewById(R.id.audioLayout)
        val audioSeekBar: SeekBar = view.findViewById(R.id.audioSeekBar)
        val txtAudioTime: TextView = view.findViewById(R.id.txtAudioTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.activity_attachment_adapter, parent, false)
        )

    override fun getItemCount() = localFiles.size + serverFiles.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.itemView.context
        val isLocal = position < localFiles.size
        val item = if (isLocal) localFiles[position] else serverFiles[position - localFiles.size]

        // Ẩn tất cả trước
        holder.previewImage.visibility = View.GONE
        holder.previewVideoIcon.visibility = View.GONE
        holder.audioLayout.visibility = View.GONE
        holder.txtFileName.visibility = View.GONE

        // Hiển thị tên file
        val fileName = when (item) {
            is Uri -> item.lastPathSegment ?: "file"
            is String -> item.substringAfterLast("/")
            else -> "file"
        }
        holder.txtFileName.text = fileName
        holder.txtFileName.visibility = View.VISIBLE

        // Xác định loại file
        val mimeType = when (item) {
            is Uri -> context.contentResolver.getType(item) ?: "*/*"
            is String -> when {
                item.endsWith(".png", true) || item.endsWith(".jpg", true) -> "image/*"
                item.endsWith(".mp4", true) -> "video/*"
                item.endsWith(".mp3", true) || item.endsWith(".m4a", true) -> "audio/*"
                else -> "*/*"
            }
            else -> "*/*"
        }

        when {
            mimeType.startsWith("image/") -> {
                holder.previewImage.visibility = View.VISIBLE
                Glide.with(context).load(item).centerCrop().into(holder.previewImage)
                holder.previewImage.setOnClickListener { openFile(context, item, "image/*") }
            }

            mimeType.startsWith("video/") -> {
                holder.previewImage.visibility = View.VISIBLE
                holder.previewVideoIcon.visibility = View.VISIBLE
                Glide.with(context).load(item).centerCrop().into(holder.previewImage)
                holder.previewImage.setOnClickListener { openFile(context, item, "video/*") }
            }

            mimeType.startsWith("audio/") -> {
                // Ẩn khung vuông hình ảnh của preview
                holder.itemView.findViewById<View>(R.id.previewFrame).visibility = View.GONE

                // Hiện layout audio
                holder.audioLayout.visibility = View.VISIBLE
                holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)
                holder.audioSeekBar.progress = 0
                holder.txtAudioTime.text = "00:00 / 00:00"

                holder.btnPlayAudio.setOnClickListener {
                    playOrPauseAudio(context, item, holder)
                }
            }


            else -> {
                holder.txtFileName.setOnClickListener { openFile(context, item, "*/*") }
            }
        }

        holder.btnRemove.setOnClickListener { onRemove(position, isLocal) }
    }

    /** ====================== PHÁT ÂM THANH ======================= **/
    private fun playOrPauseAudio(context: Context, item: Any, holder: ViewHolder) {
        try {
            // Nếu đang phát cùng file -> toggle pause/resume
            if (currentHolder == holder && currentPlayer != null) {
                if (currentPlayer!!.isPlaying) {
                    currentPlayer!!.pause()
                    holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    currentPlayer!!.start()
                    holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause)
                    updateSeekBar(holder)
                }
                return
            }

            // Nếu đang phát file khác -> dừng
            currentPlayer?.release()
            currentPlayer = null
            currentHolder?.btnPlayAudio?.setImageResource(android.R.drawable.ic_media_play)

            val mediaPlayer = MediaPlayer()
            currentPlayer = mediaPlayer
            currentHolder = holder

            if (item is Uri) {
                try {
                    val fd = context.contentResolver.openFileDescriptor(item, "r")?.fileDescriptor
                    if (fd != null) {
                        mediaPlayer.setDataSource(fd)
                    } else {
                        Toast.makeText(context, "Không thể mở tệp âm thanh", Toast.LENGTH_SHORT).show()
                        return
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi mở file âm thanh: ${e.message}", Toast.LENGTH_SHORT).show()
                    return
                }
            } else if (item is String) {
                // 🔗 File trên server (ví dụ: https://myserver.com/uploads/audio123.mp3)
                mediaPlayer.setDataSource(item)
            } else {
                Toast.makeText(context, "Tệp âm thanh không hợp lệ", Toast.LENGTH_SHORT).show()
                return
            }


            mediaPlayer.setOnPreparedListener {
                holder.audioSeekBar.max = it.duration
                holder.txtAudioTime.text = "00:00 / ${formatTime(it.duration)}"
                it.start()
                holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause)
                updateSeekBar(holder)
            }

            mediaPlayer.setOnCompletionListener {
                holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)
                holder.audioSeekBar.progress = 0
                holder.txtAudioTime.text = "00:00 / ${formatTime(it.duration)}"
                it.release()
                currentPlayer = null
                currentHolder = null
            }

            mediaPlayer.prepareAsync()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Không thể phát âm thanh", Toast.LENGTH_SHORT).show()
        }
    }

    /** Cập nhật SeekBar và thời gian */
    private fun updateSeekBar(holder: ViewHolder) {
        val player = currentPlayer ?: return

        handler.post(object : Runnable {
            override fun run() {
                try {
                    if (player.isPlaying) {
                        holder.audioSeekBar.progress = player.currentPosition
                        holder.txtAudioTime.text =
                            "${formatTime(player.currentPosition)} / ${formatTime(player.duration)}"
                        handler.postDelayed(this, 500)
                    }
                } catch (e: IllegalStateException) {
                    // MediaPlayer đã bị release → dừng cập nhật
                    handler.removeCallbacks(this)
                }
            }
        })
    }

    /** Format thời gian (ms -> mm:ss) */
    private fun formatTime(ms: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /** Mở file (ảnh/video) */
    private fun openFile(context: Context, item: Any, type: String) {
        try {
            val uri = when (item) {
                is Uri -> item
                is String -> Uri.parse(item)
                else -> null
            } ?: return

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Không thể mở tệp này", Toast.LENGTH_SHORT).show()
        }
    }
}
