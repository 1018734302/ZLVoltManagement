const state = {
    token: localStorage.getItem('zl_token') || '',
    user: null,
    page: 'dashboard',
    cache: {},
    selectedProject: null,
    demoResult: null,
    demoRunning: false,
    modalSubmit: null
};

const PAGE_META = {
    dashboard: ['经营驾驶舱', '合同、项目、采购、资金、运维一屏掌握'],
    demo: ['一键演示', '从客户到付款、再到并网运维的完整业务链'],
    crm: ['CRM 客户关系', '客户、线索、商机、合同与立项'],
    pm: ['项目管理 PM', 'WBS、里程碑、进度、变更、成本与结算'],
    srm: ['采购 SRM', '供应商、物料、采购订单、发货与收货'],
    inv: ['库存管理', '收货入库、项目领料、库存台账与流水'],
    fin: ['财务结算', '供应商对账、发票、三单匹配与应付'],
    fund: ['资金管理', '资金账户、付款申请、审批与付款执行'],
    om: ['运维 O&M', '电站、设备、告警、工单与处理闭环'],
    iot: ['IoT 设备接入', 'MQTT 遥测、设备状态、告警触发']
};

const NAV = [
    { key: 'dashboard', icon: 'BI', label: '经营驾驶舱' },
    { key: 'demo', icon: 'E2E', label: '一键演示', badge: 'FULL' },
    { key: 'crm', icon: 'CRM', label: '客户关系' },
    { key: 'pm', icon: 'PM', label: '项目管理' },
    { key: 'srm', icon: 'SRM', label: '采购管理' },
    { key: 'inv', icon: 'INV', label: '库存管理' },
    { key: 'fin', icon: 'FIN', label: '财务结算' },
    { key: 'fund', icon: 'FUND', label: '资金管理' },
    { key: 'om', icon: 'O&M', label: '运维中心' },
    { key: 'iot', icon: 'IoT', label: '设备接入' }
];

const STATUS_MAP = {
    ACTIVE: ['green', '生效'],
    EFFECTIVE: ['green', '已生效'],
    WON: ['green', '赢单'],
    DONE: ['green', '已完成'],
    COMPLETED: ['green', '已完工'],
    CLEARED: ['gray', '已清除'],
    ALARM_OPEN: ['orange', '未处理'],
    RECEIVED: ['green', '已收货'],
    STORED: ['green', '已入库'],
    MATCHED: ['green', '已匹配'],
    PAID: ['green', '已付款'],
    RECONCILED: ['green', '已核销'],
    CLOSED: ['green', '已关闭'],
    APPROVED: ['green', '已审批'],
    SETTLED: ['green', '已结算'],
    GRID_CONNECTED: ['green', '已并网'],
    SUCCESS: ['green', '成功'],
    QUALIFIED: ['green', '合格'],
    APPROVING: ['blue', '审批中'],
    IN_PROGRESS: ['blue', '进行中'],
    PROCESSING: ['blue', '处理中'],
    ASSIGNED: ['blue', '已派单'],
    CREATED: ['blue', '待处理'],
    ACKED: ['blue', '已确认'],
    PENDING: ['orange', '待处理'],
    DRAFT: ['gray', '草稿'],
    OPEN: ['orange', '待付款'],
    PARTIAL: ['orange', '部分付款'],
    PARTIAL_RECEIVED: ['orange', '部分收货'],
    SUPPLIER_CONFIRMED: ['orange', '供应商已确认'],
    BUYER_CONFIRMED: ['orange', '采购已确认'],
    FINANCE_CONFIRMED: ['blue', '财务已确认'],
    INVOICED: ['blue', '已开票'],
    MISMATCH: ['red', '匹配异常'],
    REJECTED: ['red', '已驳回'],
    FAILED: ['red', '失败'],
    BLACKLIST: ['red', '黑名单'],
    CRITICAL: ['red', '严重'],
    MAJOR: ['orange', '重要'],
    MINOR: ['blue', '次要'],
    WARNING: ['gray', '提示'],
    RUNNING: ['green', '运行'],
    STANDBY: ['gray', '待机'],
    FAULT: ['red', '故障'],
    ONLINE: ['green', '在线'],
    OFFLINE: ['gray', '离线']
};

const COST_LABELS = {
    MATERIAL: '材料',
    LABOR: '人工',
    MACHINERY: '机械',
    SUBCONTRACT: '分包',
    OTHER: '其他'
};

function el(id) { return document.getElementById(id); }

