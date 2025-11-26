-- =======================
-- Bảng Users
-- =======================
CREATE TABLE Users (
    id INT IDENTITY(1,1) PRIMARY KEY,
    email NVARCHAR(255) NOT NULL UNIQUE,
    password NVARCHAR(255) NOT NULL,
    fullname NVARCHAR(255) NOT NULL
);

-- =======================
-- Bảng Notes
-- =======================
CREATE TABLE Notes (
    id INT IDENTITY(1,1) PRIMARY KEY,
    userId INT NOT NULL,
    title NVARCHAR(255) NOT NULL DEFAULT 'Untitled',
    content NVARCHAR(MAX) NOT NULL DEFAULT '',
    important BIT NOT NULL DEFAULT 0,
    createdAt DATETIME NOT NULL DEFAULT GETDATE(),
    updatedAt DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_Notes_Users FOREIGN KEY (userId)
        REFERENCES Users(id) ON DELETE CASCADE
);

-- =======================
-- Bảng NoteAttachments (file: ảnh, video, ghi âm, ...)
-- =======================
CREATE TABLE NoteAttachments (
    id INT IDENTITY(1,1) PRIMARY KEY,
    noteId INT NOT NULL,
    filePath NVARCHAR(500) NOT NULL,
    fileType NVARCHAR(50) NOT NULL, -- ví dụ: 'image', 'video', 'audio', 'other'
    uploadedAt DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_Attachments_Notes FOREIGN KEY (noteId)
        REFERENCES Notes(id) ON DELETE CASCADE
);

CREATE TABLE NoteShares (
  id INT PRIMARY KEY IDENTITY,
  noteId INT NOT NULL,
  fromUserId INT NOT NULL,
  toUserId INT NOT NULL,
  zipPath NVARCHAR(255) NOT NULL,
  status NVARCHAR(20) DEFAULT 'pending', -- pending/accepted/rejected
  createdAt DATETIME DEFAULT GETDATE()

   CONSTRAINT FK_NoteShares_Notes FOREIGN KEY (noteId)
        REFERENCES Notes(id) ON DELETE CASCADE,

);




