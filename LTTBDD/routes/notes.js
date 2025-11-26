const express = require('express');
const router = express.Router();
const sql = require('mssql');
const auth = require('../middleware/auth');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
require('dotenv').config();
const ffmpeg = require('fluent-ffmpeg');
ffmpeg.setFfmpegPath("D:/TBDD/LTTBDD/libs/ffmpeg-2025-10-09-git-469aad3897-essentials_build/bin/ffmpeg.exe");



/* ========== SQL CONFIG ========== */
const dbConfig = {
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  server: process.env.DB_SERVER,

  database: process.env.DB_NAME,
  options: { trustServerCertificate: true }
};

// Pool Promise
const poolPromise = new sql.ConnectionPool(dbConfig)
  .connect()
  .then(pool => {
    console.log('Connected to SQL Server');
    return pool;
  })
  .catch(err => console.log('Database connection failed:', err));

/* ========== MULTER CONFIG ========== */
const uploadDir = path.join(__dirname, '../uploads/notes');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}


const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadPath = 'uploads/notes';
    if (!fs.existsSync(uploadPath)) fs.mkdirSync(uploadPath, { recursive: true });
    cb(null, uploadPath);
  },
  filename: (req, file, cb) => {
    // Lấy đuôi gốc
    let ext = path.extname(file.originalname);

   // Nếu không có đuôi thì thử đoán theo mimetype
if (!ext || ext === '') {
  const mime = file.mimetype;
    if (mime.startsWith('image/')) ext = '.' + mime.split('/')[1];
    else if (mime.startsWith('video/')) ext = '.' + mime.split('/')[1];
    else if (mime.startsWith('audio/')) {
    if (mime === 'audio/mp4' || mime === 'audio/m4a') ext = '.m4a';
    else ext = '.' + mime.split('/')[1];
    }
    else if (mime === 'application/pdf') ext = '.pdf';
    else if (mime === 'text/plain') ext = '.txt';
    else ext = '';
}
    
    const baseName = path.basename(file.originalname, path.extname(file.originalname));
    const uniqueName = `${Date.now()}-${baseName}${ext}`;

    cb(null, uniqueName);
  }
});

const upload = multer({
  storage,
  fileFilter: (req, file, cb) => {
    const allowed = ['image/', 'video/', 'audio/', 'application/pdf', 'text/'];

    if (allowed.some(type => file.mimetype.startsWith(type)) || !file.mimetype) {
      cb(null, true);
    } else {
      console.log(' File bị chặn:', file.originalname, file.mimetype);
      cb(null, false);
    }
  }
});

function convertM4aToMp3(inputPath, outputPath) {
  return new Promise((resolve, reject) => {
    ffmpeg(inputPath)
      .audioCodec('libmp3lame')
      .audioBitrate('320k')
      .audioChannels(2)
      .audioFrequency(48000)
      .on('end', () => {
        console.log('Convert done:', outputPath);
        resolve(outputPath);
      })
      .on('error', (err) => {
        console.error('Convert error:', err);
        reject(err);
      })
      .save(outputPath);
  });
}

/* ================== NOTES CRUD ================== */

