const express = require("express");
const router = express.Router();
const path = require("path");
const fs = require("fs");
const archiver = require("archiver");
const unzipper = require("unzipper");
const { sql, poolPromise } = require("../db");
const auth = require("../middleware/auth");

// ===============================
// Gửi share
// ===============================
router.post("/:id/share", auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const userId = req.user.id; 
    const { email } = req.body;
    const noteId = parseInt(req.params.id);

    // 🔎 Tìm user theo email
    const u = await pool.request()
      .input("email", sql.VarChar, email)
      .query("SELECT id FROM Users WHERE email=@email");

    if (u.recordset.length === 0) {
      return res.status(404).json({ message: "Không tìm thấy user nhận" });
    }
    const toUserId = u.recordset[0].id;

    // 📂 Lấy file attach của note
    const files = await pool.request()
      .input("noteId", sql.Int, noteId)
      .query("SELECT filePath FROM NoteAttachments WHERE noteId=@noteId");

    // Tạo thư mục shares nếu chưa có
    const zipName = `share_${noteId}_${Date.now()}.zip`;
    const zipPath = path.join(__dirname, "../uploads/shares", zipName);
    fs.mkdirSync(path.dirname(zipPath), { recursive: true });

    // Nén file
    await new Promise((resolve, reject) => {
      const output = fs.createWriteStream(zipPath);
      const archive = archiver("zip", { zlib: { level: 9 } });

      output.on("close", resolve);
      archive.on("error", reject);

      archive.pipe(output);

      if (files.recordset.length > 0) {
        files.recordset.forEach(f => {
          const fullPath = path.join(__dirname, "../", f.filePath);
          if (fs.existsSync(fullPath)) {
            archive.file(fullPath, { name: path.basename(f.filePath) });
          }
        });
      }
      archive.finalize();
    });

    // 📝 Lưu record share
    await pool.request()
      .input("noteId", sql.Int, noteId)
      .input("fromUserId", sql.Int, userId)
      .input("toUserId", sql.Int, toUserId)
      .input("zipPath", sql.NVarChar, `/uploads/shares/${zipName}`)
      .query(`
        INSERT INTO NoteShares(noteId, fromUserId, toUserId, zipPath, status, createdAt)
        VALUES (@noteId, @fromUserId, @toUserId, @zipPath, 'pending', GETDATE())
      `);

    res.json({ message: "✅ Đã gửi yêu cầu share" });
  } catch (err) {
    console.error("❌ Lỗi share:", err);
    res.status(500).json({ message: "Lỗi server khi share" });
  }
});

// ===============================
// Người nhận xem danh sách share
// ===============================
router.get("/", auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const userId = req.user.id;

    const result = await pool.request()
      .input("toUserId", sql.Int, userId)
      .query(`
        SELECT s.*, n.title, u.email as fromEmail
        FROM NoteShares s
        JOIN Notes n ON s.noteId=n.id
        JOIN Users u ON s.fromUserId=u.id
        WHERE s.toUserId=@toUserId AND s.status='pending'
        ORDER BY s.createdAt DESC
      `);

    res.json(result.recordset);
  } catch (err) {
    console.error("❌ Lỗi get shares:", err);
    res.status(500).json({ message: "Lỗi server khi lấy shares" });
  }
});

// ===============================
// Người nhận accept share
// ===============================
router.post("/:id/accept", auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const userId = req.user.id;
    const shareId = parseInt(req.params.id);

    // Kiểm tra share hợp lệ
    const s = await pool.request()
      .input("id", sql.Int, shareId)
      .input("toUserId", sql.Int, userId)
      .query("SELECT * FROM NoteShares WHERE id=@id AND toUserId=@toUserId");

    if (s.recordset.length === 0) {
      return res.status(404).json({ message: "Không tìm thấy yêu cầu share" });
    }

    const share = s.recordset[0];

    // Copy note
    const note = await pool.request()
      .input("noteId", sql.Int, share.noteId)
      .query("SELECT title, content, important FROM Notes WHERE id=@noteId");

    const noteData = note.recordset[0];
    const newNote = await pool.request()
      .input("userId", sql.Int, userId)
      .input("title", sql.NVarChar, noteData.title)
      .input("content", sql.NVarChar, noteData.content)
      .input("important", sql.Bit, noteData.important)
      .query(`
        INSERT INTO Notes(userId, title, content, important, createdAt, updatedAt)
        OUTPUT INSERTED.id
        VALUES(@userId,@title,@content,@important,GETDATE(),GETDATE())
      `);

    const newNoteId = newNote.recordset[0].id;

    // 📦 Giải nén file
    const extractPath = path.join(__dirname, "../uploads/notes", `${newNoteId}`);
    fs.mkdirSync(extractPath, { recursive: true });

    await fs.createReadStream(path.join(__dirname, "../", share.zipPath))
      .pipe(unzipper.Extract({ path: extractPath }))
      .promise();

    // Lưu Attachments mới
    const extractedFiles = fs.readdirSync(extractPath);
    for (const f of extractedFiles) {
      const ext = path.extname(f).toLowerCase();
      let type = "other";
      if ([".png", ".jpg", ".jpeg", ".gif"].includes(ext)) type = "image";
      else if ([".mp4", ".mov"].includes(ext)) type = "video";
      else if ([".mp3", ".wav"].includes(ext)) type = "audio";

      await pool.request()
        .input("noteId", sql.Int, newNoteId)
        .input("filePath", sql.NVarChar, `/uploads/notes/${newNoteId}/${f}`)
        .input("fileType", sql.NVarChar, type)
        .query("INSERT INTO NoteAttachments(noteId, filePath, fileType) VALUES(@noteId,@filePath,@fileType)");
    }

    // Cập nhật trạng thái
    await pool.request()
      .input("id", sql.Int, shareId)
      .query("UPDATE NoteShares SET status='accepted' WHERE id=@id");

    res.json({ message: "✅ Đã nhận ghi chú" });
  } catch (err) {
    console.error("❌ Lỗi accept share:", err);
    res.status(500).json({ message: "Lỗi server khi accept share" });
  }
});

// ===============================
// Người nhận reject share
// ===============================
router.post("/:id/reject", auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const userId = req.user.id;
    const shareId = parseInt(req.params.id);

    await pool.request()
      .input("id", sql.Int, shareId)
      .input("toUserId", sql.Int, userId)
      .query("UPDATE NoteShares SET status='rejected' WHERE id=@id AND toUserId=@toUserId");

    res.json({ message: "🚫 Đã từ chối ghi chú" });
  } catch (err) {
    console.error("❌ Lỗi reject share:", err);
    res.status(500).json({ message: "Lỗi server khi reject share" });
  }
});

router.get("/accepted-all", auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const userId = req.user.id;

    const result = await pool.request()
      .input("toUserId", sql.Int, userId)
      .query(`
        SELECT s.*, n.title, u.email as fromEmail
        FROM NoteShares s
        JOIN Notes n ON s.noteId = n.id
        JOIN Users u ON s.fromUserId = u.id
        WHERE s.toUserId=@toUserId AND s.status='accepted'
        ORDER BY s.createdAt DESC
      `);

    res.json(result.recordset);
  } catch (err) {
    console.error("❌ Lỗi get accepted shares:", err);
    res.status(500).json({ message: "Lỗi server khi lấy shares accepted" });
  }
});

module.exports = router;
