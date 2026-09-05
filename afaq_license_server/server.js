const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'subscriptions.json');
const PUBLIC_DIR = path.join(__dirname, 'public');

// Optional Neon PostgreSQL Integration
const DATABASE_URL = process.env.DATABASE_URL;
let pool = null;

if (DATABASE_URL) {
    try {
        const { Pool } = require('pg');
        pool = new Pool({
            connectionString: DATABASE_URL,
            ssl: { rejectUnauthorized: false }
        });
        console.log('✅ Connected to Neon PostgreSQL Database!');
        initNeonDB();
    } catch (e) {
        console.log('⚠️ Could not initialize pg driver, falling back to local JSON DB.', e.message);
    }
} else {
    console.log('ℹ️ DATABASE_URL not provided. Running on Local JSON DB (subscriptions.json).');
}

// Initialize Neon Table
async function initNeonDB() {
    if (!pool) return;
    const query = `
        CREATE TABLE IF NOT EXISTS subscriptions (
            id VARCHAR(50) PRIMARY KEY,
            customer_name VARCHAR(100) NOT NULL,
            phone VARCHAR(50),
            email VARCHAR(100),
            status VARCHAR(20) DEFAULT 'ACTIVE',
            expiry_date VARCHAR(50),
            device_id VARCHAR(100),
            device_name VARCHAR(100),
            max_offline_days INT DEFAULT 15,
            notes TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    `;
    try {
        await pool.query(query);
        console.log('✅ Neon PostgreSQL Subscriptions table verified/created.');
        // Seed initial sub if table empty
        const countRes = await pool.query('SELECT COUNT(*) FROM subscriptions');
        if (parseInt(countRes.rows[0].count) === 0) {
            await pool.query(`
                INSERT INTO subscriptions (id, customer_name, phone, email, status, expiry_date, max_offline_days)
                VALUES ('SUB-1001', 'عميل تجريبي ممتاز', '770000000', 'client@afaq.com', 'ACTIVE', '2027-12-31', 15)
            `);
            console.log('✅ Seeded initial subscription to Neon DB.');
        }
    } catch (err) {
        console.error('❌ Error initializing Neon DB table:', err);
    }
}

// MIME types for static files
const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.ico': 'image/x-icon'
};

// ================= LOCAL JSON DB FALLBACK =================
function loadLocalDB() {
    if (!fs.existsSync(DB_FILE)) {
        const initialData = {
            subscriptions: [
                {
                    id: "SUB-1001",
                    customerName: "عميل تجريبي ممتاز",
                    phone: "770000000",
                    email: "client@afaq.com",
                    status: "ACTIVE",
                    expiryDate: "2027-12-31",
                    deviceId: null,
                    deviceName: null,
                    maxOfflineDays: 15,
                    createdAt: new Date().toISOString()
                }
            ]
        };
        fs.writeFileSync(DB_FILE, JSON.stringify(initialData, null, 2));
        return initialData;
    }
    try {
        const content = fs.readFileSync(DB_FILE, 'utf8');
        return JSON.parse(content);
    } catch (e) {
        return { subscriptions: [] };
    }
}

function saveLocalDB(data) {
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2));
}

function normalize(str) {
    return (str || '').toString().trim().toLowerCase();
}

function sendJSON(res, statusCode, data) {
    res.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end(JSON.stringify(data));
}

// ================= DATA ABSTRACTED METHODS (NEON / LOCAL) =================
async function getAllSubscriptions() {
    if (pool) {
        try {
            const res = await pool.query('SELECT * FROM subscriptions ORDER BY created_at DESC');
            return res.rows.map(r => ({
                id: r.id,
                customerName: r.customer_name,
                phone: r.phone || '',
                email: r.email || '',
                status: r.status,
                expiryDate: r.expiry_date,
                deviceId: r.device_id,
                deviceName: r.device_name,
                maxOfflineDays: r.max_offline_days || 15,
                notes: r.notes || '',
                createdAt: r.created_at
            }));
        } catch (e) {
            console.error('Neon query error, fallback to local', e);
        }
    }
    return loadLocalDB().subscriptions;
}

