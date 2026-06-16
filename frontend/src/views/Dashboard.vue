<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import * as echarts from 'echarts'

const router = useRouter()
const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(c => {
  const t = localStorage.getItem('token')
  if (t) c.headers.Authorization = `Bearer ${t}`
  return c
})
api.interceptors.response.use(r => r.data, err => {
  if (err.response?.status === 401) { localStorage.removeItem('token'); router.push('/login') }
  return Promise.reject(err)
})

const tab = ref('upload')
const files = ref([])
const selectedFile = ref(null)
const preview = ref(null)
const question = ref('')
const aiResult = ref(null)
const uploading = ref(false)
const chartRef = ref(null)
const chartType = ref('bar')
const userInfo = ref(null)

onMounted(async () => {
  try { userInfo.value = (await api.get('/user/info')).data } catch {}
  loadFiles()
})

async function loadFiles() {
  try { files.value = (await api.get('/user/file/list')).data || [] } catch {}
}

async function handleUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  uploading.value = true
  try {
    const fd = new FormData(); fd.append('file', file)
    const res = await api.post('/user/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const uf = res.data
    try {
      await api.post('/data/trigger-parse', { filePath: uf.filePath, originalName: uf.originalName, fileSize: uf.fileSize })
    } catch {}
    await loadFiles()
  } catch(e) { alert('上传失败: ' + (e.response?.data?.msg || e.message)) }
  finally { uploading.value = false }
}

async function selectFile(file) {
  selectedFile.value = file
  tab.value = 'preview'
  try {
    const r = await api.get('/data/preview/' + file.id)
    preview.value = r.data
  } catch(e) { alert('加载失败') }
}

async function askAI() {
  if (!question.value || !selectedFile.value) return
  try {
    const r = await api.post('/ai/query', { fileId: selectedFile.value.id, question: question.value })
    aiResult.value = r.data
  } catch(e) { alert('AI查询失败: ' + (e.response?.data?.msg || e.message)) }
}

async function loadChart(type) {
  chartType.value = type
  tab.value = 'chart'
  if (!selectedFile.value) return
  await nextTick()
  try {
    const r = await api.post('/viz/chart/' + type + '/' + selectedFile.value.id, {})
    const dom = chartRef.value
    if (dom) { const c = echarts.init(dom); c.setOption(r.data) }
  } catch(e) { /* ignore */ }
}

function logout() { localStorage.removeItem('token'); router.push('/login') }
function fmtSize(b) { if (!b) return '0B'; const u = ['B','KB','MB','GB']; let i = 0, s = b; while (s >= 1024 && i < u.length-1) { s /= 1024; i++ }; return s.toFixed(1)+u[i] }
</script>

