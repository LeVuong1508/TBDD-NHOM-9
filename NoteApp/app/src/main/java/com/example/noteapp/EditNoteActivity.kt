package com.example.noteapp

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.model.Attachment
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
import com.example.noteapp.api.ApiService.DeleteFilesRequest
import com.example.noteapp.api.ApiService.DeleteFilesResponse
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
class EditNoteActivity : AppCompatActivity() {

    private lateinit var edtTitle: EditText
    private lateinit var edtContent: EditText
    private lateinit var btnImportant: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var fabMain: FloatingActionButton
    private lateinit var rvAttachments: RecyclerView

    private lateinit var api: ApiService
    private lateinit var token: String
    private var noteId: Int = -1
    private var isImportant = false

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null

    private val selectedFiles = mutableListOf<Uri>()
    private val serverFiles = mutableListOf<String>()
    private lateinit var attachmentAdapter: AttachmentAdapter

    private lateinit var pickFilesLauncher: ActivityResultLauncher<Intent>
    private lateinit var drawLauncher: ActivityResultLauncher<Intent>
    private lateinit var recordLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_note)

        // --- Khởi tạo UI ---
        edtTitle = findViewById(R.id.edtTitle)
        edtContent = findViewById(R.id.edtContent)
        btnImportant = findViewById(R.id.btnImportant)
        btnBack = findViewById(R.id.btnBack)
        fabMain = findViewById(R.id.fabMain)
        rvAttachments = findViewById(R.id.rvAttachments)
        val fabFile: FloatingActionButton = findViewById(R.id.fabFile)
        val fabRecord: FloatingActionButton = findViewById(R.id.fabRecord)


        val fabDraw: FloatingActionButton = findViewById(R.id.fabDraw)

        api = ApiClient.instance.create(ApiService::class.java)
        token = "Bearer " + (getSharedPreferences("NoteApp", MODE_PRIVATE).getString("token", "") ?: "")

        // --- Lấy note từ Intent ---
        @Suppress("DEPRECATION")
        val note: Note? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("note", Note::class.java)
        } else {
            intent.getSerializableExtra("note") as? Note
        }

        note?.let {
            noteId = it.id
            edtTitle.setText(it.title)
            edtContent.setText(it.content)
            isImportant = it.important
            btnImportant.setImageResource(if (isImportant) R.drawable.ic_pin_on else R.drawable.ic_pin_off)

            // --- Load file đính kèm từ server ---
            val serverBaseUrl = "http://10.44.70.16:5000/"
            loadAttachments(serverBaseUrl)
        }

        attachmentAdapter = AttachmentAdapter(selectedFiles, serverFiles) { pos, isLocal ->
            if (isLocal) {
                if (pos in selectedFiles.indices) {
                    selectedFiles.removeAt(pos)
                    attachmentAdapter.notifyItemRemoved(pos)
                }
            } else {
                val serverIndex = pos - selectedFiles.size
                if (serverIndex in serverFiles.indices) {
                    val fileUrl = serverFiles[serverIndex]
                    val fileName = fileUrl.substringAfterLast('/')

                    AlertDialog.Builder(this)
                        .setTitle("Xóa file khỏi ghi chú")
                        .setMessage("Bạn có chắc muốn xóa file '$fileName' không?")
                        .setPositiveButton("Xóa") { _, _ ->
                            deleteServerAttachment(fileName, serverIndex)
                        }
                        .setNegativeButton("Hủy", null)
                        .show()
                }
            }
        }

        rvAttachments.layoutManager = GridLayoutManager(this, 2)
        rvAttachments.adapter = attachmentAdapter

        // --- Launchers ---
        pickFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uris = mutableListOf<Uri>()
                data?.let {
                    it.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
                    } ?: it.data?.let { uris.add(it) }
                }
                val startIndex = selectedFiles.size
                selectedFiles.addAll(uris)
                attachmentAdapter.notifyItemRangeInserted(startIndex, uris.size)
                Toast.makeText(this, "${uris.size} file đã chọn", Toast.LENGTH_SHORT).show()
            }
        }

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

        recordLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val path = result.data?.getStringExtra("record_path")
                path?.let {
                    val originalFile = File(it)
                    if (originalFile.exists()) {
                        val audioFile = File(filesDir, "record_${System.currentTimeMillis()}.m4a")

                        originalFile.copyTo(audioFile, overwrite = true)

                        // ✅ Lấy Uri từ FileProvider
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            audioFile
                        )
                        selectedFiles.add(uri)
                        attachmentAdapter.notifyItemInserted(selectedFiles.size - 1)

                        Toast.makeText(this, "Đã thêm ghi âm: ${audioFile.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Không tìm thấy file ghi âm!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        // --- FAB chức năng ---
        fabMain.setOnClickListener {
            val show = fabFile.visibility == View.GONE
            val visibility = if (show) View.VISIBLE else View.GONE
            fabFile.visibility = visibility
            fabRecord.visibility = visibility
            fabDraw.visibility = visibility
        }
        fabFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
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
        fabDraw.setOnClickListener { drawLauncher.launch(Intent(this, DrawActivity::class.java)) }
        fabRecord.setOnClickListener {
            if (!isRecording) {
                // Chưa ghi thì xin quyền và bắt đầu
                requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                // Đang ghi thì dừng
                stopRecording()
            }
        }

        // --- Nút back ---
        btnBack.setOnClickListener { saveAndExit() }
        onBackPressedDispatcher.addCallback(this) { saveAndExit() }

        // --- Toggle ghim quan trọng ---
        btnImportant.setOnClickListener {
            isImportant = !isImportant
            btnImportant.setImageResource(if (isImportant) R.drawable.ic_pin_on else R.drawable.ic_pin_off)
        }
    }

    /** Load file đính kèm từ server */
    private fun loadAttachments(serverBaseUrl: String) {
        api.getNoteAttachments(token, noteId).enqueue(object : Callback<List<Attachment>> {
            override fun onResponse(call: Call<List<Attachment>>, response: Response<List<Attachment>>) {
                if (response.isSuccessful) {
                    val attachments = response.body() ?: emptyList()
                    attachments.forEach { att ->
                        val fullUrl = serverBaseUrl + att.filePath.trimStart('/')
                        serverFiles.add(fullUrl)
                    }
                    attachmentAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this@EditNoteActivity, "Lỗi tải file: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Attachment>>, t: Throwable) {
                Toast.makeText(this@EditNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /** Lưu note và upload file */
    private fun saveAndExit() {
        val title = edtTitle.text.toString().trim()
        val content = edtContent.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề", Toast.LENGTH_SHORT).show()
            return
        }

        val noteReq = ApiService.NoteRequest(title, content, if (isImportant) 1 else 0)
        api.updateNote(token, noteId, noteReq).enqueue(object : Callback<Note> {
            override fun onResponse(call: Call<Note>, response: Response<Note>) {
                if (response.isSuccessful) {

                    // ✅ Lọc bỏ file ghi âm (.m4a, .mp3) khỏi danh sách upload
                    val nonAudioFiles = selectedFiles.filterNot { uri ->
                        val path = uri.toString().lowercase()
                        path.endsWith(".m4a") || path.endsWith(".mp3")
                    }

                    if (nonAudioFiles.isNotEmpty()) {
                        uploadFiles(nonAudioFiles)
                    } else {
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    Toast.makeText(this@EditNoteActivity, "Lỗi lưu: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Note>, t: Throwable) {
                Toast.makeText(this@EditNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    /** Upload file (chỉ local) */
    private fun uploadFiles(files: List<Uri>) {
            val multipartFiles = files.map { uri ->
            val file = getFileFromUri(this, uri)
            val mime = contentResolver.getType(uri) ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString())!!) ?: "application/octet-stream"
            MultipartBody.Part.createFormData("files", file.name, file.asRequestBody(mime.toMediaTypeOrNull()))
        }

        api.uploadNoteAttachments( token, noteId,multipartFiles).enqueue(object : Callback<Unit> {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                Toast.makeText(this@EditNoteActivity, if (response.isSuccessful) "Upload thành công" else "Upload thất bại", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK); finish()
            }
            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Toast.makeText(this@EditNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK); finish()
            }
        })
    }

    /** Uri -> File */
    private fun getFileFromUri(context: Context, uri: Uri): File {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, getFileName(uri))
        inputStream.use { input -> FileOutputStream(file).use { output -> input?.copyTo(output) } }
        return file
    }

    /** Lấy tên file */
    private fun getFileName(uri: Uri): String {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex("_display_name")
            if (cursor.moveToFirst() && idx != -1) name = cursor.getString(idx)
        }
        return name ?: "file_${System.currentTimeMillis()}"
    }

    /** Gọi API xóa file khỏi server */
    private fun deleteServerAttachment(fileName: String, serverIndex: Int) {
        val body = DeleteFilesRequest(listOf(fileName))

        api.deleteNoteAttachments(token, noteId, body)
            .enqueue(object : Callback<DeleteFilesResponse> {
                override fun onResponse(
                    call: Call<DeleteFilesResponse>,
                    response: Response<DeleteFilesResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        serverFiles.removeAt(serverIndex)
                        attachmentAdapter.notifyItemRemoved(selectedFiles.size + serverIndex)
                        Toast.makeText(
                            this@EditNoteActivity,
                            "Đã xóa ${response.body()?.deleted ?: 0} file",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@EditNoteActivity,
                            "Lỗi khi xóa: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                override fun onFailure(call: Call<DeleteFilesResponse>, t: Throwable) {
                    Toast.makeText(this@EditNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
    /** Bắt đầu ghi âm */
    private fun startRecording() {
        try {
            val outputDir = cacheDir
            audioFile = File(outputDir, "record_${System.currentTimeMillis()}.m4a")

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
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
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



    /** Dừng ghi âm và xử lý file */
    private fun stopRecording() {
        try {
            // Ngừng ghi âm an toàn
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@EditNoteActivity, "Không thể dừng ghi âm: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Cập nhật icon UI
            findViewById<FloatingActionButton>(R.id.fabRecord).apply {
                setImageResource(R.drawable.ic_mic)
                setColorFilter(getColor(R.color.white))
            }

            // Kiểm tra file ghi âm
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    // ✅ Thêm file vào danh sách đính kèm (để hiển thị)
                    val playUri = FileProvider.getUriForFile(
                        this@EditNoteActivity,
                        "${applicationContext.packageName}.provider",
                        file
                    )
                    selectedFiles.add(playUri)
                    attachmentAdapter.notifyItemInserted(selectedFiles.size - 1)

                    Toast.makeText(this, "Đã lưu ghi âm: ${file.name}", Toast.LENGTH_SHORT).show()

                    // ✅ Tạo bản copy đảm bảo FFmpeg đọc được
                    val uploadFile = File(cacheDir, "upload_${System.currentTimeMillis()}.m4a")
                    try {
                        file.inputStream().use { input ->
                            uploadFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Không thể sao chép file: ${e.message}", Toast.LENGTH_SHORT).show()
                        return
                    }


                    uploadAudioFile(uploadFile)

                } else {
                    Toast.makeText(this, "Tệp ghi âm chưa sẵn sàng.", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi dừng ghi âm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAudioFile(file: File) {
        val mime = "audio/mp4"
        val body = MultipartBody.Part.createFormData(
            "files", file.name, file.asRequestBody(mime.toMediaTypeOrNull())
        )

        api.uploadNoteAttachments(token, noteId, listOf(body)).enqueue(object : Callback<Unit> {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@EditNoteActivity, "Upload thành công", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@EditNoteActivity, "Upload thất bại: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Toast.makeText(this@EditNoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }




}
