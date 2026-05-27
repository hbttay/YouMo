<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  createFeedback, listFeedback, getFeedbackStats,
  analyzeFeedback, updateFeedbackStatus, deleteFeedback,
} from '@/api/feedback'

const router = useRouter()

// ── State ──
const feedbacks = ref([])
const stats = ref({})
const loading = ref(false)
const submitting = ref(false)
const analyzingId = ref(null)

// ── Form ──
const form = ref({ content: '', contact: '' })
const formError = ref('')
const showForm = ref(false)

// ── Filters ──
const statusFilter = ref('')
const categoryFilter = ref('')
const severityFilter = ref('')
const techFilter = ref(false)

// ── Labels ──
const categoryLabels = {
  BUG: '🐛 缺陷', FEATURE_REQUEST: '💡 功能建议', UX: '🎨 体验',
  PERFORMANCE: '⚡ 性能', CONTENT_QUALITY: '📝 内容质量', OTHER: '📌 其他',
}
const severityLabels = {
  LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急',
}
const severityColors = {
  LOW: '#16a34a', MEDIUM: '#d97706', HIGH: '#dc2626', CRITICAL: '#7c3aed',
}
const statusLabels = {
  PENDING: '待处理', REVIEWED: '已评审', ESCALATED: '已升级', RESOLVED: '已解决', DISMISSED: '已忽略',
}

// ── Methods ──
async function load() {
  loading.value = true
  try {
    const params = {}
    if (statusFilter.value) params.status = statusFilter.value
    if (categoryFilter.value) params.category = categoryFilter.value
    if (severityFilter.value) params.severity = severityFilter.value
    if (techFilter.value) params.escalate_to_tech = true
    const res = await listFeedback(params)
    feedbacks.value = res?.data || []
    const s = await getFeedbackStats()
    stats.value = s?.data || {}
  } catch (e) {
    console.error('加载反馈失败', e)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.value.content.trim()) { formError.value = '请输入反馈内容'; return }
  submitting.value = true
  formError.value = ''
  try {
    await createFeedback({
      content: form.value.content,
      contact: form.value.contact || undefined,
    })
    form.value = { content: '', contact: '' }
    showForm.value = false
    await load()
    // Auto-analyze after creation
    const latest = feedbacks.value[0]
    if (latest) await doAnalyze(latest.id)
  } catch (e) {
    formError.value = e.message || '提交失败'
  } finally {
    submitting.value = false
  }
}

async function doAnalyze(id) {
  analyzingId.value = id
  try {
    await analyzeFeedback(id)
    await load()
  } catch (e) {
    console.error('AI分析失败', e)
  } finally {
    analyzingId.value = null
  }
}

async function doUpdateStatus(id, status) {
  await updateFeedbackStatus(id, status)
  await load()
}