function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function fmtMoney(value) {
    const number = Number(value || 0);
    return '¥' + number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtNum(value, digits = 2) {
    const number = Number(value || 0);
    return number.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

function fmtDate(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
}

function badge(status) {
    if (!status) return '<span class="badge gray">-</span>';
    const item = STATUS_MAP[status] || ['gray', status];
    return `<span class="badge ${item[0]}">${esc(item[1])}</span>`;
}

function table(headers, rows, renderRow, emptyText = '暂无数据') {
    if (!rows || rows.length === 0) {
        return `<div class="empty">${esc(emptyText)}</div>`;
    }
    return `<div class="table-wrap"><table><thead><tr>${headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(renderRow).join('')}</tbody></table></div>`;
}

function loading(text = '正在加载业务数据...') {
    return `<div class="loading">${esc(text)}</div>`;
}

function toast(message, type = '') {
    const box = el('toast');
    box.className = 'toast ' + type;
    box.textContent = message;
    box.classList.remove('hidden');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => box.classList.add('hidden'), 3200);
}

async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.token && options.auth !== false) headers.Authorization = 'Bearer ' + state.token;
    const response = await fetch(path, {
        ...options,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
    let body;
    try {
        body = await response.json();
    } catch (e) {
        body = { code: response.status, message: response.statusText || '请求失败' };
    }
    if (body.code !== 0) {
        if (body.code === 40101) {
            localStorage.removeItem('zl_token');
            state.token = '';
            showLogin();
        }
        throw new Error(body.message || '请求失败');
    }
    return body.data;
}

function showLogin() {
    el('app').classList.add('hidden');
    el('login').classList.remove('hidden');
    el('password').focus();
}

async function login() {
    const username = el('username').value.trim();
    const password = el('password').value;
    if (!username || !password) {
        toast('请输入账号和密码', 'error');
        return;
    }
    try {
        const data = await api('/api/auth/login', { method: 'POST', auth: false, body: { username, password } });
        state.token = data.token;
        state.user = data.user;
        localStorage.setItem('zl_token', data.token);
        bootApp();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function logout() {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
    localStorage.removeItem('zl_token');
    state.token = '';
    state.user = null;
    showLogin();
}

async function boot() {
    if (!state.token) {
        showLogin();
        return;
    }
    try {
        state.user = await api('/api/auth/me');
        bootApp();
    } catch (e) {
        showLogin();
    }
}

function bootApp() {
    el('login').classList.add('hidden');
    el('app').classList.remove('hidden');
    if (state.user) {
        el('userName').textContent = state.user.realName || state.user.username;
        el('userRole').textContent = (state.user.roleName || state.user.role || '') + ' · ' + (state.user.deptName || '');
        el('avatar').textContent = (state.user.realName || 'U').slice(0, 1);
    }
    renderNav();
    switchPage(state.page || 'dashboard');
}

function renderNav() {
    el('nav').innerHTML = NAV.map((item) => `
        <div class="nav-item ${state.page === item.key ? 'active' : ''}" onclick="switchPage('${item.key}')">
            <span class="nav-icon">${esc(item.icon)}</span>
            <span>${esc(item.label)}</span>
            ${item.badge ? `<span class="nav-badge">${esc(item.badge)}</span>` : ''}
        </div>
    `).join('');
}

function switchPage(page, keepProject = false) {
    state.page = page;
    if (!keepProject) state.selectedProject = null;
    const meta = PAGE_META[page] || ['ZL-Volt', ''];
    el('pageTitle').textContent = meta[0];
    el('pageSubtitle').textContent = meta[1];
    renderNav();
    renderPage();
}

async function renderPage() {
    const content = el('content');
    content.innerHTML = loading();
    try {
        switch (state.page) {
            case 'dashboard': await renderDashboard(); break;
            case 'demo': await renderDemo(); break;
            case 'crm': await renderCrm(); break;
            case 'pm': await renderPm(); break;
            case 'srm': await renderSrm(); break;
            case 'inv': await renderInv(); break;
            case 'fin': await renderFin(); break;
            case 'fund': await renderFund(); break;
            case 'om': await renderOm(); break;
            case 'iot': await renderIot(); break;
            default: await renderDashboard();
        }
    } catch (e) {
        content.innerHTML = `<div class="card"><div class="empty">加载失败：${esc(e.message)}<br><br><button class="primary" onclick="renderPage()">重试</button></div></div>`;
    }
}

function openModal(title, fields, onSubmit) {
    state.modalSubmit = onSubmit;
    el('modalTitle').textContent = title;
    el('modalBody').innerHTML = fields.map((field) => {
        const id = 'field_' + field.name;
        if (field.type === 'select') {
            const options = (field.options || []).map((option) => {
                if (typeof option === 'string') return `<option value="${esc(option)}">${esc(option)}</option>`;
                return `<option value="${esc(option.value)}" ${String(option.value) === String(field.value) ? 'selected' : ''}>${esc(option.label)}</option>`;
            }).join('');
            return `<label>${esc(field.label)}<select id="${id}" name="${esc(field.name)}">${options}</select></label>`;
        }
        if (field.type === 'textarea') {
            return `<label>${esc(field.label)}<textarea id="${id}" name="${esc(field.name)}" rows="3" placeholder="${esc(field.placeholder || '')}">${esc(field.value || '')}</textarea></label>`;
        }
        return `<label>${esc(field.label)}<input id="${id}" name="${esc(field.name)}" type="${field.type || 'text'}" value="${esc(field.value === undefined ? '' : field.value)}" placeholder="${esc(field.placeholder || '')}"></label>`;
    }).join('');
    el('modal').classList.remove('hidden');
}

function closeModal() {
    el('modal').classList.add('hidden');
    state.modalSubmit = null;
}

async function submitModal(event) {
    event.preventDefault();
    if (!state.modalSubmit) return;
    const form = new FormData(el('modalForm'));
    const values = {};
    form.forEach((value, key) => { values[key] = value; });
    try {
        await state.modalSubmit(values);
        closeModal();
    } catch (e) {
        toast(e.message, 'error');
    }
}

function optionsOf(items, valueKey = 'id', labelKey = 'name') {
    return (items || []).map((item) => ({ value: item[valueKey], label: item[labelKey] }));
}

/* ---------------- 驾驶舱 ---------------- */
async function renderDashboard() {
    const data = await api('/api/reports/dashboard');
    const costRows = (data.costByType || []).map((item) => {
        const max = Math.max(...(data.costByType || []).map((x) => Number(x.amount || 0)), 1);
        const width = Math.round(Number(item.amount || 0) / max * 100);
        return `<div class="cost-bar-row">
            <span class="name">${esc(COST_LABELS[item.costType] || item.costType)}</span>
            <span class="bar"><span style="width:${width}%"></span></span>
            <span class="num">${fmtMoney(item.amount)}</span>
        </div>`;
    }).join('');

    const chain = data.chain || {};
    const chainChips = Object.keys(chain).map((key) => `<div class="chip">${esc(key)}<strong>${esc(chain[key])}</strong></div>`).join('');

    el('content').innerHTML = `
        <div class="grid grid-4">
            ${metricCard('合同总额', fmtMoney(data.contractAmount), '已生效销售合同', 'green')}
            ${metricCard('项目数量', data.projectCount || 0, 'EPC + 运维项目', '')}
            ${metricCard('采购总额', fmtMoney(data.poAmount), '采购订单含税金额', '')}
            ${metricCard('已付款', fmtMoney(data.paidAmount), '累计付款记录', 'green')}
        </div>
        <div class="grid grid-4 section">
            ${metricCard('应付余额', fmtMoney(data.payableOpen), '未付 + 部分付款', 'orange')}
            ${metricCard('未处理告警', data.alarmOpen || 0, 'OPEN 状态告警', 'red')}
            ${metricCard('在办工单', data.workOrderOpen || 0, '待派单/处理中', 'orange')}
            ${metricCard('业务链数据', Object.values(chain).reduce((a, b) => a + Number(b || 0), 0), '五条链路累计单据', '')}
        </div>
        <div class="grid grid-main section">
            <div class="card">
                <div class="section-head">
                    <div class="section-title">最近项目</div>
                    <button class="small" onclick="switchPage('pm')">全部项目</button>
                </div>
                ${table(['项目编号', '项目名称', '客户', '预算', '成本', '状态'], data.recentProjects || [], (p) => `
                    <tr>
                        <td class="mono">${esc(p.code)}</td>
                        <td>${esc(p.name)}</td>
                        <td>${esc(p.customerName || '-')}</td>
                        <td class="money">${fmtMoney(p.budgetAmount)}</td>
                        <td class="money">${fmtMoney(p.costTotal)}</td>
                        <td>${badge(p.status)}</td>
                    </tr>`)}
            </div>
            <div class="card">
                <div class="section-head"><div class="section-title">项目成本结构</div></div>
                <div class="cost-bars">${costRows || '<div class="empty">暂无成本数据</div>'}</div>
                <div class="section-head" style="margin-top:22px"><div class="section-title">业务链累计</div></div>
                <div class="chips">${chainChips}</div>
            </div>
        </div>
        <div class="card section">
            <div class="section-head">
                <div class="section-title">最近采购订单</div>
                <button class="small" onclick="switchPage('srm')">采购管理</button>
            </div>
            ${table(['订单号', '供应商', '项目', '金额', '下单日期', '状态'], data.recentPurchaseOrders || [], (po) => `
                <tr>
                    <td class="mono">${esc(po.code)}</td>
                    <td>${esc(po.supplierName || '-')}</td>
                    <td>${esc(po.projectName || '-')}</td>
                    <td class="money">${fmtMoney(po.totalAmount)}</td>
                    <td>${fmtDate(po.orderDate)}</td>
                    <td>${badge(po.status)}</td>
                </tr>`)}
        </div>
        <div class="card section hero">
            <h2>想一次看完所有业务链？</h2>
            <p>点击一键演示，系统会自动创建客户、线索、合同、项目、采购订单、收货、对账、发票、应付、付款、领料成本、并网、设备故障告警与工单，并把每一步结果展示出来。</p>
            <div class="hero-actions"><button class="primary" onclick="switchPage('demo')">进入一键演示</button></div>
        </div>
    `;
}

function metricCard(label, value, sub, color) {
    return `<div class="card metric">
        <div class="metric-label">${esc(label)}</div>
        <div class="metric-value ${color}">${typeof value === 'number' ? fmtNum(value, 0) : esc(value)}</div>
        <div class="metric-sub">${esc(sub)}</div>
    </div>`;
}

/* ---------------- 一键演示 ---------------- */
async function renderDemo() {
    const result = state.demoResult;
    const chainCards = [
        ['L2C', '线索 → 商机 → 合同 → 立项'],
        ['P2P', '采购 → 收货 → 对账 → 发票 → 应付 → 付款'],
        ['COST', '领料 → 人工/分包 → 项目成本'],
        ['O&M', '并网 → 遥测 → 告警 → 工单']
    ].map(([code, text]) => `<div class="card metric"><div class="metric-label">${esc(code)}</div><div class="metric-value" style="font-size:18px">${esc(text)}</div><div class="metric-sub">一键演示覆盖</div></div>`).join('');

    let logHtml = `<div class="empty">点击上方“开始演示”，系统会从零创建一条完整业务链。</div>`;
    let summaryHtml = '';
    if (result) {
        const steps = result.steps || [];
        logHtml = `<div class="timeline">${steps.map((step) => {
            const cls = step.status === 'FAIL' ? 'fail' : (step.status === 'SKIP' ? 'skip' : '');
            return `<div class="timeline-item ${cls}">
                <strong>${esc(step.name)}</strong><em>${esc(step.status)}</em>
                <p>${esc(step.detail || '')}</p>
            </div>`;
        }).join('')}</div>`;
        if (result.summary) {
            const s = result.summary;
            summaryHtml = `<div class="grid grid-4 section">
                ${metricCard('合同金额', fmtMoney(s.contractAmount), s.contractCode, 'green')}
                ${metricCard('采购金额', fmtMoney(s.poAmount), s.poCode, '')}
                ${metricCard('项目成本', fmtMoney(s.projectCost), s.projectCode, 'orange')}
                ${metricCard('毛利', fmtMoney(s.grossMargin), '项目毛利', 'green')}
            </div>
            <div class="card section">
                <div class="section-head"><div class="section-title">演示结果摘要</div></div>
                <div class="split">
                    <div>
                        ${line('客户编码', s.customerCode)}
                        ${line('合同编码', s.contractCode)}
                        ${line('项目编码', s.projectCode)}
                        ${line('采购订单', s.poCode)}
                        ${line('收货单', s.receiptCode)}
                        ${line('对账单', s.reconciliationCode)}
                    </div>
                    <div>
                        ${line('发票', s.invoiceCode)}
                        ${line('应付单', s.payableCode)}
                        ${line('付款单', s.paymentCode)}
                        ${line('已付金额', fmtMoney(s.paidAmount))}
                        ${line('电站', s.stationCode)}
                        ${line('告警/工单', `${s.alarmCount} / ${s.workOrderCount}`)}
                    </div>
                </div>
            </div>`;
        }
    }

    el('content').innerHTML = `
        <div class="card hero">
            <h2>一键跑通全业务链</h2>
            <p>演示会真实写入 H2 数据库：CRM 客户/线索/商机/合同 → PM 立项/WBS/里程碑 → SRM 采购 → INV 收货/领料 → FIN 对账/发票/三单匹配/应付 → FUND 付款 → PM 成本/并网 → IoT 故障告警 → O&M 工单处理 → PM 结算。</p>
            <div class="hero-actions">
                <button class="primary" onclick="runDemo()" ${state.demoRunning ? 'disabled' : ''}>${state.demoRunning ? '演示执行中...' : '开始演示'}</button>
                <button class="ghost" onclick="switchPage('dashboard')">回到驾驶舱</button>
            </div>
        </div>
        <div class="grid grid-4 section">${chainCards}</div>
        <div class="card section">
            <div class="section-head">
                <div class="section-title">执行轨迹</div>
                <span class="section-sub">${result ? (result.success ? '全部步骤完成' : '演示中断') : '等待执行'}</span>
            </div>
            ${logHtml}
        </div>
        ${summaryHtml}
    `;
}

function line(label, value) {
    return `<div class="list-line"><span>${esc(label)}</span><strong>${esc(value === null || value === undefined ? '-' : value)}</strong></div>`;
}

async function runDemo() {
    if (state.demoRunning) return;
    state.demoRunning = true;
    state.demoResult = null;
    renderDemo();
    try {
        const data = await api('/api/demo/run', { method: 'POST' });
        state.demoResult = data;
        toast(data.success ? '全业务链演示完成' : ('演示中断：' + data.message), data.success ? 'success' : 'error');
    } catch (e) {
        toast(e.message, 'error');
    } finally {
        state.demoRunning = false;
        renderDemo();
    }
}

/* ---------------- CRM ---------------- */
async function renderCrm() {
    const [customers, leads, opportunities, contracts] = await Promise.all([
        api('/api/crm/customers'),
        api('/api/crm/leads'),
        api('/api/crm/opportunities'),
        api('/api/crm/contracts')
    ]);
    state.cache.customers = customers;
    state.cache.opportunities = opportunities;

    el('content').innerHTML = `
        <div class="card">
            <div class="section-head">
                <div><div class="section-title">客户档案</div><span class="section-sub">共 ${customers.length} 个客户</span></div>
                <div class="actions">
                    <button class="ghost" onclick="newLead()">登记线索</button>
                    <button class="primary" onclick="newCustomer()">新建客户</button>
                </div>
            </div>
            ${table(['客户编码', '客户名称', '类型', '区域', '联系人', '负责人', '状态'], customers, (c) => `
                <tr>
                    <td class="mono">${esc(c.code)}</td>
                    <td><strong>${esc(c.name)}</strong></td>
                    <td>${esc(c.type)}</td>
                    <td>${esc(c.region || '-')}</td>
                    <td>${esc(c.contactName || '-')}<br><span class="muted">${esc(c.contactPhone || '')}</span></td>
                    <td>${esc(c.ownerName || '-')}</td>
                    <td>${badge(c.status)}</td>
                </tr>`)}
        </div>

        <div class="split section">
            <div class="card">
                <div class="section-head">
                    <div><div class="section-title">线索</div><span class="section-sub">${leads.length} 条</span></div>
                </div>
                ${table(['线索', '客户', '来源', '预估金额', '阶段', '操作'], leads, (l) => `
                    <tr>
                        <td>${esc(l.name)}</td>
                        <td>${esc(l.customerName || '-')}</td>
                        <td>${esc(l.source || '-')}</td>
                        <td class="money">${fmtMoney(l.estimatedAmount)}</td>
                        <td>${badge(l.stage)}</td>
                        <td class="actions-cell">${l.stage === 'CONVERTED' ? '<span class="muted">已转化</span>' : `<button class="small" onclick="convertLead(${l.id})">转商机</button>`}</td>
                    </tr>`)}
            </div>
            <div class="card">
                <div class="section-head">
                    <div><div class="section-title">商机</div><span class="section-sub">${opportunities.length} 个</span></div>
                </div>
                ${table(['商机', '客户', '金额', '赢率', '阶段'], opportunities, (o) => `
                    <tr>
                        <td>${esc(o.name)}</td>
                        <td>${esc(o.customerName || '-')}</td>
                        <td class="money">${fmtMoney(o.amount)}</td>
                        <td>${fmtNum(o.winRate, 0)}%</td>
                        <td>${badge(o.stage)}</td>
                    </tr>`)}
            </div>
        </div>

        <div class="card section">
            <div class="section-head">
                <div><div class="section-title">销售合同</div><span class="section-sub">${contracts.length} 份合同</span></div>
                <button class="primary" onclick="newContract()">新建合同</button>
            </div>
            ${table(['合同编码', '合同名称', '客户', '金额', '签订日期', '项目', '状态', '操作'], contracts, (c) => `
                <tr>
                    <td class="mono">${esc(c.code)}</td>
                    <td>${esc(c.name)}</td>
                    <td>${esc(c.customerName || '-')}</td>
                    <td class="money">${fmtMoney(c.amount)}</td>
                    <td>${fmtDate(c.signDate)}</td>
                    <td>${c.projectCode ? `<span class="mono">${esc(c.projectCode)}</span>` : '<span class="muted">未立项</span>'}</td>
                    <td>${badge(c.status)}</td>
                    <td class="actions-cell">${c.projectId ? `<button class="small" onclick="viewProject(${c.projectId})">查看项目</button>` : `<button class="small" onclick="createProjectFromContract(${c.id})">合同立项</button>`}</td>
                </tr>`)}
        </div>
    `;
}

function newCustomer() {
    openModal('新建客户', [
        { name: 'name', label: '客户名称', value: '演示客户-' + Date.now() },
        { name: 'type', label: '客户类型', type: 'select', options: [
            { value: 'ROOFTOP_OWNER', label: '屋顶业主' },
            { value: 'INVESTOR', label: '投资商' },
            { value: 'EPC', label: 'EPC 合作方' },
            { value: 'PARTNER', label: '渠道伙伴' }
        ]},
        { name: 'region', label: '区域', value: '江苏苏州' },
        { name: 'contactName', label: '联系人', value: '张经理' },
        { name: 'contactPhone', label: '联系电话', value: '13900000000' }
    ], async (values) => {
        await api('/api/crm/customers', { method: 'POST', body: values });
        toast('客户创建成功', 'success');
        await renderCrm();
    });
}

function newLead() {
    const customers = state.cache.customers || [];
    if (!customers.length) {
        toast('请先创建客户', 'error');
        return;
    }
    openModal('登记线索', [
        { name: 'name', label: '线索名称', value: '屋顶光伏线索-' + Date.now() },
        { name: 'customerId', label: '关联客户', type: 'select', options: optionsOf(customers) },
        { name: 'source', label: '线索来源', value: '官网咨询' },
        { name: 'estimatedAmount', label: '预估金额（元）', type: 'number', value: 4200000 },
        { name: 'estimatedCapacityMw', label: '预估容量（MW）', type: 'number', value: 1 }
    ], async (values) => {
        await api('/api/crm/leads', { method: 'POST', body: values });
        toast('线索登记成功', 'success');
        await renderCrm();
    });
}

async function convertLead(id) {
    try {
        await api(`/api/crm/leads/${id}/convert`, { method: 'POST' });
        toast('线索已转商机', 'success');
        await renderCrm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

function newContract() {
    const customers = state.cache.customers || [];
    const opportunities = state.cache.opportunities || [];
    if (!customers.length) {
        toast('请先创建客户', 'error');
        return;
    }
    const oppOptions = [{ value: '', label: '不关联商机' }, ...optionsOf(opportunities)];
    openModal('新建销售合同', [
        { name: 'name', label: '合同名称', value: '屋顶分布式光伏 EPC 合同-' + Date.now() },
        { name: 'customerId', label: '客户', type: 'select', options: optionsOf(customers) },
        { name: 'opportunityId', label: '关联商机', type: 'select', options: oppOptions },
        { name: 'amount', label: '合同金额（元）', type: 'number', value: 4200000 }
    ], async (values) => {
        if (!values.opportunityId) delete values.opportunityId;
        await api('/api/crm/contracts', { method: 'POST', body: values });
        toast('合同创建成功', 'success');
        await renderCrm();
    });
}

async function createProjectFromContract(contractId) {
    try {
        const project = await api(`/api/pm/projects/from-contract/${contractId}`, { method: 'POST' });
        toast('项目立项成功：' + project.code, 'success');
        state.selectedProject = project.id;
        switchPage('pm', true);
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ---------------- PM ---------------- */
async function renderPm() {
    const projects = await api('/api/pm/projects');
    let detail = null;
    if (state.selectedProject) {
        detail = await api(`/api/pm/projects/${state.selectedProject}`);
    }
    let detailHtml = '';
    if (detail) {
        const cost = detail.costSummary || {};
        const costItems = detail.costItems || [];
        detailHtml = `
            <div class="card section">
                <div class="section-head">
                    <div>
                        <div class="section-title">${esc(detail.code)} · ${esc(detail.name)}</div>
                        <span class="section-sub">${esc(detail.customerName || '-')} · ${esc(detail.managerName || '-')} · ${esc(detail.capacityMw)} MW</span>
                    </div>
                    <button class="ghost" onclick="state.selectedProject=null;renderPm()">收起</button>
                </div>
                <div class="grid grid-4">
                    ${metricCard('项目预算', fmtMoney(detail.budgetAmount), '预算金额', '')}
                    ${metricCard('累计成本', fmtMoney(cost.totalCost), '材料/人工/分包/其他', 'orange')}
                    ${metricCard('合同金额', fmtMoney(detail.contractAmount), detail.contractCode || '-', 'green')}
                    ${metricCard('项目毛利', fmtMoney(cost.grossMargin), '毛利率 ' + fmtNum(cost.grossRate, 2) + '%', 'green')}
                </div>
                <div class="section-head" style="margin-top:18px">
                    <div class="section-title">当前进度</div>
                    <span class="section-sub">${detail.progress}% · ${badge(detail.status)}</span>
                </div>
                <div class="progress" style="height:10px"><span style="width:${detail.progress}%"></span></div>
                <div class="actions" style="margin-top:16px">
                    <button class="small" onclick="updateProgress(${detail.id}, ${detail.progress})">进度 +10%</button>
                    <button class="small" onclick="changeOrder(${detail.id})">变更签证</button>
                    <button class="small" onclick="addCost(${detail.id})">归集成本</button>
                    ${detail.actualGridDate
                        ? '<button class="small" disabled>已并网</button>'
                        : `<button class="small" onclick="gridConnect(${detail.id})">并网登记</button>`}
                    <button class="small" onclick="settleProject(${detail.id})">项目结算</button>
                </div>
            </div>
            <div class="split section">
                <div class="card">
                    <div class="section-head"><div class="section-title">WBS 与预算</div></div>
                    ${table(['WBS', '名称', '预算'], detail.wbs || [], (w) => `
                        <tr><td class="mono">${esc(w.code)}</td><td>${esc(w.name)}</td><td class="money">${fmtMoney(w.budgetAmount)}</td></tr>`)}
                </div>
                <div class="card">
                    <div class="section-head"><div class="section-title">里程碑</div></div>
                    ${table(['里程碑', '计划日期', '实际日期', '状态'], detail.milestones || [], (m) => `
                        <tr><td>${esc(m.name)}</td><td>${fmtDate(m.planDate)}</td><td>${fmtDate(m.actualDate)}</td><td>${badge(m.status)}</td></tr>`)}
                </div>
            </div>
            <div class="split section">
                <div class="card">
                    <div class="section-head"><div class="section-title">成本明细</div></div>
                    ${table(['类型', '金额', '发生日期', '备注'], costItems, (c) => `
                        <tr><td>${esc(COST_LABELS[c.costType] || c.costType)}</td><td class="money">${fmtMoney(c.amount)}</td><td>${fmtDate(c.occurredDate)}</td><td>${esc(c.remark || '-')}</td></tr>`)}
                </div>
                <div class="card">
                    <div class="section-head"><div class="section-title">变更签证</div></div>
                    ${table(['变更单', '类型', '金额增减', '工期', '状态'], detail.changeOrders || [], (c) => `
                        <tr><td class="mono">${esc(c.code)}</td><td>${esc(c.type)}</td><td class="money">${fmtMoney(c.amountDelta)}</td><td>${c.scheduleDeltaDays || 0} 天</td><td>${badge(c.status)}</td></tr>`)}
                </div>
            </div>
        `;
    }

    el('content').innerHTML = `
        <div class="card">
            <div class="section-head">
                <div><div class="section-title">项目列表</div><span class="section-sub">共 ${projects.length} 个项目</span></div>
                <button class="primary" onclick="switchPage('crm')">从合同立项</button>
            </div>
            ${table(['项目编码', '项目名称', '客户', '容量', '进度', '预算', '成本', '状态', '操作'], projects, (p) => `
                <tr>
                    <td class="mono">${esc(p.code)}</td>
                    <td><strong>${esc(p.name)}</strong><br><span class="muted">${esc(p.managerName || '')}</span></td>
                    <td>${esc(p.customerName || '-')}</td>
                    <td>${fmtNum(p.capacityMw, 2)} MW</td>
                    <td><div class="flex"><div class="progress"><span style="width:${p.progress}%"></span></div><span class="muted">${p.progress}%</span></div></td>
                    <td class="money">${fmtMoney(p.budgetAmount)}</td>
                    <td class="money">${fmtMoney(p.costTotal)}</td>
                    <td>${badge(p.status)}</td>
                    <td class="actions-cell"><button class="small" onclick="viewProject(${p.id})">查看</button></td>
                </tr>`)}
        </div>
        ${detailHtml}
    `;
}

function viewProject(id) {
    state.selectedProject = id;
    renderPm();
}

async function updateProgress(id, current) {
    try {
        const next = Math.min(100, Number(current || 0) + 10);
        await api(`/api/pm/projects/${id}/progress`, { method: 'POST', body: { percent: next } });
        toast('项目进度更新为 ' + next + '%', 'success');
        await renderPm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

function changeOrder(id) {
    openModal('新增变更签证', [
        { name: 'type', label: '变更类型', type: 'select', options: ['DESIGN', 'QUANTITY', 'SCHEDULE', 'OTHER'] },
        { name: 'amountDelta', label: '金额增减（元）', type: 'number', value: 80000 },
        { name: 'scheduleDeltaDays', label: '工期影响（天）', type: 'number', value: 2 },
        { name: 'reason', label: '变更原因', type: 'textarea', value: '屋面加固及监控点位增加' }
    ], async (values) => {
        await api(`/api/pm/projects/${id}/change-orders`, { method: 'POST', body: values });
        toast('变更签证已审批生效', 'success');
        await renderPm();
    });
}

function addCost(id) {
    openModal('归集项目成本', [
        { name: 'costType', label: '成本类型', type: 'select', options: [
            { value: 'LABOR', label: '人工成本' },
            { value: 'SUBCONTRACT', label: '分包成本' },
            { value: 'MACHINERY', label: '机械成本' },
            { value: 'OTHER', label: '其他费用' }
        ]},
        { name: 'amount', label: '金额（元）', type: 'number', value: 100000 },
        { name: 'remark', label: '备注', value: '演示成本归集' }
    ], async (values) => {
        await api(`/api/pm/projects/${id}/costs`, { method: 'POST', body: values });
        toast('成本已归集', 'success');
        await renderPm();
    });
}

async function gridConnect(id) {
    try {
        const data = await api(`/api/pm/projects/${id}/grid-connection`, { method: 'POST', body: {} });
        toast('并网登记成功，电站：' + data.station.code, 'success');
        await renderPm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function settleProject(id) {
    try {
        await api(`/api/pm/projects/${id}/settle`, { method: 'POST' });
        toast('项目已结算', 'success');
        await renderPm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ---------------- SRM ---------------- */
async function renderSrm() {
    const [suppliers, materials, purchaseOrders, projects] = await Promise.all([
        api('/api/srm/suppliers'),
        api('/api/srm/materials'),
        api('/api/srm/purchase-orders'),
        api('/api/pm/projects')
    ]);
    state.cache.suppliers = suppliers;
    state.cache.materials = materials;
    state.cache.projects = projects;

    el('content').innerHTML = `
        <div class="card">
            <div class="section-head">
                <div><div class="section-title">采购订单</div><span class="section-sub">${purchaseOrders.length} 张订单</span></div>
                <button class="primary" onclick="newPurchaseOrder()">新建采购订单</button>
            </div>
            ${table(['订单号', '项目', '供应商', '金额', '下单日期', '状态', '操作'], purchaseOrders, (po) => {
                let actions = '';
                if (po.status === 'DRAFT' || po.status === 'APPROVING') {
                    actions += `<button class="small" onclick="approvePo(${po.id})">审批</button>`;
                }
                if (po.status === 'APPROVED') {
                    actions += `<button class="small" onclick="deliverPo(${po.id})">发货</button>`;
                    actions += `<button class="small" onclick="receivePo(${po.id})">收货</button>`;
                }
                if (po.status === 'PARTIAL_RECEIVED') {
                    actions += `<button class="small" onclick="receivePo(${po.id})">继续收货</button>`;
                }
                if (!actions) actions = '<span class="muted">已完成</span>';
                return `<tr>
                    <td class="mono">${esc(po.code)}</td>
                    <td>${esc(po.projectName || '-')}</td>
                    <td>${esc(po.supplierName || '-')}</td>
                    <td class="money">${fmtMoney(po.totalAmount)}</td>
                    <td>${fmtDate(po.orderDate)}</td>
                    <td>${badge(po.status)}</td>
                    <td class="actions-cell">${actions}</td>
                </tr>`;
            })}
        </div>

        <div class="split section">
            <div class="card">
                <div class="section-head"><div class="section-title">供应商</div><span class="section-sub">${suppliers.length} 家</span></div>
                ${table(['编码', '名称', '类型', '等级', '状态'], suppliers, (s) => `
                    <tr><td class="mono">${esc(s.code)}</td><td>${esc(s.name)}</td><td>${esc(s.type)}</td><td>${esc(s.level)}</td><td>${badge(s.status)}</td></tr>`)}
            </div>
            <div class="card">
                <div class="section-head"><div class="section-title">物料主数据</div><span class="section-sub">${materials.length} 个</span></div>
                ${table(['编码', '名称', '分类', '单位'], materials, (m) => `
                    <tr><td class="mono">${esc(m.code)}</td><td>${esc(m.name)}</td><td>${esc(m.category)}</td><td>${esc(m.unit)}</td></tr>`)}
            </div>
        </div>
    `;
}

function newPurchaseOrder() {
    const projects = state.cache.projects || [];
    const suppliers = state.cache.suppliers || [];
    const materials = state.cache.materials || [];
    if (!projects.length || !suppliers.length || !materials.length) {
        toast('主数据未加载完成，请刷新页面', 'error');
        return;
    }
    openModal('新建采购订单', [
        { name: 'projectId', label: '项目', type: 'select', options: optionsOf(projects) },
        { name: 'supplierId', label: '供应商', type: 'select', options: optionsOf(suppliers) },
        { name: 'materialId', label: '物料', type: 'select', options: materials.map((m) => ({ value: m.id, label: m.name + '（' + m.unit + '）' })) },
        { name: 'qty', label: '数量', type: 'number', value: 1000 },
        { name: 'unitPrice', label: '含税单价（元）', type: 'number', value: 850 },
        { name: 'deliveryDate', label: '要求到货日期', type: 'date', value: new Date(Date.now() + 15 * 86400000).toISOString().slice(0, 10) }
    ], async (values) => {
        const body = {
            projectId: values.projectId,
            supplierId: values.supplierId,
            deliveryDate: values.deliveryDate,
            lines: [{ materialId: values.materialId, qty: values.qty, unitPrice: values.unitPrice }]
        };
        const po = await api('/api/srm/purchase-orders', { method: 'POST', body });
        toast('采购订单已创建：' + po.code, 'success');
        await renderSrm();
    });
}

async function approvePo(id) {
    try {
        await api(`/api/srm/purchase-orders/${id}/approve`, { method: 'POST' });
        toast('采购订单已审批', 'success');
        await renderSrm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function deliverPo(id) {
    try {
        const data = await api(`/api/srm/purchase-orders/${id}/deliver`, { method: 'POST' });
        toast('供应商已发货：' + data.code, 'success');
        await renderSrm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function receivePo(id) {
    try {
        const receipt = await api('/api/inv/receipts', { method: 'POST', body: { poId: id, warehouseId: 2 } });
        toast('收货入库成功：' + receipt.code, 'success');
        await renderSrm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ---------------- 库存 ---------------- */
async function renderInv() {
    const [stocks, receipts, movements, warehouses, projects, materials] = await Promise.all([
        api('/api/inv/stocks'),
        api('/api/inv/receipts'),
        api('/api/inv/movements'),
        api('/api/inv/warehouses'),
        api('/api/pm/projects'),
        api('/api/srm/materials')
    ]);
    state.cache.warehouses = warehouses;
    state.cache.projects = projects;
    state.cache.materials = materials;

    el('content').innerHTML = `
        <div class="card">
            <div class="section-head">
                <div><div class="section-title">库存台账</div><span class="section-sub">${stocks.length} 条库存</span></div>
                <div class="actions">
                    <button class="ghost" onclick="openReceiveModal()">采购收货</button>
                    <button class="primary" onclick="openIssueModal()">项目领料</button>
                </div>
            </div>
            ${table(['仓库', '物料编码', '物料名称', '数量', '平均单价', '库存金额'], stocks, (s) => `
                <tr>
                    <td>${esc(s.warehouseName || '-')}</td>
                    <td class="mono">${esc(s.materialCode || '-')}</td>
                    <td>${esc(s.materialName || '-')}</td>
                    <td>${fmtNum(s.qty, 2)} ${esc(s.unit || '')}</td>
                    <td class="money">${fmtMoney(s.avgCost)}</td>
                    <td class="money">${fmtMoney(s.amount)}</td>
                </tr>`)}
        </div>

        <div class="split section">
            <div class="card">
                <div class="section-head"><div class="section-title">收货单</div><span class="section-sub">${receipts.length} 张</span></div>
                ${table(['收货单', '采购订单', '项目', '金额', '状态'], receipts, (r) => `
                    <tr><td class="mono">${esc(r.code)}</td><td class="mono">${esc(r.poCode || '-')}</td><td>${esc(r.projectName || '-')}</td><td class="money">${fmtMoney(r.totalAmount)}</td><td>${badge(r.status)}</td></tr>`)}
            </div>
            <div class="card">
                <div class="section-head"><div class="section-title">库存流水</div><span class="section-sub">最近 ${Math.min(movements.length, 20)} 条</span></div>
                ${table(['时间', '类型', '物料', '数量', '金额'], movements.slice(0, 20), (m) => `
                    <tr><td>${fmtDate(m.occurredAt)}</td><td>${esc(m.bizType)}</td><td>${esc(m.materialName || '-')}</td><td>${fmtNum(m.qtyDelta, 2)}</td><td class="money">${fmtMoney(m.amount)}</td></tr>`)}
            </div>
        </div>
    `;
}

async function openReceiveModal() {
    try {
        const pos = await api('/api/srm/purchase-orders');
        const available = pos.filter((po) => po.status === 'APPROVED' || po.status === 'PARTIAL_RECEIVED');
        if (!available.length) {
            toast('没有可收货的采购订单，请先审批采购订单', 'error');
            return;
        }
        openModal('采购收货入库', [
            { name: 'poId', label: '采购订单', type: 'select', options: available.map((po) => ({ value: po.id, label: po.code + ' · ' + po.supplierName })) },
            { name: 'warehouseId', label: '收货仓库', type: 'select', options: optionsOf(state.cache.warehouses || []) }
        ], async (values) => {
            const receipt = await api('/api/inv/receipts', { method: 'POST', body: values });
            toast('收货入库成功：' + receipt.code, 'success');
            await renderInv();
        });
    } catch (e) {
        toast(e.message, 'error');
    }
}

function openIssueModal() {
    const projects = state.cache.projects || [];
    const warehouses = state.cache.warehouses || [];
    const materials = state.cache.materials || [];
    if (!projects.length || !warehouses.length || !materials.length) {
        toast('主数据未加载完成，请刷新页面', 'error');
        return;
    }
    openModal('项目领料出库', [
        { name: 'projectId', label: '项目', type: 'select', options: optionsOf(projects) },
        { name: 'warehouseId', label: '出库仓库', type: 'select', options: optionsOf(warehouses) },
        { name: 'materialId', label: '物料', type: 'select', options: materials.map((m) => ({ value: m.id, label: m.name + '（' + m.unit + '）' })) },
        { name: 'qty', label: '领用数量', type: 'number', value: 100 }
    ], async (values) => {
        const body = {
            projectId: values.projectId,
            wbsId: 1,
            warehouseId: values.warehouseId,
            lines: [{ materialId: values.materialId, qty: values.qty }]
        };
        const issue = await api('/api/inv/issues', { method: 'POST', body });
        toast('领料出库成功：' + issue.code, 'success');
        await renderInv();
    });
}

/* ---------------- 财务结算 ---------------- */
async function renderFin() {
    const [reconciliations, invoices, payables, suppliers, projects] = await Promise.all([
        api('/api/fin/reconciliations'),
        api('/api/fin/invoices'),
        api('/api/fin/payables'),
        api('/api/srm/suppliers'),
        api('/api/pm/projects')
    ]);
    state.cache.suppliers = suppliers;
    state.cache.projects = projects;
    state.cache.reconciliations = reconciliations;

    el('content').innerHTML = `
        <div class="card">
            <div class="section-head">
                <div><div class="section-title">供应商对账</div><span class="section-sub">${reconciliations.length} 张对账单</span></div>
                <button class="primary" onclick="generateReconciliationModal()">生成对账单</button>
            </div>
            ${table(['对账单', '供应商', '项目', '期间', '金额', '状态', '操作'], reconciliations, (r) => {
                const canConfirm = ['DRAFT', 'SUPPLIER_CONFIRMED', 'BUYER_CONFIRMED'].includes(r.status);
                return `<tr>
                    <td class="mono">${esc(r.code)}</td>
                    <td>${esc(r.supplierName || '-')}</td>
                    <td>${esc(r.projectName || '-')}</td>
                    <td>${esc(r.period)}</td>
                    <td class="money">${fmtMoney(r.totalAmount)}</td>
                    <td>${badge(r.status)}</td>
                    <td class="actions-cell">${canConfirm ? `<button class="small" onclick="confirmRec(${r.id})">确认</button>` : '<span class="muted">-</span>'}</td>
                </tr>`;
            })}
        </div>

        <div class="split section">
            <div class="card">
                <div class="section-head">
                    <div><div class="section-title">发票与三单匹配</div><span class="section-sub">${invoices.length} 张发票</span></div>
                    <button class="ghost" onclick="registerInvoiceModal()">登记发票</button>
                </div>
                ${table(['发票登记号', '发票号', '供应商', '金额', '状态', '操作'], invoices, (i) => {
                    const canMatch = i.status === 'PENDING';
                    return `<tr>
                        <td class="mono">${esc(i.code)}</td>
                        <td class="mono">${esc(i.invoiceNo)}</td>
                        <td>${esc(i.supplierName || '-')}</td>
                        <td class="money">${fmtMoney(i.amount)}</td>
                        <td>${badge(i.status)}</td>
                        <td class="actions-cell">${canMatch ? `<button class="small" onclick="matchInvoice(${i.id})">三单匹配</button>` : '<span class="muted">-</span>'}</td>
                    </tr>`;
                })}
            </div>
            <div class="card">
                <div class="section-head"><div class="section-title">应付账款</div><span class="section-sub">${payables.length} 条</span></div>
                ${table(['应付单', '供应商', '项目', '应付', '已付', '可用', '状态', '操作'], payables, (p) => {
                    const canPay = ['OPEN', 'PARTIAL'].includes(p.status) && Number(p.availableAmount) > 0;
                    return `<tr>
                        <td class="mono">${esc(p.code)}</td>
                        <td>${esc(p.supplierName || '-')}</td>
                        <td>${esc(p.projectName || '-')}</td>
                        <td class="money">${fmtMoney(p.amount)}</td>
                        <td class="money">${fmtMoney(p.paidAmount)}</td>
                        <td class="money">${fmtMoney(p.availableAmount)}</td>
                        <td>${badge(p.status)}</td>
                        <td class="actions-cell">${canPay ? `<button class="small" onclick="payPayable(${p.id}, ${Number(p.availableAmount)})">付款</button>` : '<span class="muted">-</span>'}</td>
                    </tr>`;
                })}
            </div>
        </div>
    `;
}

function generateReconciliationModal() {
    const suppliers = state.cache.suppliers || [];
    const projects = state.cache.projects || [];
    if (!suppliers.length || !projects.length) {
        toast('主数据未加载完成，请刷新页面', 'error');
        return;
    }
    openModal('生成供应商对账单', [
        { name: 'supplierId', label: '供应商', type: 'select', options: optionsOf(suppliers) },
        { name: 'projectId', label: '项目', type: 'select', options: optionsOf(projects) },
        { name: 'period', label: '对账期间', value: new Date().toISOString().slice(0, 7) }
    ], async (values) => {
        const rec = await api('/api/fin/reconciliations/generate', { method: 'POST', body: values });
        toast('对账单已生成：' + rec.code, 'success');
        await renderFin();
    });
}

async function confirmRec(id) {
    try {
        await api(`/api/fin/reconciliations/${id}/confirm`, { method: 'POST' });
        toast('对账单已确认', 'success');
        await renderFin();
    } catch (e) {
        toast(e.message, 'error');
    }
}

function registerInvoiceModal() {
    const recs = state.cache.reconciliations || [];
    if (!recs.length) {
        toast('请先生成对账单', 'error');
        return;
    }
    openModal('登记供应商发票', [
        { name: 'reconciliationId', label: '关联对账单', type: 'select', options: recs.map((r) => ({ value: r.id, label: r.code + ' · ' + fmtMoney(r.totalAmount) })) },
        { name: 'invoiceNo', label: '发票号码', value: 'FP' + Date.now() },
        { name: 'amount', label: '发票金额（元，留空取对账金额）', type: 'number', value: '' }
    ], async (values) => {
        if (!values.amount) delete values.amount;
        const invoice = await api('/api/fin/invoices', { method: 'POST', body: values });
        toast('发票登记成功：' + invoice.code, 'success');
        await renderFin();
    });
}

async function matchInvoice(id) {
    try {
        const data = await api(`/api/fin/invoices/${id}/match`, { method: 'POST' });
        toast(data.message || '三单匹配完成', data.matchResult === 'MATCHED' ? 'success' : 'error');
        await renderFin();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function payPayable(id, amount) {
    try {
        const request = await api('/api/fund/payment-requests', {
            method: 'POST',
            body: { payableId: id, amount, accountId: 1, paymentType: 'PROGRESS' }
        });
        await api(`/api/fund/payment-requests/${request.id}/approve`, { method: 'POST' });
        const payment = await api(`/api/fund/payment-requests/${request.id}/pay`, { method: 'POST' });
        toast('付款成功：' + payment.paymentCode, 'success');
        await renderFin();
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ---------------- 资金管理 ---------------- */
async function renderFund() {
    const [accounts, requests, payments] = await Promise.all([
        api('/api/fund/accounts'),
        api('/api/fund/payment-requests'),
        api('/api/fund/payments')
    ]);

    el('content').innerHTML = `
        <div class="grid grid-3">
            ${accounts.map((a) => `<div class="card metric">
                <div class="metric-label">${esc(a.bankName)} · ${esc(a.code)}</div>
                <div class="metric-value green">${fmtMoney(a.balance)}</div>
                <div class="metric-sub">${esc(a.name)} · ${esc(a.accountNo)}</div>
            </div>`).join('')}
        </div>
        <div class="card section">
            <div class="section-head"><div class="section-title">付款申请</div><span class="section-sub">${requests.length} 笔</span></div>
            ${table(['申请单', '应付单', '供应商', '项目', '金额', '账户', '状态', '操作'], requests, (q) => {
                let actions = '';
                if (q.status === 'APPROVING' || q.status === 'DRAFT') {
                    actions += `<button class="small" onclick="approvePayment(${q.id})">审批</button>`;
                }
                if (q.status === 'APPROVED') {
                    actions += `<button class="small" onclick="payPayment(${q.id})">付款</button>`;
                }
                if (!actions) actions = '<span class="muted">-</span>';
                return `<tr>
                    <td class="mono">${esc(q.code)}</td>
                    <td class="mono">${esc(q.payableCode || '-')}</td>
                    <td>${esc(q.supplierName || '-')}</td>
                    <td>${esc(q.projectName || '-')}</td>
                    <td class="money">${fmtMoney(q.amount)}</td>
                    <td>${esc(q.accountName || '-')}</td>
                    <td>${badge(q.status)}</td>
                    <td class="actions-cell">${actions}</td>
                </tr>`;
            })}
        </div>
        <div class="card section">
            <div class="section-head"><div class="section-title">付款记录</div><span class="section-sub">${payments.length} 笔</span></div>
            ${table(['付款单', '付款申请', '账户', '金额', '付款时间', '银行流水号', '状态'], payments, (p) => `
                <tr>
                    <td class="mono">${esc(p.code)}</td>
                    <td class="mono">${esc(p.requestCode || '-')}</td>
                    <td>${esc(p.accountName || '-')}</td>
                    <td class="money">${fmtMoney(p.amount)}</td>
                    <td>${fmtDate(p.paymentDate)}</td>
                    <td class="mono">${esc(p.bankSerialNo || '-')}</td>
                    <td>${badge(p.status)}</td>
                </tr>`)}
        </div>
    `;
}

async function approvePayment(id) {
    try {
        await api(`/api/fund/payment-requests/${id}/approve`, { method: 'POST' });
        toast('付款申请已审批', 'success');
        await renderFund();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function payPayment(id) {
    try {
        const payment = await api(`/api/fund/payment-requests/${id}/pay`, { method: 'POST' });
        toast('付款成功：' + payment.paymentCode, 'success');
        await renderFund();
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ---------------- 运维 O&M ---------------- */
async function renderOm() {
    const [stations, devices, alarms, workOrders] = await Promise.all([
        api('/api/om/stations'),
        api('/api/om/devices'),
        api('/api/om/alarms'),
        api('/api/om/work-orders')
    ]);

    el('content').innerHTML = `
        <div class="grid grid-4">
            ${stations.map((s) => `<div class="card metric">
                <div class="metric-label">${esc(s.code)}</div>
                <div class="metric-value" style="font-size:17px">${esc(s.name)}</div>
                <div class="metric-sub">${fmtNum(s.capacityMw, 2)} MW · 设备 ${s.deviceCount} · 未处理告警 ${s.openAlarmCount}</div>
            </div>`).join('')}
        </div>

        <div class="card section">
            <div class="section-head"><div class="section-title">告警列表</div><span class="section-sub">${alarms.length} 条</span></div>
            ${table(['告警编码', '电站/设备', '级别', '内容', '告警时间', '状态', '工单', '操作'], alarms, (a) => {
                let actions = '';
                if (a.status === 'OPEN') {
                    actions += `<button class="small" onclick="ackAlarm(${a.id})">确认</button>`;
                }
                if (!a.workOrderId) {
                    actions += `<button class="small" onclick="createWo(${a.id})">建工单</button>`;
                }
                if (!actions) actions = '<span class="muted">-</span>';
                return `<tr>
                    <td class="mono">${esc(a.code)}</td>
                    <td>${esc(a.stationName || '-')}<br><span class="muted">${esc(a.deviceCode || '')}</span></td>
                    <td>${badge(a.level)}</td>
                    <td>${esc(a.content)}</td>
                    <td>${fmtDate(a.alarmTime)}</td>
                    <td>${badge(a.status === 'OPEN' ? 'ALARM_OPEN' : a.status)}</td>
                    <td>${a.workOrderId ? `<span class="mono">#${a.workOrderId}</span>` : '-'}</td>
                    <td class="actions-cell">${actions}</td>
                </tr>`;
            })}
        </div>

        <div class="card section">
            <div class="section-head"><div class="section-title">运维工单</div><span class="section-sub">${workOrders.length} 张</span></div>
            ${table(['工单', '电站/设备', '标题', '优先级', '处理人', '计划结束', '状态', '操作'], workOrders, (w) => {
                let actions = '';
                if (w.status === 'CREATED') actions += `<button class="small" onclick="assignWo(${w.id})">派单</button>`;
                if (w.status === 'ASSIGNED' || w.status === 'PROCESSING') actions += `<button class="small" onclick="completeWoModal(${w.id})">完工</button>`;
                if (w.status === 'COMPLETED') actions += `<button class="small" onclick="closeWo(${w.id})">关闭</button>`;
                if (!actions) actions = '<span class="muted">-</span>';
                return `<tr>
                    <td class="mono">${esc(w.code)}</td>
                    <td>${esc(w.stationName || '-')}<br><span class="muted">${esc(w.deviceCode || '')}</span></td>
                    <td>${esc(w.title)}</td>
                    <td>${badge(w.priority)}</td>
                    <td>${esc(w.assigneeName || '-')}</td>
                    <td>${fmtDate(w.planEnd)}</td>
                    <td>${badge(w.status)}</td>
                    <td class="actions-cell">${actions}</td>
                </tr>`;
            })}
        </div>

        <div class="card section">
            <div class="section-head"><div class="section-title">设备台账</div><span class="section-sub">${devices.length} 台</span></div>
            ${table(['设备编码', '电站', '名称', '类型', '型号', '厂商', '状态', '最后在线'], devices, (d) => `
                <tr>
                    <td class="mono">${esc(d.code)}</td>
                    <td>${esc(d.stationName || '-')}</td>
                    <td>${esc(d.name)}</td>
                    <td>${esc(d.type)}</td>
                    <td>${esc(d.model || '-')}</td>
                    <td>${esc(d.manufacturer || '-')}</td>
                    <td>${badge(d.status)}</td>
                    <td>${fmtDate(d.lastOnlineAt)}</td>
                </tr>`)}
        </div>
    `;
}

async function ackAlarm(id) {
    try {
        await api(`/api/om/alarms/${id}/ack`, { method: 'POST' });
        toast('告警已确认', 'success');
        await renderOm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function createWo(alarmId) {
    try {
        const workOrder = await api('/api/om/work-orders', { method: 'POST', body: { alarmId, assigneeId: 9 } });
        toast('工单已创建：' + workOrder.code, 'success');
        await renderOm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

function assignWo(id) {
    openModal('工单派单', [
        { name: 'assigneeId', label: '处理人', type: 'select', options: [
            { value: 9, label: '张强（运维工程师）' },
            { value: 4, label: '王海涛（项目经理）' },
            { value: 8, label: '李娜（资金主管）' }
        ]}
    ], async (values) => {
        await api(`/api/om/work-orders/${id}/assign`, { method: 'POST', body: values });
        toast('工单已派单', 'success');
        await renderOm();
    });
}

function completeWoModal(id) {
    openModal('工单完工', [
        { name: 'description', label: '处理结果', type: 'textarea', value: '更换散热风扇并复测温度，设备恢复运行' },
        { name: 'costAmount', label: '工单成本（元）', type: 'number', value: 2800 }
    ], async (values) => {
        await api(`/api/om/work-orders/${id}/complete`, { method: 'POST', body: values });
        toast('工单已完工，告警自动清除', 'success');
        await renderOm();
    });
}

async function closeWo(id) {
    try {
        await api(`/api/om/work-orders/${id}/close`, { method: 'POST' });
        toast('工单已关闭', 'success');
        await renderOm();
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ---------------- IoT ---------------- */
async function renderIot() {
    const [status, telemetry, devices] = await Promise.all([
        api('/api/iot/status'),
        api('/api/iot/telemetry?limit=30'),
        api('/api/om/devices')
    ]);

    el('content').innerHTML = `
        <div class="grid grid-4">
            ${metricCard('MQTT Broker', status.mqttEnabled ? '运行中' : '未启用', '内置端口 ' + status.mqttPort, status.mqttEnabled ? 'green' : 'red')}
            ${metricCard('接入设备', status.deviceCount || 0, '电站设备台账', '')}
            ${metricCard('遥测数据', status.telemetryCount || 0, 'H2 本地时序表', 'green')}
            ${metricCard('未处理告警', status.openAlarmCount || 0, 'OPEN 状态', 'red')}
        </div>

        <div class="card section">
            <div class="section-head">
                <div><div class="section-title">设备模拟与遥测</div><span class="section-sub">点击按钮即可生成一条真实遥测，并触发告警/工单</span></div>
                <div class="actions">
                    <button class="ghost" onclick="simulateIot('normal')">模拟正常运行</button>
                    <button class="primary" onclick="simulateIot('fault')">模拟故障上报</button>
                </div>
            </div>
            ${table(['时间', '电站', '设备', '类型', '有功功率', '直流电压', '温度', '状态', '故障码'], telemetry, (t) => `
                <tr>
                    <td>${fmtDate(t.ts)}</td>
                    <td class="mono">${esc(t.stationCode || '-')}</td>
                    <td class="mono">${esc(t.deviceCode || '-')}</td>
                    <td>${esc(t.deviceType || '-')}</td>
                    <td class="money">${fmtNum(t.activePower, 2)} kW</td>
                    <td>${fmtNum(t.dcVoltage, 2)} V</td>
                    <td>${fmtNum(t.temperature, 2)} ℃</td>
                    <td>${badge(t.status)}</td>
                    <td class="mono">${esc(t.faultCode || '-')}</td>
                </tr>`)}
        </div>

        <div class="split section">
            <div class="card">
                <div class="section-head"><div class="section-title">MQTT 模拟器</div></div>
                <p class="muted" style="font-size:12px;line-height:1.8">
                    内置 Broker 监听 <strong>1884</strong>。在项目根目录执行：
                </p>
                <pre class="mono" style="background:#F7FBF9;padding:12px;border-radius:10px;overflow:auto">cd scripts/iot
npm install
node mqtt-device-simulator.js --broker mqtt://localhost:1884 --stations 2 --devices 5</pre>
                <p class="muted" style="font-size:12px;line-height:1.8">
                    设备上报 Topic：<span class="mono">pv/{stationCode}/{deviceCode}/telemetry</span>。故障数据会自动创建告警与工单。
                </p>
            </div>
            <div class="card">
                <div class="section-head"><div class="section-title">设备在线状态</div><span class="section-sub">${devices.length} 台</span></div>
                ${table(['设备', '电站', '类型', '状态', '最后在线'], devices.slice(0, 10), (d) => `
                    <tr><td class="mono">${esc(d.code)}</td><td>${esc(d.stationName || '-')}</td><td>${esc(d.type)}</td><td>${badge(d.status)}</td><td>${fmtDate(d.lastOnlineAt)}</td></tr>`)}
            </div>
        </div>
    `;
}

async function simulateIot(mode) {
    try {
        const data = await api('/api/iot/simulate', { method: 'POST', body: { mode } });
        const result = data.result || {};
        if (result.alarm) {
            toast('故障上报成功，已生成告警 ' + result.alarm.code + ' 与工单', 'success');
        } else {
            toast('遥测上报成功：' + (result.deviceCode || ''), 'success');
        }
        await renderIot();
    } catch (e) {
        toast(e.message, 'error');
    }
}

boot();




