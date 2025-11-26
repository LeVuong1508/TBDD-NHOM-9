package com.example.noteapp

import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noteapp.api.ApiClient
import com.example.noteapp.api.ApiService
import com.example.noteapp.model.Note
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

class NoteActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var api: ApiService
    private lateinit var adapter: NoteAdapter
    private var notes: MutableList<Note> = mutableListOf()
    private lateinit var token: String
    private lateinit var fabMain: FloatingActionButton



    private val addNoteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadNotes()
    }

    private val editNoteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadNotes()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        recyclerView = findViewById(R.id.recyclerView)
        edtSearch = findViewById(R.id.edtSearch)
        fabMain = findViewById(R.id.fabMain)

        api = ApiClient.instance.create(ApiService::class.java)
        token = "Bearer " + (getSharedPreferences("NoteApp", MODE_PRIVATE)
            .getString("token", "") ?: "")

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NoteAdapter(this, notes) { note ->
            val intent = Intent(this, EditNoteActivity::class.java)
            intent.putExtra("note", note)
            editNoteLauncher.launch(intent)
        }
        recyclerView.adapter = adapter

        loadNotes()
        setupSwipeToDelete()

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isEmpty()) loadNotes() else searchNotes(query)
            }
        })

        fabMain.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            addNoteLauncher.launch(intent)
        }
        val fabShare = findViewById<FloatingActionButton>(R.id.fabShare)

        fabShare.setOnClickListener {
            val intent = Intent(this, ShareActivity::class.java)
            startActivity(intent)
        }

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            val prefs = getSharedPreferences("NoteApp", MODE_PRIVATE)
            prefs.edit().remove("token").commit()

            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, LoginActivity::class.java)
            // Xóa toàn bộ Activity stack để không back lại NoteActivity được
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }


    private fun loadNotes() {
        api.getNotes(token).enqueue(object : Callback<List<Note>> {
            override fun onResponse(call: Call<List<Note>>, response: Response<List<Note>>) {
                if (response.isSuccessful && response.body() != null) {
                    notes = response.body()!!.toMutableList()
                    adapter.updateList(notes)
                } else {
                    Toast.makeText(this@NoteActivity, "Không thể tải ghi chú", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Note>>, t: Throwable) {
                Toast.makeText(this@NoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun searchNotes(query: String) {
        api.searchNotes(token, query).enqueue(object : Callback<List<Note>> {
            override fun onResponse(call: Call<List<Note>>, response: Response<List<Note>>) {
                if (response.isSuccessful && response.body() != null) {
                    adapter.updateList(response.body()!!)
                }
            }

            override fun onFailure(call: Call<List<Note>>, t: Throwable) {
                Toast.makeText(this@NoteActivity, "Lỗi tìm kiếm", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupSwipeToDelete() {
        val deleteIcon: Drawable? = ContextCompat.getDrawable(this, R.drawable.ic_delete)
        val background = ColorDrawable(Color.RED)
        val itemTouchHelper = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = notes[position]
                AlertDialog.Builder(this@NoteActivity)
                    .setTitle("Xóa ghi chú")
                    .setMessage("Bạn có chắc muốn xóa ghi chú này không?")
                    .setPositiveButton("Xóa") { _, _ ->
                        deleteNote(note.id)
                    }
                    .setNegativeButton("Hủy") { dialog, _ ->
                        dialog.dismiss()
                        adapter.notifyItemChanged(position)
                    }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2

                if (dX > 0) {
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    deleteIcon?.setBounds(
                        itemView.left + iconMargin, itemView.top + iconMargin,
                        itemView.left + iconMargin + (deleteIcon?.intrinsicWidth ?: 0),
                        itemView.bottom - iconMargin
                    )
                } else if (dX < 0) {
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    deleteIcon?.setBounds(
                        itemView.right - iconMargin - (deleteIcon?.intrinsicWidth ?: 0),
                        itemView.top + iconMargin,
                        itemView.right - iconMargin,
                        itemView.bottom - iconMargin
                    )
                } else {
                    background.setBounds(0, 0, 0, 0)
                }

                background.draw(c)
                deleteIcon?.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun deleteNote(id: Int) {
        api.deleteNote(token, id).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@NoteActivity, "Đã xóa ghi chú", Toast.LENGTH_SHORT).show()
                    loadNotes()
                } else {
                    Toast.makeText(this@NoteActivity, "Xóa thất bại", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@NoteActivity, "Lỗi mạng: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

}