async function doDelete(id) {
  if (!confirm('确定删除此反馈？')) return
  await deleteFeedback(id)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="feedback-page">
    <div class="fb-header">
      <button class="back-link" @click="router.push('/')">&larr; 返回首页</button>
      <h1>用户反馈</h1>
      <button class="btn-submit" @click="showForm = !showForm">
        {{ showForm ? '取消' : '+ 提交反馈' }}
      </button>
    </div>

    <!-- Stats bar -->
    <div class="fb-stats" v-if="stats.total">
      <div class="stat-item"><span class="stat-num">{{ stats.total }}</span>总计</div>
      <div class="stat-item pending"><span class="stat-num">{{ stats.pending || 0 }}</span>待处理</div>
      <div class="stat-item escalated"><span class="stat-num">{{ stats.escalated || 0 }}</span>已升级</div>
      <div class="stat-item resolved"><span class="stat-num">{{ stats.resolved || 0 }}</span>已解决</div>
      <div class="stat-item tech"><span class="stat-num">{{ stats.needTech || 0 }}</span>需技术介入</div>
    </div>

    <!-- Submit form -->
    <div v-if="showForm" class="fb-form">
      <textarea
        v-model="form.content"
        class="fb-textarea"
        rows="4"
        placeholder="请描述你遇到的问题或建议..."
      ></textarea>
      <input v-model="form.contact" class="fb-input" placeholder="联系方式（选填，方便我们回复）" />
      <div v-if="formError" class="fb-form-error">{{ formError }}</div>
      <div class="fb-form-actions">
        <button class="btn-send" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中...' : '提交反馈' }}
        </button>
        <span class="fb-hint">提交后 AI 将自动分析问题类型和严重等级</span>
      </div>
    </div>

    <!-- Filters -->
    <div class="fb-filters">
      <select v-model="statusFilter" @change="load" class="fb-filter">
        <option value="">全部状态</option>
        <option v-for="(label, key) in statusLabels" :key="key" :value="key">{{ label }}</option>
      </select>
      <select v-model="categoryFilter" @change="load" class="fb-filter">
        <option value="">全部分类</option>
        <option v-for="(label, key) in categoryLabels" :key="key" :value="key">{{ label }}</option>
      </select>
      <select v-model="severityFilter" @change="load" class="fb-filter">
        <option value="">全部等级</option>
        <option v-for="(label, key) in severityLabels" :key="key" :value="key">{{ label }}</option>
      </select>
      <label class="fb-tech-toggle">
        <input type="checkbox" v-model="techFilter" @change="load" />
        仅看需技术介入
      </label>
    </div>

    <!-- List -->
    <div v-if="loading" class="fb-loading">加载中...</div>
    <div v-else-if="!feedbacks.length" class="fb-empty">
      暂无反馈，点击上方按钮提交你的第一条反馈
    </div>
    <div v-else class="fb-list">
      <div v-for="fb in feedbacks" :key="fb.id" class="fb-card" :class="'sev-' + (fb.severity || 'LOW').toLowerCase()">
        <div class="fb-card-top">
          <span class="fb-id">#{{ fb.id }}</span>
          <span v-if="fb.category" class="fb-category-tag">{{ categoryLabels[fb.category] || fb.category }}</span>
          <span v-if="fb.severity" class="fb-severity-tag" :style="{ background: severityColors[fb.severity] }">
            {{ severityLabels[fb.severity] || fb.severity }}
          </span>
          <span v-if="fb.escalate_to_tech" class="fb-tech-tag">🔧 需技术介入</span>
          <span class="fb-status-tag">{{ statusLabels[fb.status] || fb.status }}</span>
          <span class="fb-time">{{ new Date(fb.created_at).toLocaleString('zh-CN', { month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' }) }}</span>
        </div>
        <div class="fb-content">{{ fb.content }}</div>
        <div v-if="fb.ai_analysis" class="fb-analysis">
          <strong>AI 分析：</strong>
          {{ (() => { try { return JSON.parse(fb.ai_analysis).summary || fb.ai_analysis } catch { return fb.ai_analysis } })() }}
        </div>
        <div class="fb-contact" v-if="fb.contact">联系：{{ fb.contact }}</div>
        <div class="fb-actions">
          <button v-if="!fb.category" class="fb-btn-ai" :disabled="analyzingId === fb.id" @click="doAnalyze(fb.id)">
            {{ analyzingId === fb.id ? '分析中...' : '🤖 AI 分析' }}
          </button>
          <select v-if="fb.status !== 'RESOLVED' && fb.status !== 'DISMISSED'" class="fb-status-select" @change="doUpdateStatus(fb.id, ($event.target).value)">
            <option value="">标记状态...</option>
            <option value="ESCALATED">升级技术团队</option>
            <option value="RESOLVED">已解决</option>
            <option value="DISMISSED">忽略</option>
          </select>
          <button class="fb-btn-delete" @click="doDelete(fb.id)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feedback-page {
  max-width: 880px; margin: 0 auto; padding: 24px 20px 80px;
}
.fb-header {
  display: flex; align-items: center; gap: 16px; margin-bottom: 20px;
}
.fb-header h1 { font-size: 22px; font-weight: 700; margin: 0; flex: 1; }
.back-link { color: #5b3cc4; text-decoration: none; font-size: 14px; cursor: pointer; background: none; border: none; font-family: inherit; }
.back-link:hover { color: #4a2fa8; }
.btn-submit {
  padding: 8px 20px; font-size: 14px; font-weight: 600; border: none; border-radius: 8px;
  background: #5b3cc4; color: #fff; cursor: pointer; font-family: inherit;
}
.btn-submit:hover { background: #4a2fa8; }

/* Stats */
.fb-stats { display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.stat-item {
  padding: 10px 18px; background: #f3f4f6; border-radius: 8px;
  font-size: 13px; color: #6b7280; display: flex; flex-direction: column; align-items: center; gap: 2px;
}
.stat-num { font-size: 20px; font-weight: 700; color: #111827; }
.stat-item.pending .stat-num { color: #d97706; }
.stat-item.escalated .stat-num { color: #dc2626; }
.stat-item.resolved .stat-num { color: #16a34a; }
.stat-item.tech .stat-num { color: #7c3aed; }

/* Form */
.fb-form {
  background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px;
  padding: 20px; margin-bottom: 20px;
}
.fb-textarea {
  width: 100%; padding: 12px; font-size: 14px; font-family: inherit;
  border: 1px solid #d1d5db; border-radius: 8px; resize: vertical; box-sizing: border-box;
}
.fb-textarea:focus { outline: none; border-color: #5b3cc4; box-shadow: 0 0 0 2px rgba(91,60,196,0.15); }
.fb-input {
  width: 100%; padding: 10px 12px; margin-top: 10px; font-size: 14px; font-family: inherit;
  border: 1px solid #d1d5db; border-radius: 8px; box-sizing: border-box;
}
.fb-input:focus { outline: none; border-color: #5b3cc4; }
.fb-form-error { color: #dc2626; font-size: 13px; margin-top: 8px; }
.fb-form-actions { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.btn-send {
  padding: 8px 24px; font-size: 14px; font-weight: 600; border: none; border-radius: 8px;
  background: #5b3cc4; color: #fff; cursor: pointer; font-family: inherit;
}
.btn-send:hover:not(:disabled) { background: #4a2fa8; }
.btn-send:disabled { opacity: 0.6; cursor: not-allowed; }
.fb-hint { font-size: 12px; color: #9ca3af; }

/* Filters */
.fb-filters { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
.fb-filter {
  padding: 6px 12px; font-size: 13px; border: 1px solid #d1d5db; border-radius: 6px;
  background: #fff; font-family: inherit; cursor: pointer;
}
.fb-tech-toggle { font-size: 13px; color: #6b7280; display: flex; align-items: center; gap: 4px; cursor: pointer; }

/* Cards */
.fb-loading, .fb-empty { text-align: center; padding: 60px 0; color: #9ca3af; font-size: 14px; }
.fb-list { display: flex; flex-direction: column; gap: 12px; }
.fb-card {
  border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px 20px;
  transition: border-color 0.15s; background: #fff;
}
.fb-card:hover { border-color: #c4b5fd; }
.fb-card.sev-critical { border-left: 4px solid #7c3aed; }
.fb-card.sev-high { border-left: 4px solid #dc2626; }
.fb-card.sev-medium { border-left: 4px solid #d97706; }

.fb-card-top { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.fb-id { font-size: 12px; color: #9ca3af; font-weight: 500; }
.fb-category-tag { font-size: 11px; font-weight: 600; padding: 1px 8px; background: #ede9fe; color: #7c3aed; border-radius: 10px; }
.fb-severity-tag { font-size: 11px; font-weight: 600; padding: 1px 8px; color: #fff; border-radius: 10px; }
.fb-tech-tag { font-size: 11px; font-weight: 600; padding: 1px 8px; background: #fef3c7; color: #92400e; border-radius: 10px; }
.fb-status-tag { font-size: 11px; padding: 1px 8px; background: #f3f4f6; color: #6b7280; border-radius: 10px; }
.fb-time { font-size: 11px; color: #9ca3af; margin-left: auto; }

.fb-content { font-size: 14px; line-height: 1.7; color: #1f2937; margin-bottom: 8px; white-space: pre-wrap; }
.fb-analysis { font-size: 12px; color: #6b7280; background: #f0fdf4; padding: 8px 12px; border-radius: 6px; margin-bottom: 6px; line-height: 1.5; }
.fb-contact { font-size: 12px; color: #9ca3af; margin-bottom: 8px; }
.fb-actions { display: flex; gap: 8px; align-items: center; }
.fb-btn-ai {
  padding: 4px 12px; font-size: 12px; font-weight: 500; border: 1px solid #7c3aed; border-radius: 6px;
  background: transparent; color: #7c3aed; cursor: pointer; font-family: inherit;
}
.fb-btn-ai:hover:not(:disabled) { background: #ede9fe; }
.fb-btn-ai:disabled { opacity: 0.5; cursor: not-allowed; }
.fb-status-select {
  padding: 4px 8px; font-size: 12px; border: 1px solid #d1d5db; border-radius: 6px;
  background: #fff; font-family: inherit; cursor: pointer; color: #6b7280;
}
.fb-btn-delete {
  padding: 4px 10px; font-size: 12px; border: none; background: transparent;
  color: #9ca3af; cursor: pointer; font-family: inherit; margin-left: auto;
}
.fb-btn-delete:hover { color: #dc2626; }
</style>