async function findSubscriptionByIdentifier(identifier) {
    const q = normalize(identifier);
    const all = await getAllSubscriptions();
    return all.find(s => normalize(s.email) === q || normalize(s.phone) === q);
}

async function findSubscriptionByDeviceId(deviceId) {
    const all = await getAllSubscriptions();
    return all.find(s => s.deviceId === deviceId);
}

async function createSubscription(newSub) {
    if (pool) {
        try {
            await pool.query(
                `INSERT INTO subscriptions (id, customer_name, phone, email, status, expiry_date, max_offline_days, notes)
                 VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                [newSub.id, newSub.customerName, newSub.phone, newSub.email, newSub.status, newSub.expiryDate, newSub.maxOfflineDays, newSub.notes || '']
            );
            return newSub;
        } catch (e) {
            console.error('Neon insert error', e);
        }
    }
    const db = loadLocalDB();
    db.subscriptions.unshift(newSub);
    saveLocalDB(db);
    return newSub;
}

async function updateSubscriptionRecord(sub) {
    if (pool) {
        try {
            await pool.query(
                `UPDATE subscriptions
                 SET customer_name = $1, phone = $2, email = $3, status = $4, expiry_date = $5,
                     device_id = $6, device_name = $7, max_offline_days = $8, notes = $9
                 WHERE id = $10`,
                [sub.customerName, sub.phone, sub.email, sub.status, sub.expiryDate, sub.deviceId, sub.deviceName, sub.maxOfflineDays, sub.notes || '', sub.id]
            );
            return sub;
        } catch (e) {
            console.error('Neon update error', e);
        }
    }
    const db = loadLocalDB();
    const idx = db.subscriptions.findIndex(s => s.id === sub.id);
    if (idx >= 0) db.subscriptions[idx] = sub;
    saveLocalDB(db);
    return sub;
}

async function deleteSubscriptionRecord(id) {
    if (pool) {
        try {
            await pool.query('DELETE FROM subscriptions WHERE id = $1', [id]);
            return true;
        } catch (e) {
            console.error('Neon delete error', e);
        }
    }
    const db = loadLocalDB();
    db.subscriptions = db.subscriptions.filter(s => s.id !== id);
    saveLocalDB(db);
    return true;
}

// ================= HTTP SERVER & API ROUTES =================
const server = http.createServer(async (req, res) => {
    // CORS Preflight
    if (req.method === 'OPTIONS') {
        res.writeHead(204, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
        });
        res.end();
        return;
    }

    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;

    let bodyData = '';
    req.on('data', chunk => { bodyData += chunk; });
    req.on('end', async () => {
        let body = {};
        if (bodyData) {
            try { body = JSON.parse(bodyData); } catch (e) {}
        }

        try {
            // ================= API ROUTING =================
            if (pathname.startsWith('/api/admin/')) {
                const clientKey = req.headers['x-admin-key'];
                const serverKey = process.env.ADMIN_API_KEY || 'afaq-admin-12345'; // Change this in production
                if (!clientKey || clientKey !== serverKey) {
                    return sendJSON(res, 401, { success: false, message: "Unauthorized: Invalid Admin API Key." });
                }
            }

            if (pathname === '/api/stats' && req.method === 'GET') {
                const subs = await getAllSubscriptions();
                return sendJSON(res, 200, { success: true, count: subs.length, dbType: pool ? 'Neon PostgreSQL' : 'Local JSON' });
            }

            if (pathname === '/api/license/activate' && req.method === 'POST') {
                const { identifier, deviceId, deviceName } = body;
                if (!identifier || !deviceId) {
                    return sendJSON(res, 400, { success: false, message: "يرجى تقديم البريد/الهاتف ومعرف الجهاز." });
                }

                const sub = await findSubscriptionByIdentifier(identifier);

                if (!sub) {
                    return sendJSON(res, 404, { success: false, message: "لم يتم العثور على اشتراك مسجل بهذا البريد أو الهاتف." });
                }

                if (sub.status !== "ACTIVE") {
                    return sendJSON(res, 403, { success: false, message: "حساب الاشتراك هذا معطل حالياً." });
                }

                const today = new Date().toISOString().split('T')[0];
                if (sub.expiryDate < today) {
                    return sendJSON(res, 403, { success: false, message: `انتهت صلاحية هذا الاشتراك بتاريخ (${sub.expiryDate}).` });
                }

                if (!sub.deviceId) {
                    sub.deviceId = deviceId;
                    sub.deviceName = deviceName || "جهاز أندرويد";
                    sub.activatedAt = new Date().toISOString();
                    await updateSubscriptionRecord(sub);
                } else if (sub.deviceId !== deviceId) {
                    return sendJSON(res, 403, { success: false, message: "هذا الاشتراك مرتبط بجهاز آخر بالفعل." });
                }

                return sendJSON(res, 200, {
                    success: true,
                    message: "تم تفعيل الاشتراك وتوثيق الجهاز بنجاح!",
                    subscription: {
                        id: sub.id,
                        customerName: sub.customerName,
                        phone: sub.phone,
                        email: sub.email,
                        expiryDate: sub.expiryDate,
                        maxOfflineDays: sub.maxOfflineDays || 15
                    }
                });
            }

            if (pathname === '/api/license/check' && req.method === 'POST') {
                const { deviceId, identifier } = body;
                if (!deviceId) return sendJSON(res, 400, { success: false, message: "معرف الجهاز مطلوب." });

                let sub = await findSubscriptionByDeviceId(deviceId);
                if (!sub && identifier) {
                    sub = await findSubscriptionByIdentifier(identifier);
                }

                if (!sub || sub.deviceId !== deviceId) {
                    return sendJSON(res, 200, { isValid: false, message: "الجهاز غير مرتبط بأي اشتراك نشط." });
                }

                const today = new Date().toISOString().split('T')[0];
                const isValid = sub.status === "ACTIVE" && sub.expiryDate >= today;

                return sendJSON(res, 200, {
                    isValid: isValid,
                    status: sub.status,
                    expiryDate: sub.expiryDate,
                    maxOfflineDays: sub.maxOfflineDays || 15,
                    customerName: sub.customerName
                });
            }

            if (pathname === '/api/admin/subscriptions' && req.method === 'GET') {
                const subs = await getAllSubscriptions();
                return sendJSON(res, 200, subs);
            }

            if (pathname === '/api/admin/subscriptions/create' && req.method === 'POST') {
                const { customerName, phone, email, expiryDate, durationMonths, durationDays, maxOfflineDays, notes } = body;
                if (!customerName || (!phone && !email)) {
                    return sendJSON(res, 400, { success: false, message: "اسم العميل والبريد أو الهاتف مطلوبة." });
                }

                let finalExpiryDate = expiryDate;
                if (durationMonths || durationDays) {
                    const now = new Date();
                    const months = parseInt(durationMonths) || 0;
                    const days = parseInt(durationDays) || 0;
                    now.setMonth(now.getMonth() + months);
                    now.setDate(now.getDate() + days);
                    finalExpiryDate = now.toISOString().split('T')[0];
                }

                if (!finalExpiryDate) {
                    const now = new Date();
                    now.setFullYear(now.getFullYear() + 1);
                    finalExpiryDate = now.toISOString().split('T')[0];
                }

                const newId = "SUB-" + Math.floor(1000 + Math.random() * 9000);
                const newSub = {
                    id: newId,
                    customerName,
                    phone: phone || "",
                    email: email || "",
                    status: "ACTIVE",
                    expiryDate: finalExpiryDate,
                    deviceId: null,
                    deviceName: null,
                    maxOfflineDays: parseInt(maxOfflineDays) || 15,
                    notes: notes || "",
                    createdAt: new Date().toISOString()
                };

                await createSubscription(newSub);
                return sendJSON(res, 200, { success: true, message: "تم إنشاء الاشتراك بنجاح!", subscription: newSub });
            }

            if (pathname === '/api/admin/subscriptions/renew' && req.method === 'POST') {
                const { id, addMonths, addDays } = body;
                const subs = await getAllSubscriptions();
                const sub = subs.find(s => s.id === id);
                if (!sub) return sendJSON(res, 404, { success: false, message: "الاشتراك غير موجود." });

                const baseDate = (sub.expiryDate && sub.expiryDate > new Date().toISOString().split('T')[0])
                    ? new Date(sub.expiryDate)
                    : new Date();

                const months = parseInt(addMonths) || 0;
                const days = parseInt(addDays) || 0;
                baseDate.setMonth(baseDate.getMonth() + months);
                baseDate.setDate(baseDate.getDate() + days);

                sub.expiryDate = baseDate.toISOString().split('T')[0];
                sub.status = "ACTIVE";
                await updateSubscriptionRecord(sub);

                return sendJSON(res, 200, { success: true, message: `تم تمديد الاشتراك حتى (${sub.expiryDate}) بنجاح!`, subscription: sub });
            }

            if (pathname === '/api/admin/subscriptions/update' && req.method === 'POST') {
                const { id, customerName, phone, email, expiryDate, notes, maxOfflineDays } = body;
                const subs = await getAllSubscriptions();
                const sub = subs.find(s => s.id === id);
                if (!sub) return sendJSON(res, 404, { success: false, message: "الاشتراك غير موجود." });

                if (customerName) sub.customerName = customerName;
                if (phone !== undefined) sub.phone = phone;
                if (email !== undefined) sub.email = email;
                if (expiryDate) sub.expiryDate = expiryDate;
                if (notes !== undefined) sub.notes = notes;
                if (maxOfflineDays) sub.maxOfflineDays = parseInt(maxOfflineDays);

                await updateSubscriptionRecord(sub);
                return sendJSON(res, 200, { success: true, message: "تم تحديث بيانات الترخيص بنجاح!", subscription: sub });
            }

            if (pathname === '/api/admin/subscriptions/toggle' && req.method === 'POST') {
                const { id } = body;
                const subs = await getAllSubscriptions();
                const sub = subs.find(s => s.id === id);
                if (!sub) return sendJSON(res, 404, { success: false, message: "الاشتراك غير موجود." });

                sub.status = sub.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
                await updateSubscriptionRecord(sub);
                return sendJSON(res, 200, { success: true, message: `تم تغيير حالة الاشتراك إلى ${sub.status}`, status: sub.status });
            }

            if (pathname === '/api/admin/subscriptions/unbind' && req.method === 'POST') {
                const { id } = body;
                const subs = await getAllSubscriptions();
                const sub = subs.find(s => s.id === id);
                if (!sub) return sendJSON(res, 404, { success: false, message: "الاشتراك غير موجود." });

                sub.deviceId = null;
                sub.deviceName = null;
                await updateSubscriptionRecord(sub);
                return sendJSON(res, 200, { success: true, message: "تم فك ربط الجهاز بنجاح." });
            }

            if (pathname === '/api/admin/subscriptions/delete' && req.method === 'POST') {
                const { id } = body;
                await deleteSubscriptionRecord(id);
                return sendJSON(res, 200, { success: true, message: "تم حذف الاشتراك." });
            }

            // ================= STATIC FILES SERVING =================
            let filePath = path.join(PUBLIC_DIR, pathname === '/' ? 'index.html' : pathname);
            const ext = path.extname(filePath);

            fs.readFile(filePath, (err, content) => {
                if (err) {
                    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
                    res.end('404 - الصفحة غير موجودة');
                } else {
                    res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
                    res.end(content);
                }
            });

        } catch (err) {
            console.error('Server Internal Error:', err);
            sendJSON(res, 500, { success: false, message: "حدث خطأ داخلي في السيرفر." });
        }
    });
});

server.listen(PORT, () => {
    console.log(`====================================================`);
    console.log(`🚀 Afaq License Server is running on port ${PORT}`);
    console.log(`🌐 Web Admin Dashboard: http://localhost:${PORT}`);
    console.log(`🐘 Database Mode: ${DATABASE_URL ? 'Neon PostgreSQL (Cloud DB)' : 'Local JSON File'}`);
    console.log(`====================================================`);
});
