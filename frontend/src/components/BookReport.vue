<script setup>
import { ref, nextTick } from 'vue'
import { getBookReport } from '@/api/generation'

const props = defineProps({
  bookId: { type: [String, Number], required: true },
})

const emit = defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const report = ref(null)
const reportRef = ref(null)
const exporting = ref(false)

const depthLabels = { L0: '背景板', L1: '配角', L2: '重要配角', L3: '主角' }
const qualityColors = { '优秀': '#10b981', '良好': '#3b82f6', '一般': '#f59e0b', '需改进': '#ef4444' }
const arcColors = { '完整': '#10b981', '发展中': '#f59e0b', '薄弱': '#ef4444' }
const recoveryColors = { '高': '#10b981', '中': '#f59e0b', '低': '#ef4444' }
const priorityColors = { '高': '#ef4444', '中': '#f59e0b', '低': '#3b82f6' }

async function generate() {
  loading.value = true
  error.value = ''
  try {
    report.value = await getBookReport(props.bookId)
  } catch (e) {
    error.value = e.message || '生成失败'
  } finally {
    loading.value = false
  }
}

async function exportImage() {
  if (exporting.value) return
  exporting.value = true
  try {
    const { default: html2canvas } = await import('html2canvas')
    await nextTick()
    const el = reportRef.value
    if (!el) return
    const canvas = await html2canvas(el, { backgroundColor: '#ffffff', scale: 2 })
    const link = document.createElement('a')
    link.download = `写作报告_${new Date().toISOString().slice(0,10)}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
  } catch (e) {
    console.error('Export image failed:', e)
  } finally {
    exporting.value = false
  }
}

async function exportPDF() {
  if (exporting.value) return
  exporting.value = true
  try {
    const { default: html2canvas } = await import('html2canvas')
    const { default: jsPDF } = await import('jspdf')
    await nextTick()
    const el = reportRef.value
    if (!el) return
    const canvas = await html2canvas(el, { backgroundColor: '#ffffff', scale: 2 })
    const imgData = canvas.toDataURL('image/png')
    const pdf = new jsPDF('p', 'mm', 'a4')
    const pageWidth = pdf.internal.pageSize.getWidth()
    const imgHeight = (canvas.height * pageWidth) / canvas.width
    let heightLeft = imgHeight
    let position = 0
    pdf.addImage(imgData, 'PNG', 0, position, pageWidth, imgHeight)
    heightLeft -= pdf.internal.pageSize.getHeight()
    while (heightLeft > 0) {
      position = heightLeft - imgHeight
      pdf.addPage()
      pdf.addImage(imgData, 'PNG', 0, position, pageWidth, imgHeight)
      heightLeft -= pdf.internal.pageSize.getHeight()
    }
    pdf.save(`写作报告_${new Date().toISOString().slice(0,10)}.pdf`)
  } catch (e) {
    console.error('Export PDF failed:', e)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="report-overlay" @click.self="emit('close')">
    <div class="report-modal">
      <div class="report-header">
        <h2>📊 全书写作报告</h2>
        <div class="report-actions">
          <template v-if="report">
            <button class="btn-action" title="导出图片" @click="exportImage" :disabled="exporting">🖼️</button>
            <button class="btn-action" title="导出PDF" @click="exportPDF" :disabled="exporting">📄</button>
            <button class="btn-action" title="重新生成" @click="generate" :disabled="loading">🔄</button>
          </template>
          <button class="btn-close" @click="emit('close')">✕</button>
        </div>
      </div>

      <div v-if="loading" class="report-loading">
        <div class="spinner"></div>
        <p>AI 正在分析全书内容，预计需 30-60 秒...</p>
      </div>

      <div v-else-if="error" class="report-error">
        <p>{{ error }}</p>
        <button class="btn-retry" @click="generate">重试</button>
      </div>

      <div v-else-if="!report" class="report-start">
        <p>基于各章摘要、角色数据和伏笔记录，AI 将生成一份全面的写作诊断报告。</p>
        <p class="report-start-hint">包含：总体评价 · 分卷质量 · 人物线完整度 · 伏笔回收率 · 改进建议</p>
        <button class="btn-generate" @click="generate">开始生成</button>
      </div>

      <div v-else ref="reportRef" class="report-body">
        <!-- Overall -->
        <section class="r-section">
          <h3>总体评价</h3>
          <p class="r-overall">{{ report.overall_assessment }}</p>
        </section>

        <!-- Volume analysis -->
        <section v-if="report.volume_analyses?.length" class="r-section">
          <h3>分卷分析</h3>
          <div class="r-volumes">
            <div v-for="(v, i) in report.volume_analyses" :key="i" class="r-volume-card">
              <div class="r-vol-header">
                <span class="r-vol-title">{{ v.title }}</span>
                <span class="r-vol-words">{{ (v.word_count || 0).toLocaleString() }} 字</span>
                <span class="r-vol-quality" :style="{ color: qualityColors[v.quality] || '#9ca3af' }">{{ v.quality }}</span>
              </div>
              <div class="r-vol-row"><span class="r-label">节奏</span><span>{{ v.pacing }}</span></div>
              <div class="r-vol-row"><span class="r-label">亮点</span><span>{{ v.highlight }}</span></div>
              <div v-if="v.issue" class="r-vol-row"><span class="r-label">问题</span><span class="r-issue">{{ v.issue }}</span></div>
            </div>
          </div>
        </section>

        <!-- Character arcs -->
        <section v-if="report.character_arcs" class="r-section">
          <h3>人物线完整度</h3>
          <p class="r-sub">{{ report.character_arcs.overall_assessment }}</p>
          <div class="r-chars">
            <div v-for="(c, i) in report.character_arcs.arcs" :key="i" class="r-char-row">
              <span class="r-char-name">{{ c.name }}</span>
              <span class="r-char-depth">{{ depthLabels[c.depth_level] || c.depth_level }}</span>
              <span class="r-char-appear">{{ c.appearance_chapters }} 章</span>
              <span class="r-char-arc" :style="{ color: arcColors[c.arc_completeness] || '#9ca3af' }">{{ c.arc_completeness }}</span>
              <span class="r-char-desc">{{ c.description }}</span>
            </div>
          </div>
        </section>

        <!-- Foreshadowing -->
        <section v-if="report.foreshadowing" class="r-section">
          <h3>伏笔回收</h3>
          <div class="r-foreshadow">
            <div class="r-fs-stats">
              <div class="r-fs-stat">
                <span class="r-fs-num">{{ report.foreshadowing.total_planted }}</span>
                <span class="r-fs-label">已埋伏笔</span>
              </div>
              <div class="r-fs-stat">
                <span class="r-fs-num">{{ report.foreshadowing.recycled }}</span>
                <span class="r-fs-label">已回收</span>
              </div>
              <div class="r-fs-stat">
                <span class="r-fs-num" :style="{ color: recoveryColors[report.foreshadowing.recovery_rate] || '#9ca3af' }">{{ report.foreshadowing.recovery_rate }}</span>
                <span class="r-fs-label">回收率</span>
              </div>
            </div>
            <p class="r-fs-assessment">{{ report.foreshadowing.assessment }}</p>
            <div v-if="report.foreshadowing.unrecycled_items?.length" class="r-unrecycled">
              <span class="r-label">未回收伏笔：</span>
              <span v-for="(item, i) in report.foreshadowing.unrecycled_items" :key="i" class="r-tag">{{ item }}</span>
            </div>
          </div>
        </section>

        <!-- Top advice -->
        <section v-if="report.top_advice?.length" class="r-section">
          <h3>改进建议</h3>
          <div class="r-advice-list">
            <div v-for="(a, i) in report.top_advice" :key="i" class="r-advice-card">
              <div class="r-advice-header">
                <span class="r-advice-idx">{{ i + 1 }}</span>
                <span class="r-advice-cat">{{ a.category }}</span>
                <span class="r-advice-priority" :style="{ color: priorityColors[a.priority] || '#9ca3af' }">{{ a.priority }}优先级</span>
              </div>
              <p class="r-advice-text">{{ a.suggestion }}</p>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
}
.report-modal {
  background: var(--bg-surface);
  border-radius: 12px;
  width: 720px; max-width: 95vw; max-height: 85vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.report-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 24px; border-bottom: 1px solid var(--border-color);
  position: sticky; top: 0; background: var(--bg-surface); z-index: 1;
}
.report-header h2 { margin: 0; font-size: 18px; }
.report-actions { display: flex; align-items: center; gap: 6px; }
.btn-action {
  width: 32px; height: 32px; border: none; background: none;
  font-size: 16px; cursor: pointer; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
}
.btn-action:hover { background: var(--bg-surface-hover); }
.btn-action:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-close {
  width: 32px; height: 32px; border: none; background: none;
  font-size: 18px; cursor: pointer; color: var(--text-secondary);
  border-radius: 6px; display: flex; align-items: center; justify-content: center;
}
.btn-close:hover { background: var(--bg-surface-hover); color: var(--text-primary); }

.report-loading, .report-error, .report-start {
  text-align: center; padding: 60px 24px; color: var(--text-secondary);
}
.report-start-hint { font-size: 13px; color: var(--text-secondary); margin-top: -8px; }
.spinner {
  width: 36px; height: 36px; border: 3px solid var(--border-color);
  border-top-color: var(--color-brand); border-radius: 50%;
  animation: spin 0.8s linear infinite; margin: 0 auto 16px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.report-error { color: #dc2626; }
.btn-generate, .btn-retry {
  margin-top: 16px; padding: 10px 32px;
  background: var(--color-brand); color: #fff; border: none;
  border-radius: 8px; font-size: 15px; cursor: pointer;
}
.btn-generate:hover, .btn-retry:hover { background: #4a2fa8; }

.report-body { padding: 24px; display: flex; flex-direction: column; gap: 24px; }
.r-section h3 {
  font-size: 16px; font-weight: 600; color: var(--text-primary);
  margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border-color);
}
.r-overall { font-size: 14px; line-height: 1.7; color: var(--text-primary); margin: 0; }
.r-sub { font-size: 13px; color: var(--text-secondary); margin: 0 0 12px; }

/* Volume cards */
.r-volumes { display: flex; flex-direction: column; gap: 12px; }
.r-volume-card {
  background: var(--bg-surface-hover); border-radius: 8px; padding: 14px 16px;
}
.r-vol-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.r-vol-title { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.r-vol-words { font-size: 12px; color: var(--text-secondary); }
.r-vol-quality { font-size: 13px; font-weight: 600; margin-left: auto; }
.r-vol-row { display: flex; gap: 8px; font-size: 13px; color: var(--text-primary); margin-top: 4px; }
.r-label { color: var(--text-secondary); flex-shrink: 0; min-width: 36px; }
.r-issue { color: #ef4444; }

/* Character rows */
.r-chars { display: flex; flex-direction: column; gap: 6px; }
.r-char-row {
  display: flex; align-items: baseline; gap: 10px; font-size: 13px;
  padding: 6px 8px; border-radius: 4px;
}
.r-char-row:nth-child(even) { background: var(--bg-surface-hover); }
.r-char-name { font-weight: 600; color: var(--text-primary); min-width: 60px; }
.r-char-depth { font-size: 11px; color: var(--text-secondary); min-width: 56px; }
.r-char-appear { font-size: 11px; color: var(--text-secondary); min-width: 40px; }
.r-char-arc { font-weight: 600; font-size: 12px; min-width: 40px; }
.r-char-desc { color: var(--text-secondary); flex: 1; }

/* Foreshadowing */
.r-fs-stats { display: flex; gap: 24px; margin-bottom: 12px; }
.r-fs-stat { text-align: center; }
.r-fs-num { display: block; font-size: 28px; font-weight: 700; color: var(--text-primary); }
.r-fs-label { font-size: 12px; color: var(--text-secondary); }
.r-fs-assessment { font-size: 13px; color: var(--text-primary); margin: 0 0 8px; }
.r-unrecycled { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
.r-tag {
  font-size: 12px; padding: 2px 8px; background: #fef3c7; color: #92400e;
  border-radius: 4px;
}

/* Advice */
.r-advice-list { display: flex; flex-direction: column; gap: 10px; }
.r-advice-card {
  background: var(--bg-surface-hover); border-radius: 8px; padding: 14px 16px;
  border-left: 3px solid var(--color-brand);
}
.r-advice-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.r-advice-idx {
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--color-brand); color: #fff;
  font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.r-advice-cat { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.r-advice-priority { font-size: 12px; margin-left: auto; }
.r-advice-text { font-size: 13px; color: var(--text-secondary); margin: 0; line-height: 1.6; }
</style>
