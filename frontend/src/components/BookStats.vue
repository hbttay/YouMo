<script setup>
import { ref, computed, onMounted } from 'vue'
import { getBookStats } from '@/api/book'
import BookReport from '@/components/BookReport.vue'

const props = defineProps({
  bookId: { type: [String, Number], required: true },
})

const loading = ref(true)
const error = ref('')
const stats = ref(null)
const showReport = ref(false)

const depthLabels = { L0: '背景板', L1: '配角', L2: '重要配角', L3: '主角' }
const depthColors = { L0: '#9ca3af', L1: '#3b82f6', L2: '#f59e0b', L3: '#7c3aed' }
const statusLabels = { DRAFT: '草稿', WRITING: '写作中', REVISION: '修改中', COMPLETED: '已完成' }
const statusColors = { DRAFT: '#9ca3af', WRITING: '#3b82f6', REVISION: '#f59e0b', COMPLETED: '#10b981' }
const sourceLabels = { USER_EDITED: '用户撰写', AI_GENERATED: 'AI 生成', AI_REWRITTEN: 'AI 改写' }
const sourceColors = { USER_EDITED: '#7c3aed', AI_GENERATED: '#3b82f6', AI_REWRITTEN: '#f59e0b' }

const completionRate = computed(() => {
  if (!stats.value || !stats.value.chapter_count) return 0
  return Math.round((stats.value.completed_chapters / stats.value.chapter_count) * 100)
})

const maxChapterWords = computed(() => {
  if (!stats.value?.chapter_word_counts?.length) return 1
  return Math.max(...stats.value.chapter_word_counts.map(c => c.word_count), 1)
})

const totalNodes = computed(() => {
  if (!stats.value?.node_status_breakdown) return 1
  return Object.values(stats.value.node_status_breakdown).reduce((a, b) => a + b, 0) || 1
})

const maxDailyWords = computed(() => {
  if (!stats.value?.daily_activity?.length) return 1
  return Math.max(...stats.value.daily_activity.map(d => d.word_count), 1)
})

