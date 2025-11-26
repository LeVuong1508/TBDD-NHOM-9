const express = require('express');
const router = express.Router();
const sql = require('mssql');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
require('dotenv').config();

// Kết nối SQL Server
const dbConfig = {
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  server: process.env.DB_SERVER,
  database: process.env.DB_NAME,
  options: { trustServerCertificate: true }
};

// Đăng ký
router.post('/register', async (req, res) => {
  const { email, password, fullname } = req.body;

  // Kiểm tra mật khẩu
  const passwordRegex = /^(?=.*[0-9])(?=.*[!@#$%^&*(),.?":{}|<>]).{8,}$/;
  if (!passwordRegex.test(password)) {
    return res.status(400).json({ 
      message: 'Mật khẩu phải có ít nhất 8 ký tự, gồm 1 chữ số và 1 ký tự đặc biệt' 
    });
  }
  try {
    await sql.connect(dbConfig);

    // kiểm tra email đã tồn tại chưa
    const checkUser = await sql.query`SELECT * FROM Users WHERE email=${email}`;
    if (checkUser.recordset.length > 0) {
      return res.status(400).json({ message: 'Email đã tồn tại' });
    }

    const hashed = await bcrypt.hash(password, 10);
    await sql.query`INSERT INTO Users (email, password, fullname) VALUES (${email}, ${hashed}, ${fullname})`;
    res.json({ message: 'Đăng ký thành công' });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// Đăng nhập
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  try {
    await sql.connect(dbConfig);
    const result = await sql.query`SELECT * FROM Users WHERE email=${email}`;
    if (result.recordset.length === 0) return res.status(400).json({ message: 'Không tìm thấy user' });

    const user = result.recordset[0];
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) return res.status(400).json({ message: 'Sai mật khẩu' });

    const token = jwt.sign({ user: { id: user.id } }, process.env.JWT_SECRET, { expiresIn: '1h' });
    res.json({ message: 'Đăng nhập thành công', token });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

module.exports = router;
