let allSubscriptions = [];
let currentFilter = 'ALL';

document.addEventListener('DOMContentLoaded', () => {
    fetchSubscriptions();
});

function switchTab(tabName, event) {
    if (event) event.preventDefault();

    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => item.classList.remove('active'));
    if (event && event.currentTarget) {
        event.currentTarget.classList.add('active');
    }

    const statsGrid = document.querySelector('.stats-grid');
    const pageTitle = document.getElementById('page-title');

    if (tabName === 'dashboard') {
        if (statsGrid) statsGrid.style.display = 'grid';
        if (pageTitle) pageTitle.innerText = 'لوحة التحكم والتراخيص';
    } else if (tabName === 'subscriptions') {
        if (statsGrid) statsGrid.style.display = 'none';
        if (pageTitle) pageTitle.innerText = 'إدارة التراخيص والعملاء';
        const searchInput = document.getElementById('search-input');
        if (searchInput) searchInput.focus();
    }
}


async function fetchSubscriptions() {
    try {
        const res = await fetch('/api/admin/subscriptions');
        allSubscriptions = await res.json();
        renderStats();
        renderSubscriptions();
    } catch (e) {
        console.error('Failed to fetch subscriptions:', e);
    }
}

function renderStats() {
    const total = allSubscriptions.length;
    const active = allSubscriptions.filter(s => s.status === 'ACTIVE').length;
    const bound = allSubscriptions.filter(s => s.deviceId).length;
    const expired = allSubscriptions.filter(s => s.status === 'DISABLED' || isExpired(s.expiryDate)).length;

    document.getElementById('stat-total').innerText = total;
    document.getElementById('stat-active').innerText = active;
    document.getElementById('stat-devices').innerText = bound;
    document.getElementById('stat-expired').innerText = expired;
}

function isExpired(dateStr) {
    if (!dateStr) return false;
    const today = new Date().toISOString().split('T')[0];
    return dateStr < today;
}