<template>
  <div class="dashboard">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="side-logo">📊 <span>DataInsight</span></div>
      <nav>
        <a :class="{ active: tab === 'upload' }" @click="tab = 'upload'">📁 文件上传</a>
        <a :class="{ active: tab === 'preview' }" @click="tab = 'preview'" :style="{ opacity: preview ? 1 : 0.4 }">🔍 数据预览</a>
        <a :class="{ active: tab === 'ai' }" @click="tab = 'ai'" :style="{ opacity: selectedFile ? 1 : 0.4 }">🤖 AI 分析</a>
        <a :class="{ active: tab === 'chart' }" @click="tab = 'chart'" :style="{ opacity: selectedFile ? 1 : 0.4 }">📈 图表</a>
      </nav>
      <div class="side-user">
        <div class="avatar">{{ userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}</div>
        <span>{{ userInfo?.username || '' }}</span>
        <button @click="logout">退出</button>
      </div>
    </aside>

    <!-- Main -->
    <main class="main-content">
      <!-- Upload -->
      <section v-if="tab === 'upload'" class="page">
        <h2>📁 文件上传</h2>
        <p class="desc">上传 CSV 或 Excel 文件，自动解析并生成数据洞察</p>
        <label class="upload-zone" :class="{ uploading }">
          <input type="file" accept=".csv,.xlsx,.xls" @change="handleUpload" :disabled="uploading" />
          <div class="upload-icon">📤</div>
          <div class="upload-text">{{ uploading ? '上传解析中...' : '拖拽文件到此处或点击上传' }}</div>
          <div class="upload-hint">支持 CSV、Excel（.xlsx/.xls），最大 50MB</div>
        </label>
        <div v-if="files.length" class="file-list">
          <h3>已上传文件</h3>
          <div class="file-card" v-for="f in files" :key="f.id" @click="selectFile(f)" :class="{ selected: selectedFile?.id === f.id }">
            <span class="file-icon">📄</span>
            <span class="file-name">{{ f.originalName }}</span>
            <span class="file-size">{{ fmtSize(f.fileSize) }}</span>
            <span class="file-status" :class="f.status">{{ f.status === 'COMPLETED' ? '✅ 已解析' : f.status === 'PENDING' ? '⏳ 等待中' : '❌ 失败' }}</span>
          </div>
        </div>
      </section>

      <!-- Preview -->
      <section v-if="tab === 'preview' && preview" class="page">
        <h2>🔍 数据预览</h2>
        <div class="stats-row">
          <div class="stat-card"><div class="stat-num">{{ preview.totalRows }}</div><div class="stat-label">总行数</div></div>
          <div class="stat-card"><div class="stat-num">{{ preview.columns?.length }}</div><div class="stat-label">总列数</div></div>
          <div class="stat-card"><div class="stat-num">{{ preview.file?.status }}</div><div class="stat-label">状态</div></div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th v-for="c in preview.columns" :key="c.id">{{ c.columnName }} <small>({{ c.columnType }})</small></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in preview.records" :key="r.id">
                <td>{{ i + 1 }}</td>
                <td v-for="c in preview.columns" :key="c.id">
                  {{ (() => { try { return JSON.parse(r.rowData || '{}')['col_' + c.ordinalPosition] || '' } catch { return '' } })() }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- AI -->
      <section v-if="tab === 'ai'" class="page">
        <h2>🤖 AI 分析</h2>
        <p class="desc">用自然语言提问，AI 自动生成 SQL 并返回结果</p>
        <div class="ai-input">
          <input v-model="question" @keyup.enter="askAI" placeholder="例如：这个表有多少行？销售额最高是多少？" />
          <button @click="askAI">🚀 提问</button>
        </div>
        <div v-if="aiResult" class="ai-result">
          <div class="sql-box">
            <div class="sql-label">生成的 SQL</div>
            <pre>{{ aiResult.sql }}</pre>
          </div>
          <div class="result-data" v-if="aiResult.data?.length">
            <div class="sql-label">查询结果 ({{ aiResult.totalRows }} 条)</div>
            <table>
              <thead><tr><th v-for="(_, k) in aiResult.data[0]" :key="k">{{ k }}</th></tr></thead>
              <tbody><tr v-for="(r, i) in aiResult.data" :key="i"><td v-for="(_, k) in r" :key="k">{{ r[k] }}</td></tr></tbody>
            </table>
          </div>
          <div class="explain">{{ aiResult.explanation }}</div>
        </div>
      </section>

      <!-- Chart -->
      <section v-if="tab === 'chart'" class="page">
        <h2>📈 图表</h2>
        <div class="chart-btns">
          <button :class="{ active: chartType === 'bar' }" @click="loadChart('bar')">📊 柱状图</button>
          <button :class="{ active: chartType === 'pie' }" @click="loadChart('pie')">🥧 饼图</button>
          <button :class="{ active: chartType === 'line' }" @click="loadChart('line')">📉 折线图</button>
        </div>
        <div ref="chartRef" class="chart-container"></div>
      </section>
    </main>
  </div>
</template>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f1f5f9; color: #1e293b; }
.dashboard { display: flex; min-height: 100vh; }
.sidebar {
  width: 220px; background: linear-gradient(180deg, #0f172a 0%, #1e293b 100%); color: white;
  display: flex; flex-direction: column; padding: 20px 0; position: fixed; top: 0; left: 0; bottom: 0; z-index: 10;
}
.side-logo { padding: 0 20px 24px; font-size: 20px; font-weight: 700; }
.side-logo span { color: #409EFF; }
nav { flex: 1; }
nav a {
  display: block; padding: 14px 20px; cursor: pointer; color: #94a3b8; font-size: 15px;
  transition: all 0.2s; border-left: 3px solid transparent; text-decoration: none;
}
nav a:hover { background: rgba(255,255,255,0.05); color: white; }
nav a.active { background: rgba(64,158,255,0.15); color: #409EFF; border-left-color: #409EFF; }
.side-user { padding: 16px 20px; border-top: 1px solid rgba(255,255,255,0.1); display: flex; align-items: center; gap: 10px; }
.avatar { width: 32px; height: 32px; background: #409EFF; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; }
.side-user button { margin-left: auto; background: none; border: 1px solid rgba(255,255,255,0.2); color: #94a3b8; padding: 4px 10px; border-radius: 6px; cursor: pointer; font-size: 12px; }
.main-content { margin-left: 220px; flex: 1; padding: 32px; }
.page { max-width: 1100px; }
.page h2 { margin: 0 0 4px; font-size: 24px; }
.desc { color: #64748b; margin: 0 0 24px; }

/* Upload */
.upload-zone {
  display: block; border: 3px dashed #cbd5e1; border-radius: 16px; padding: 50px; text-align: center;
  cursor: pointer; transition: all 0.3s; background: white; margin-bottom: 24px;
}
.upload-zone:hover, .upload-zone.uploading { border-color: #409EFF; background: #eff6ff; }
.upload-zone input { display: none; }
.upload-icon { font-size: 48px; margin-bottom: 12px; }
.upload-text { font-size: 17px; color: #1e293b; font-weight: 500; }
.upload-hint { color: #94a3b8; font-size: 13px; margin-top: 8px; }
.file-list h3 { font-size: 17px; margin-bottom: 12px; }
.file-card {
  display: flex; align-items: center; gap: 12px; background: white; padding: 14px 18px;
  border-radius: 10px; margin-bottom: 8px; cursor: pointer; transition: all 0.2s; border: 2px solid transparent;
}
.file-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.file-card.selected { border-color: #409EFF; background: #eff6ff; }
.file-icon { font-size: 24px; }
.file-name { flex: 1; font-weight: 500; }
.file-size { color: #94a3b8; font-size: 13px; }
.file-status { font-size: 13px; }

/* Stats */
.stats-row { display: flex; gap: 16px; margin-bottom: 24px; }
.stat-card { flex: 1; background: white; padding: 24px; border-radius: 12px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.stat-num { font-size: 32px; font-weight: 700; color: #409EFF; }
.stat-label { color: #64748b; font-size: 14px; margin-top: 4px; }

/* Table */
.table-wrap { background: white; border-radius: 12px; overflow: auto; max-height: 500px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { background: #f8fafc; padding: 12px 16px; text-align: left; font-weight: 600; color: #475569; border-bottom: 2px solid #e2e8f0; position: sticky; top: 0; }
th small { font-weight: 400; color: #94a3b8; }
td { padding: 10px 16px; border-bottom: 1px solid #f1f5f9; }
tr:hover td { background: #f8fafc; }

/* AI */
.ai-input { display: flex; gap: 12px; margin-bottom: 24px; }
.ai-input input { flex: 1; padding: 14px 18px; border: 2px solid #e2e8f0; border-radius: 12px; font-size: 15px; outline: none; transition: border-color 0.3s; }
.ai-input input:focus { border-color: #409EFF; }
.ai-input button { padding: 14px 28px; background: linear-gradient(135deg, #409EFF, #3b82f6); color: white; border: none; border-radius: 12px; font-size: 16px; cursor: pointer; font-weight: 600; }
.ai-result { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.sql-box { background: #1e293b; border-radius: 10px; padding: 16px; margin-bottom: 16px; }
.sql-label { color: #409EFF; font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.sql-box pre { color: #e2e8f0; margin: 0; font-size: 14px; white-space: pre-wrap; }
.result-data { margin-bottom: 16px; }
.result-data table { width: 100%; }
.explain { color: #64748b; font-size: 14px; line-height: 1.6; }

/* Chart */
.chart-btns { display: flex; gap: 10px; margin-bottom: 24px; }
.chart-btns button {
  padding: 10px 24px; border: 2px solid #e2e8f0; border-radius: 10px; background: white; font-size: 15px;
  cursor: pointer; transition: all 0.2s;
}
.chart-btns button:hover { border-color: #409EFF; }
.chart-btns button.active { background: #409EFF; color: white; border-color: #409EFF; }
.chart-container { width: 100%; height: 450px; background: white; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
</style>