onMounted(async () => {
  try {
    const res = await getBookStats(props.bookId)
    stats.value = res.data || res
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="stats-panel">
    <div v-if="loading" class="stats-loading">加载中...</div>
    <div v-else-if="error" class="stats-error">{{ error }}</div>

    <template v-else-if="stats">
      <div class="stats-header">
        <h2>📊 全书统计</h2>
        <button class="btn-report" @click="showReport = true">📋 生成写作报告</button>
      </div>
      <!-- Overview cards -->
      <div class="stats-overview">
        <div class="stat-card">
          <span class="stat-value">{{ stats.total_words.toLocaleString() }}</span>
          <span class="stat-label">总字数</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ stats.volume_count }}</span>
          <span class="stat-label">卷</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ stats.chapter_count }}</span>
          <span class="stat-label">章</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ completionRate }}%</span>
          <span class="stat-label">完成度</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ stats.character_count }}</span>
          <span class="stat-label">角色</span>
        </div>
      </div>

      <div class="stats-grid">
        <!-- Left column -->
        <div class="stats-col">
          <!-- Node status breakdown -->
          <div v-if="stats.node_status_breakdown" class="stats-section">
            <h3>写作进度</h3>
            <div class="status-bars">
              <div v-for="(count, status) in stats.node_status_breakdown" :key="status" class="status-row">
                <span class="status-name">{{ statusLabels[status] || status }}</span>
                <div class="status-bar-track">
                  <div class="status-bar-fill" :style="{ width: (count / totalNodes * 100) + '%', backgroundColor: statusColors[status] || '#9ca3af' }"></div>
                </div>
                <span class="status-count">{{ count }}</span>
              </div>
            </div>
          </div>

          <!-- Source breakdown -->
          <div v-if="stats.source_breakdown" class="stats-section">
            <h3>内容来源</h3>
            <div class="source-pie">
              <div v-for="(count, source) in stats.source_breakdown" :key="source" class="source-row">
                <span class="source-dot" :style="{ background: sourceColors[source] || '#9ca3af' }"></span>
                <span class="source-label">{{ sourceLabels[source] || source }}</span>
                <span class="source-count">{{ count }} 章</span>
              </div>
            </div>
          </div>

          <!-- Character depth distribution -->
          <div v-if="stats.characters_by_depth && Object.keys(stats.characters_by_depth).length" class="stats-section">
            <h3>角色分布</h3>
            <div class="depth-bars">
              <div v-for="(count, level) in stats.characters_by_depth" :key="level" class="depth-row">
                <span class="depth-name">{{ depthLabels[level] || level }}</span>
                <div class="depth-bar-track">
                  <div class="depth-bar-fill" :style="{ width: (count / stats.character_count * 100) + '%', backgroundColor: depthColors[level] || '#9ca3af' }"></div>
                </div>
                <span class="depth-count">{{ count }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Right column -->
        <div class="stats-col">
          <!-- Volume summary -->
          <div v-if="stats.volume_word_counts?.length" class="stats-section">
            <h3>分卷概览</h3>
            <div class="volume-list">
              <div v-for="(v, i) in stats.volume_word_counts" :key="i" class="volume-row">
                <div class="volume-header">
                  <span class="volume-title">{{ v.title }}</span>
                  <span class="volume-meta">{{ v.completed_chapters }}/{{ v.chapter_count }} 章 · {{ v.word_count.toLocaleString() }} 字</span>
                </div>
                <div class="volume-bar-track">
                  <div class="volume-bar-fill" :style="{ width: v.chapter_count ? (v.completed_chapters / v.chapter_count * 100) + '%' : '0%' }"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- Character appearances -->
          <div v-if="stats.character_appearances?.length" class="stats-section">
            <h3>角色出场</h3>
            <div class="appearance-list">
              <div v-for="(c, i) in stats.character_appearances.slice(0, 8)" :key="i" class="appearance-row">
                <span class="appearance-rank">{{ i + 1 }}</span>
                <span class="appearance-name">{{ c.name }}</span>
                <span class="appearance-count">{{ c.chapter_count }} 章</span>
              </div>
            </div>
          </div>

          <!-- Daily writing activity -->
          <div v-if="stats.daily_activity?.length" class="stats-section">
            <h3>写作日历</h3>
            <div class="daily-chart">
              <div v-for="(d, i) in stats.daily_activity" :key="i" class="daily-bar-col">
                <div class="daily-bar" :style="{ height: (d.word_count / maxDailyWords * 100) + '%' }" :title="`${d.date}: ${d.word_count} 字`"></div>
                <span class="daily-date">{{ d.date.slice(5) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Chapter word counts bar chart (full width) -->
      <div v-if="stats.chapter_word_counts?.length" class="stats-section">
        <h3>分章字数</h3>
        <div class="chapter-bars">
          <div v-for="(c, i) in stats.chapter_word_counts" :key="i" class="chapter-bar-row">
            <span class="chapter-bar-label">{{ c.title }}</span>
            <div class="chapter-bar-track">
              <div class="chapter-bar-fill" :style="{ width: (c.word_count / maxChapterWords * 100) + '%' }"></div>
            </div>
            <span class="chapter-bar-count">{{ c.word_count.toLocaleString() }}</span>
          </div>
        </div>
      </div>
    </template>

    <BookReport v-if="showReport" :book-id="bookId" @close="showReport = false" />
  </div>
</template>

<style scoped>
.stats-panel {
  background: var(--bg-surface);
  border-radius: 10px;
  border: 1px solid var(--border-color);
  padding: 24px 28px;
}
.stats-loading, .stats-error {
  text-align: center; padding: 40px 0; color: var(--text-secondary); font-size: 14px;
}
.stats-error { color: #dc2626; }

.stats-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px;
}
.stats-header h2 { margin: 0; font-size: 18px; font-weight: 600; }
.btn-report {
  padding: 8px 20px; background: var(--color-brand); color: #fff;
  border: none; border-radius: 8px; font-size: 14px; cursor: pointer;
  transition: background 0.2s;
}
.btn-report:hover { background: #4a2fa8; }

/* Overview */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--bg-surface-hover);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}
.stat-value {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Two-column grid */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
@media (max-width: 800px) {
  .stats-grid { grid-template-columns: 1fr; }
}
.stats-col {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* Sections */
.stats-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}
.stats-section h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px;
}

/* Status bars */
.status-bars { display: flex; flex-direction: column; gap: 8px; }
.status-row { display: flex; align-items: center; gap: 10px; }
.status-name { width: 56px; font-size: 13px; color: var(--text-secondary); flex-shrink: 0; }
.status-bar-track { flex: 1; height: 16px; background: #f3f4f6; border-radius: 8px; overflow: hidden; }
.status-bar-fill { height: 100%; border-radius: 8px; transition: width 0.4s ease; min-width: 3px; }
.status-count { width: 24px; font-size: 13px; font-weight: 600; color: var(--text-primary); text-align: right; flex-shrink: 0; }

/* Source breakdown */
.source-pie { display: flex; flex-direction: column; gap: 8px; }
.source-row { display: flex; align-items: center; gap: 8px; }
.source-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.source-label { font-size: 13px; color: var(--text-secondary); flex: 1; }
.source-count { font-size: 13px; font-weight: 600; color: var(--text-primary); }

/* Depth bars */
.depth-bars { display: flex; flex-direction: column; gap: 10px; }
.depth-row { display: flex; align-items: center; gap: 10px; }
.depth-name { width: 60px; font-size: 13px; color: var(--text-secondary); flex-shrink: 0; }
.depth-bar-track { flex: 1; height: 20px; background: #f3f4f6; border-radius: 10px; overflow: hidden; }
.depth-bar-fill { height: 100%; border-radius: 10px; transition: width 0.4s ease; min-width: 4px; }
.depth-count { width: 28px; font-size: 13px; font-weight: 600; color: var(--text-primary); text-align: right; flex-shrink: 0; }

/* Volume list */
.volume-list { display: flex; flex-direction: column; gap: 12px; }
.volume-row { display: flex; flex-direction: column; gap: 4px; }
.volume-header { display: flex; justify-content: space-between; align-items: baseline; }
.volume-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.volume-meta { font-size: 11px; color: var(--text-secondary); }
.volume-bar-track { height: 6px; background: #f3f4f6; border-radius: 3px; overflow: hidden; }
.volume-bar-fill { height: 100%; background: #7c3aed; border-radius: 3px; min-width: 2px; transition: width 0.4s ease; }

/* Appearance list */
.appearance-list { display: flex; flex-direction: column; gap: 6px; }
.appearance-row { display: flex; align-items: center; gap: 8px; }
.appearance-rank { width: 20px; font-size: 12px; font-weight: 700; color: var(--color-brand); text-align: center; flex-shrink: 0; }
.appearance-name { font-size: 13px; color: var(--text-primary); flex: 1; }
.appearance-count { font-size: 12px; color: var(--text-secondary); flex-shrink: 0; }

/* Daily activity chart */
.daily-chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 80px;
  padding: 4px 0;
}
.daily-bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
}
.daily-bar {
  width: 100%;
  max-width: 20px;
  background: #7c3aed;
  border-radius: 2px 2px 0 0;
  min-height: 2px;
  transition: height 0.3s ease;
}
.daily-date {
  font-size: 9px;
  color: var(--text-secondary);
  margin-top: 4px;
  transform: rotate(-45deg);
  transform-origin: top left;
  white-space: nowrap;
}

/* Chapter bars */
.chapter-bars { display: flex; flex-direction: column; gap: 8px; }
.chapter-bar-row { display: flex; align-items: center; gap: 10px; }
.chapter-bar-label { width: 80px; font-size: 12px; color: var(--text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 0; }
.chapter-bar-track { flex: 1; height: 16px; background: #f3f4f6; border-radius: 8px; overflow: hidden; }
.chapter-bar-fill { height: 100%; background: #7c3aed; border-radius: 8px; transition: width 0.4s ease; min-width: 3px; }
.chapter-bar-count { width: 56px; font-size: 12px; color: var(--text-secondary); text-align: right; flex-shrink: 0; font-variant-numeric: tabular-nums; }
</style>
