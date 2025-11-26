package com.example.noteapp.api

import com.example.noteapp.model.Note
import com.example.noteapp.model.User
import com.example.noteapp.model.Attachment
import com.example.noteapp.model.Share
import retrofit2.Call
import retrofit2.http.*
import okhttp3.MultipartBody
import retrofit2.Response


interface ApiService {

    data class NoteRequest(
        val title: String,
        val content: String,
        val important: Int
    )
    /* ===== AUTH ===== */
    @Headers("Content-Type: application/json")
    @POST("api/auth/register")
    fun register(@Body user: User): Call<Map<String, Any>>

    @Headers("Content-Type: application/json")
    @POST("api/auth/login")
    fun login(@Body user: User): Call<Map<String, Any>>


    /* ===== NOTES ===== */

    // Lấy tất cả ghi chú của user
    @GET("api/notes")
    fun getNotes(@Header("Authorization") token: String): Call<List<Note>>

    @GET("notes/all")
    fun getNotesAll(
        @Header("Authorization") token: String
    ): Call<List<Note>>

    // Thêm ghi chú (Node.js: POST /api/notes/add)
    @POST("api/notes/add")
    fun createNote(
        @Header("Authorization") token: String,
        @Body note: NoteRequest
    ): Call<Note>

    @PUT("api/notes/edit/{id}")
    fun updateNote(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body note: NoteRequest
    ): Call<Note>

    // Xóa ghi chú
    @DELETE("api/notes/delete/{id}")
    fun deleteNote(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Call<Void>

    // Đánh dấu quan trọng
    @PUT("api/notes/important/{id}")
    fun toggleImportant(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Call<Note>

    // Tìm kiếm ghi chú
    @GET("api/notes/search")
    fun searchNotes(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Call<List<Note>>


    // =================== Upload attachments ===================
    @Multipart
    @POST("api/notes/upload/{noteId}/multiple")
    fun uploadNoteAttachments(
        @Header("Authorization") token: String,
        @Path("noteId") noteId: Int,
        @Part files: List<MultipartBody.Part>
    ): Call<Unit>

    data class DeleteFilesRequest(
        val files: List<String>  // danh sách tên file muốn xóa
    )

    data class DeleteFilesResponse(
        val success: Boolean,
        val message: String,
        val deleted: Int
    )

    @Headers("Content-Type: application/json")
    @POST("api/notes/{noteId}/delete-files")
    fun deleteNoteAttachments(
        @Header("Authorization") token: String,
        @Path("noteId") noteId: Int,
        @Body body: DeleteFilesRequest
    ): Call<DeleteFilesResponse>

    // =================== Lấy danh sách attachments ===================
    @GET("api/notes/{noteId}/attachments")
    fun getNoteAttachments(
        @Header("Authorization") token: String,
        @Path("noteId") noteId: Int
    ): Call<List<Attachment>>

    // ===== SHARE NOTES =====

    data class ShareRequest(val email: String)
    data class ShareResponse(val message: String)

    // Gửi share note
    @POST("api/shares/{id}/share")
    fun shareNote(
        @Header("Authorization") token: String,
        @Path("id") noteId: Int,
        @Body body: ShareRequest
    ): Call<ShareResponse>

    // Lấy danh sách share pending
    @GET("api/shares")
    fun getPendingShares(
        @Header("Authorization") token: String
    ): Call<List<Share>>



    // Accept share
    @POST("api/shares/{id}/accept")
    fun acceptShare(
        @Header("Authorization") token: String,
        @Path("id") shareId: Int
    ): Call<ShareResponse>

    // Reject share
    @POST("api/shares/{id}/reject")
    fun rejectShare(
        @Header("Authorization") token: String,
        @Path("id") shareId: Int
    ): Call<ShareResponse>

}