// ===== Thêm ghi chú =====
router.post('/add', auth, upload.none(), async (req, res) => {
  let { title, content, important } = req.body;

  if (!title || title.trim() === '') {
    return res.status(400).json({ message: 'Title is required' });
  }

  // Parse important sang 0 hoặc 1
  important = (important === true || important === "true" || important === 1) ? 1 : 0;

  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('userId', sql.Int, req.user.id)
      .input('title', sql.NVarChar, title.trim())
      .input('content', sql.NVarChar, content || '')
      .input('important', sql.Bit, important)
      .query(`
        INSERT INTO Notes (userId, title, content, important)
        VALUES (@userId, @title, @content, @important);
        SELECT * FROM Notes WHERE id = SCOPE_IDENTITY();
      `);

    res.json(result.recordset[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// ===== Sửa ghi chú =====
router.put('/edit/:id', auth, upload.none(), async (req, res) => {
  let { title, content, important } = req.body;
  const { id } = req.params;

  if (!title || title.trim() === '') {
    return res.status(400).json({ message: 'Title is required' });
  }

  important = (important === true || important === "true" || important === 1) ? 1 : 0;

  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('id', sql.Int, id)
      .input('userId', sql.Int, req.user.id)
      .input('title', sql.NVarChar, title.trim())
      .input('content', sql.NVarChar, content || '')
      .input('important', sql.Bit, important)
      .query(`
        UPDATE Notes
        SET title=@title, content=@content, important=@important, updatedAt=GETDATE()
        WHERE id=@id AND userId=@userId;
        SELECT * FROM Notes WHERE id=@id AND userId=@userId;
      `);

    if (result.recordset.length === 0)
      return res.status(404).json({ message: 'Note not found' });

    res.json(result.recordset[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});



// ===== Xóa ghi chú =====
router.delete('/delete/:id', auth, async (req, res) => {
  const { id } = req.params;
  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('id', sql.Int, id)
      .input('userId', sql.Int, req.user.id)
      .query(`
        SELECT * FROM Notes WHERE id=@id AND userId=@userId;
        DELETE FROM Notes WHERE id=@id AND userId=@userId;
      `);

    if (result.recordset.length === 0)
      return res.status(404).json({ message: 'Note not found or already deleted' });

    res.json(result.recordset[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// ===== Toggle important =====
router.put('/important/:id', auth, async (req, res) => {
  const { id } = req.params;
  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('id', sql.Int, id)
      .input('userId', sql.Int, req.user.id)
      .query(`
        UPDATE Notes
        SET important = CASE WHEN important=1 THEN 0 ELSE 1 END, updatedAt=GETDATE()
        WHERE id=@id AND userId=@userId;
        SELECT * FROM Notes WHERE id=@id AND userId=@userId;
      `);

    if (result.recordset.length === 0)
      return res.status(404).json({ message: 'Note not found' });

    res.json(result.recordset[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// ===== Tìm kiếm ghi chú =====
router.get('/search', auth, async (req, res) => {
  const { q } = req.query;
  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('userId', sql.Int, req.user.id)
      .input('q', sql.NVarChar, `%${q}%`)
      .query(`
        SELECT *
        FROM Notes
        WHERE userId=@userId AND (title LIKE @q OR content LIKE @q)
        ORDER BY important DESC, ISNULL(updatedAt, createdAt) DESC, createdAt DESC
      `);
    res.json(result.recordset);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// ===== Hiển thị tất cả ghi chú của user =====
router.get('/', auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('userId', sql.Int, req.user.id)
      .query(`
        SELECT *
        FROM Notes
        WHERE userId=@userId
        ORDER BY important DESC, ISNULL(updatedAt, createdAt) DESC, createdAt DESC
      `);
    res.json(result.recordset);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

/* ================== ATTACHMENTS ================== */


// ===== Lấy danh sách file của note =====
router.get('/:noteId/attachments', auth, async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request()
      .input('noteId', sql.Int, req.params.noteId)
      .query(`SELECT * FROM NoteAttachments WHERE noteId=@noteId ORDER BY uploadedAt DESC`);
    res.json(result.recordset);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// Route upload nhiều file
router.post('/upload/:noteId/multiple', auth, upload.array('files', 20), async (req, res) => {
  try {
    if (!req.files || req.files.length === 0)
      return res.status(400).json({ message: 'No files uploaded' });

    const pool = await poolPromise;

    const attachmentPromises = req.files.map(async (file) => {
      const fullPath = path.join(file.destination, file.filename);
      let finalPath = `/uploads/notes/${file.filename}`;
      let mimeType = file.mimetype;

      // Nếu là audio → convert sang mp3
      if (mimeType.startsWith('audio/') && !file.filename.endsWith('.mp3')) {
        const mp3Name = file.filename.replace(path.extname(file.filename), '.mp3');
        const mp3Path = path.join(file.destination, mp3Name);

        await convertM4aToMp3(fullPath, mp3Path);  
        fs.unlinkSync(fullPath);                   

        finalPath = `/uploads/notes/${mp3Name}`;
        mimeType = 'audio/mpeg';
      }

      const fileType = mimeType.startsWith('image/') ? 'image'
                     : mimeType.startsWith('video/') ? 'video'
                     : mimeType.startsWith('audio/') ? 'audio'
                     : 'other';

      const result = await pool.request()
        .input('noteId', sql.Int, req.params.noteId)
        .input('fileType', sql.NVarChar, fileType)
        .input('filePath', sql.NVarChar, finalPath)
        .query(`
          INSERT INTO NoteAttachments (noteId, filePath, fileType, uploadedAt)
          VALUES (@noteId, @filePath, @fileType, GETDATE());
          SELECT * FROM NoteAttachments WHERE id = SCOPE_IDENTITY();
        `);

      return result.recordset[0];
    });

    const attachments = await Promise.all(attachmentPromises);
    res.json({ message: 'Files uploaded successfully', attachments });

  } catch (err) {
    console.error('Upload error:', err);
    res.status(500).json({ message: err.message });
  }
});


router.get('/:id', auth, async (req, res) => {
  const { id } = req.params;
  try {
    const pool = await poolPromise;
    const noteResult = await pool.request()
      .input('id', sql.Int, id)
      .input('userId', sql.Int, req.user.id)
      .query(`SELECT * FROM Notes WHERE id=@id AND userId=@userId`);

    if (noteResult.recordset.length === 0) return res.status(404).json({ message: 'Note not found' });

    const attachmentsResult = await pool.request()
      .input('noteId', sql.Int, id)
      .query(`SELECT * FROM NoteAttachments WHERE noteId=@noteId`);

    const note = noteResult.recordset[0];
    note.attachments = attachmentsResult.recordset;

    res.json(note);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});
// ===== Xóa file đính kèm của 1 note =====
router.post("/:id/delete-files", auth, async (req, res) => {
  try {
    const noteId = req.params.id;
    const { files } = req.body; 

    if (!files || !Array.isArray(files) || files.length === 0) {
      return res.status(400).json({ error: "Chưa chọn file để xóa" });
    }

    const pool = await poolPromise;

    // Lấy danh sách file đính kèm của note
    const attachments = await pool.request()
      .input("noteId", sql.Int, noteId)
      .query(`SELECT * FROM NoteAttachments WHERE noteId=@noteId`);

    if (attachments.recordset.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy file đính kèm nào" });
    }

    // Lọc ra các file cần xóa
    const filesToDelete = attachments.recordset.filter(att =>
      files.includes(path.basename(att.filePath))
    );

    if (filesToDelete.length === 0) {
      return res.status(400).json({ error: "Không tìm thấy file hợp lệ để xóa" });
    }

    // Xóa file vật lý
    for (const f of filesToDelete) {
      const filePath = path.join(__dirname, "..", f.filePath);
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }
    }

    // Xóa khỏi DB
    for (const f of filesToDelete) {
      await pool.request()
        .input("id", sql.Int, f.id)
        .query(`DELETE FROM NoteAttachments WHERE id=@id`);
    }

    res.json({ success: true, message: "Xóa file thành công", deleted: filesToDelete.length });
  } catch (err) {
    console.error("Lỗi xóa file:", err);
    res.status(500).json({ error: "Lỗi server khi xóa file" });
  }
});


module.exports = router;
