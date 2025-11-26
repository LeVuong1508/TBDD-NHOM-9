const sql = require('mssql');
require('dotenv').config();

const config = {
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  server: process.env.DB_SERVER, // ví dụ: localhost
  database: process.env.DB_NAME,
  options: {
    encrypt: true, // nếu SQL Server cần kết nối qua TLS
    trustServerCertificate: true // với dev local
  }
};

const poolPromise = new sql.ConnectionPool(config)
  .connect()
  .then(pool => {
    console.log('Connected to SQL Server');
    return pool;
  })
  .catch(err => console.log('Database Connection Failed! ', err));

module.exports = { sql, poolPromise };