function renderSubscriptions() {
    const listElem = document.getElementById('subscriptions-list');
    const searchQuery = document.getElementById('search-input').value.trim().toLowerCase();

    const filtered = allSubscriptions.filter(sub => {
        const matchesSearch = !searchQuery || 
            sub.customerName.toLowerCase().includes(searchQuery) ||
            sub.phone.toLowerCase().includes(searchQuery) ||
            sub.email.toLowerCase().includes(searchQuery) ||
            sub.id.toLowerCase().includes(searchQuery);

        if (!matchesSearch) return false;

        if (currentFilter === 'ACTIVE') return sub.status === 'ACTIVE' && !isExpired(sub.expiryDate);
        if (currentFilter === 'DISABLED') return sub.status === 'DISABLED' || isExpired(sub.expiryDate);
        if (currentFilter === 'BOUND') return !!sub.deviceId;
        return true;
    });

    if (filtered.length === 0) {
        listElem.innerHTML = `
            <tr>
                <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 32px;">
                    لا توجد اشتراكات مطابقة للبحث حالياً.
                </td>
            </tr>
        `;
        return;
    }

    listElem.innerHTML = filtered.map(sub => {
        const expired = isExpired(sub.expiryDate);
        let statusBadge = '';
        if (sub.status === 'DISABLED') {
            statusBadge = `<span class="status-badge disabled">❌ معطل</span>`;
        } else if (expired) {
            statusBadge = `<span class="status-badge expired">⚠️ منتهي</span>`;
        } else {
            statusBadge = `<span class="status-badge active">✅ نشط</span>`;
        }

        const deviceHtml = sub.deviceId 
            ? `<div style="display:flex; flex-direction:column; gap:4px;">
                <span class="device-tag">📱 ${sub.deviceId.substring(0, 14)}...</span>
                <button class="btn btn-warning-sm" onclick="unbindDevice('${sub.id}')">🔓 فصل الجهاز</button>
               </div>`
            : `<span style="color: var(--text-muted); font-size:11px;">غير مرتبط بجهاز</span>`;

        return `
            <tr>
                <td><strong>${sub.id}</strong></td>
                <td><strong>${sub.customerName}</strong></td>
                <td>
                    <div>📞 ${sub.phone || 'غير محدد'}</div>
                    <div style="font-size:11px; color:var(--text-muted);">✉️ ${sub.email || 'غير محدد'}</div>
                </td>
                <td>${sub.expiryDate}</td>
                <td>${statusBadge}</td>
                <td>${deviceHtml}</td>
                <td>${sub.maxOfflineDays || 15} يوماً</td>
                <td>
                    <div style="display:flex; gap:4px; flex-wrap:wrap;">
                        <button class="btn btn-sm btn-primary" onclick="renewSubscriptionPrompt('${sub.id}')">🔄 تجديد</button>
                        <button class="btn btn-sm ${sub.status === 'ACTIVE' ? 'btn-warning-sm' : 'btn-primary'}" onclick="toggleStatus('${sub.id}')">
                            ${sub.status === 'ACTIVE' ? 'إيقاف' : 'تفعيل'}
                        </button>
                        <button class="btn btn-danger-sm btn-sm" onclick="deleteSubscription('${sub.id}')">🗑️</button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}


function filterSubscriptions() {
    renderSubscriptions();
}

function filterByStatus(status, elem) {
    currentFilter = status;
    document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
    elem.classList.add('active');
    renderSubscriptions();
}

// Modal Control & Expiry Calculation
function openAddModal() {
    document.getElementById('add-modal').classList.add('open');
    calculateCalculatedExpiry();
}

function closeAddModal() {
    document.getElementById('add-modal').classList.remove('open');
}

function setPresetDuration(months, days) {
    document.getElementById('cust-months').value = months;
    document.getElementById('cust-days').value = days;
    calculateCalculatedExpiry();
}

function calculateCalculatedExpiry() {
    const months = parseInt(document.getElementById('cust-months').value) || 0;
    const days = parseInt(document.getElementById('cust-days').value) || 0;

    const date = new Date();
    date.setMonth(date.getMonth() + months);
    date.setDate(date.getDate() + days);

    const dateStr = date.toISOString().split('T')[0];
    document.getElementById('cust-expiry').value = dateStr;
}

async function handleCreateSubscription(e) {
    e.preventDefault();
    const customerName = document.getElementById('cust-name').value.trim();
    const phone = document.getElementById('cust-phone').value.trim();
    const email = document.getElementById('cust-email').value.trim();
    const durationMonths = document.getElementById('cust-months').value;
    const durationDays = document.getElementById('cust-days').value;
    const expiryDate = document.getElementById('cust-expiry').value;
    const maxOfflineDays = document.getElementById('cust-offline').value;

    try {
        const res = await fetch('/api/admin/subscriptions/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ customerName, phone, email, expiryDate, durationMonths, durationDays, maxOfflineDays })
        });

        const data = await res.json();
        if (data.success) {
            closeAddModal();
            document.getElementById('add-form').reset();
            fetchSubscriptions();
        } else {
            alert(data.message || 'حدث خطأ أثناء حفظ الترخيص.');
        }
    } catch (err) {
        alert('فشل الاتصال بالسيرفر.');
    }
}

async function toggleStatus(id) {
    try {
        const res = await fetch('/api/admin/subscriptions/toggle', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        });
        await fetchSubscriptions();
    } catch (err) {
        alert('فشل الاتصال بالسيرفر.');
    }
}

async function unbindDevice(id) {
    if (!confirm('هل أنت تأكد من فك ربط الجهاز عن هذا العميل؟ سيتكمن العميل من التفعيل على جهاز جديد.')) return;
    try {
        const res = await fetch('/api/admin/subscriptions/unbind', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        });
        await fetchSubscriptions();
    } catch (err) {
        alert('فشل الاتصال بالسيرفر.');
    }
}

async function deleteSubscription(id) {
    if (!confirm('هل أنت متاكد من حذف هذا الاشتراك نهائياً؟')) return;
    try {
        await fetch('/api/admin/subscriptions/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        });
        await fetchSubscriptions();
    } catch (err) {
        alert('فشل الاتصال بالسيرفر.');
    }
}

async function renewSubscriptionPrompt(id) {

    const months = prompt("أدخل عدد الأشهر للتمديد (مثال: 12 لسنة كاملة، أو 1 لـ شهر):", "12");
    if (!months) return;

    const addMonths = parseInt(months) || 0;
    if (addMonths <= 0) return alert("يرجى إدخال عدد أشهر صحيح.");

    try {
        const res = await fetch('/api/admin/subscriptions/renew', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id, addMonths })
        });
        const data = await res.json();
        alert(data.message || "تم تجديد الاشتراك بنجاح.");
        fetchSubscriptions();
    } catch (e) {
        alert("فشل الاتصال بالسيرفر أثناء التجديد.");
    }
}

function exportCSV() {
    if (allSubscriptions.length === 0) return alert("لا توجد بيانات لتصديرها.");

    let csvContent = "data:text/csv;charset=utf-8,\uFEFF";
    csvContent += "معرف الترخيص,اسم العميل,الهاتف,البريد الإلكتروني,الحالة,تاريخ الانتهاء,الجهاز المربوط\n";

    allSubscriptions.forEach(sub => {
        const row = [
            sub.id,
            `"${sub.customerName}"`,
            `"${sub.phone || ''}"`,
            `"${sub.email || ''}"`,
            sub.status,
            sub.expiryDate,
            `"${sub.deviceId || 'غير مرتبط'}"`
        ].join(",");
        csvContent += row + "\n";
    });

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `afaq_subscriptions_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

