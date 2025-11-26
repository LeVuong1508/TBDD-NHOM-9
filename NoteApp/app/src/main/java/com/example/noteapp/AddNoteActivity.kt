package com.example.noteapp


import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.model.Note
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AddNoteActivity : AppCompatActivity() {

    private lateinit var edtTitle: EditText
    private lateinit var edtContent: EditText
    private lateinit var btnImportant: ImageButton
    private lateinit var fabSaveNote: FloatingActionButton
    private lateinit var fabMain: FloatingActionButton
    private lateinit var btnBack: ImageButton
    private lateinit var rvAttachments: RecyclerView

    private lateinit var api: ApiService
    private lateinit var token: String

    private val selectedFiles = mutableListOf<Uri>()
    private val serverFiles = mutableListOf<String>()
    private lateinit var attachmentAdapter: AttachmentAdapter
    private lateinit var pickFilesLauncher: ActivityResultLauncher<Intent>
    private lateinit var drawLauncher: ActivityResultLauncher<Intent>

    private var isImportant = false

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        // --- findView ---
        edtTitle = findViewById(R.id.edtTitle)
        edtContent = findViewById(R.id.edtContent)
        btnImportant = findViewById(R.id.btnImportant)
        fabSaveNote = findViewById(R.id.fabSaveNote)
        fabMain = findViewById(R.id.fabMain)
        btnBack = findViewById(R.id.btnBack)
        rvAttachments = findViewById(R.id.rvAttachments)

        api = ApiClient.instance.create(ApiService::class.java)
        token = "Bearer " + (getSharedPreferences("NoteApp", MODE_PRIVATE)
            .getString("token", "") ?: "")

        // --- RecyclerView ---
        attachmentAdapter = AttachmentAdapter(
            localFiles = selectedFiles,
            serverFiles = serverFiles
        ) { position, isLocal ->
            if (isLocal) {
                selectedFiles.removeAt(position)
            } else {
                serverFiles.removeAt(position - selectedFiles.size)
            }
            attachmentAdapter.notifyItemRemoved(position)
            attachmentAdapter.notifyItemRangeChanged(position, selectedFiles.size + serverFiles.size)
        }

        rvAttachments.layoutManager = GridLayoutManager(this, 2)
        rvAttachments.adapter = attachmentAdapter

        // --- Pick files launcher ---
        pickFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uris = mutableListOf<Uri>()
                data?.let {
                    if (it.clipData != null) {
                        for (i in 0 until it.clipData!!.itemCount) {
                            uris.add(it.clipData!!.getItemAt(i).uri)
                        }
                    } else if (it.data != null) {
                        uris.add(it.data!!)
                    }
                }

                val startIndex = selectedFiles.size
                selectedFiles.addAll(uris)
                attachmentAdapter.notifyItemRangeInserted(startIndex, uris.size)

                Toast.makeText(this, "${uris.size} file đã chọn", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Toggle ghim quan trọng ---
        btnImportant.setOnClickListener {
            isImportant = !isImportant
            btnImportant.setImageResource(
                if (isImportant) R.drawable.ic_pin_on else R.drawable.ic_pin_off
            )
        }

        // --- FAB con ---
        val fabFile: FloatingActionButton = findViewById(R.id.fabFile)
        val fabRecord: FloatingActionButton = findViewById(R.id.fabRecord)
        val fabDraw: FloatingActionButton = findViewById(R.id.fabDraw)

        fabMain.setOnClickListener {
            val shouldShow = fabFile.visibility == View.GONE
            fabFile.visibility = if (shouldShow) View.VISIBLE else View.GONE
            fabRecord.visibility = if (shouldShow) View.VISIBLE else View.GONE
            fabDraw.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }

        // --- Draw launcher ---
        drawLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val path = result.data?.getStringExtra("drawing_path")
                path?.let {
                    val originalFile = File(it)
                    val pngFile = File(filesDir, "${System.currentTimeMillis()}.png")
                    originalFile.copyTo(pngFile, overwrite = true)
                    val uri = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.provider",
                        pngFile
                    )
                    selectedFiles.add(uri)
                    attachmentAdapter.notifyItemInserted(selectedFiles.size - 1)
                    Toast.makeText(this, "Đã thêm bản vẽ PNG", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- Click events ---
        fabFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickFilesLauncher.launch(Intent.createChooser(intent, "Chọn file"))
        }

        val requestAudioPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecording()
            } else {
                Toast.makeText(this, "Cần quyền ghi âm để sử dụng tính năng này", Toast.LENGTH_SHORT).show()
            }
        }



        fabRecord.setOnClickListener {
            if (!isRecording) {
                // Chưa ghi thì xin quyền và bắt đầu
                requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                // Đang ghi thì dừng
                stopRecording()
            }
        }


        fabDraw.setOnClickListener {
            val intent = Intent(this, DrawActivity::class.java)
            drawLauncher.launch(intent)
        }

        fabSaveNote.setOnClickListener { addNote() }

        btnBack.setOnClickListener { finish() }
    }

    /** Bắt đầu ghi âm */
    private fun startRecording() {
        try {
            val outputDir = cacheDir
            audioFile = File(outputDir, "record_${System.currentTimeMillis()}.m4a") // đổi đuôi thành .m4a

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            findViewById<FloatingActionButton>(R.id.fabRecord).apply {
                setImageResource(R.drawable.ic_stop)
                setColorFilter(getColor(R.color.red))
            }

            Toast.makeText(this, "Đang ghi âm...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi ghi âm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    /** Dừng ghi âm */
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            findViewById<FloatingActionButton>(R.id.fabRecord).apply {
                setImageResource(R.drawable.ic_mic)
                setColorFilter(getColor(R.color.white))
            }

            audioFile?.let { file ->
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    selectedFiles.add(uri)
                    attachmentAdapter.notifyItemInserted(selectedFiles.size - 1)
                    Toast.makeText(this, "Đã lưu ghi âm: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi dừng ghi âm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // --- Gửi note + file ---
    private fun addNote() {
        val title = edtTitle.text.toString().trim()
        val content = edtContent.text.toString().trim()
        val important = if (isImportant) 1 else 0

        if (title.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề", Toast.LENGTH_SHORT).show()
            return
        }

        val noteReq = ApiService.NoteRequest(title, content, important)
        api.createNote(token, noteReq).enqueue(object : Callback<Note> {
            override fun onResponse(call: Call<Note>, response: Response<Note>) {
                if (response.isSuccessful) {
                    val noteId = response.body()?.id
                    if (noteId != null && selectedFiles.isNotEmpty()) {
                        uploadFiles(noteId)
                    } else {
                        Toast.makeText(this@AddNoteActivity, "Thêm ghi chú thành công", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    Toast.makeText(this@AddNoteActivity, "Thêm ghi chú thất bại", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Note>, t: Throwable) {
                Toast.makeText(this@AddNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun uploadFiles(noteId: Int) {
        val multipartFiles = selectedFiles.map { uri ->
            val file = getFileFromUri(this, uri)
            val mimeType = contentResolver.getType(uri)
                ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.let {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
                } ?: "application/octet-stream"

            MultipartBody.Part.createFormData(
                "files",
                file.name,
                file.asRequestBody(mimeType.toMediaTypeOrNull())
            )
        }

        if (multipartFiles.isEmpty()) return

        api.uploadNoteAttachments(token,noteId, multipartFiles)
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AddNoteActivity, "Upload thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@AddNoteActivity, "Upload thất bại: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    Toast.makeText(this@AddNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
            })
    }

    private fun getFileFromUri(context: Context, uri: Uri): File {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, getFileName(uri))
        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input?.copyTo(output)
            }
        }
        return file
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex("_display_name")
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        if (name == null || !name.contains(".")) {
            val mime = contentResolver.getType(uri)
            val ext = when {
                mime == null -> ""
                mime.startsWith("image/") -> "." + mime.substringAfter("image/")
                mime.startsWith("video/") -> "." + mime.substringAfter("video/")
                mime.startsWith("audio/") -> "." + mime.substringAfter("audio/")
                mime == "application/pdf" -> ".pdf"
                mime == "text/plain" -> ".txt"
                else -> ""
            }
            name = "file_${System.currentTimeMillis()}$ext"
        }
        return name!!
    }
}
