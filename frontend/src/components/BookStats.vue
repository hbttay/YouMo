<script setup>
import { ref, computed, onMounted } from 'vue'
import { getBookStats } from '@/api/book'

const props = defineProps({
  bookId: { type: [String, Number], required: true },
})

const loading = ref(true)
const error = ref('')
const stats = ref(null)

const depthLabels = { L0: '背景板', L1: '配角', L2: '重要配角', L3: '主角' }
const depthColors = { L0: '#9ca3af', L1: '#3b82f6', L2: '#f59e0b', L3: '#7c3aed' }

const completionRate = computed(() => {
  if (!stats.value || !stats.value.chapter_count || !stats.value.completed_chapters) return 0
  return Math.round((stats.value.completed_chapters / stats.value.chapter_count) * 100)
})

const maxChapterWords = computed(() => {
  if (!stats.value || !stats.value.chapter_word_counts) return 1
  return Math.max(...stats.value.chapter_word_counts.map(c => c.word_count), 1)
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

      <!-- Character depth distribution -->
      <div v-if="stats.characters_by_depth && Object.keys(stats.characters_by_depth).length" class="stats-section">
        <h3>角色分布</h3>
        <div class="depth-bars">
          <div v-for="(count, level) in stats.characters_by_depth" :key="level" class="depth-row">
            <span class="depth-name">{{ depthLabels[level] || level }}</span>
            <div class="depth-bar-track">
              <div
                class="depth-bar-fill"
                :style="{
                  width: (count / stats.character_count * 100) + '%',
                  backgroundColor: depthColors[level] || '#9ca3af',
                }"
              ></div>
            </div>
            <span class="depth-count">{{ count }}</span>
          </div>
        </div>
      </div>

      <!-- Chapter word counts bar chart -->
      <div v-if="stats.chapter_word_counts && stats.chapter_word_counts.length" class="stats-section">
        <h3>分章字数</h3>
        <div class="chapter-bars">
          <div v-for="(c, i) in stats.chapter_word_counts" :key="i" class="chapter-bar-row">
            <span class="chapter-bar-label">{{ c.title }}</span>
            <div class="chapter-bar-track">
              <div
                class="chapter-bar-fill"
                :style="{ width: (c.word_count / maxChapterWords * 100) + '%' }"
              ></div>
            </div>
            <span class="chapter-bar-count">{{ c.word_count.toLocaleString() }}</span>
          </div>
        </div>
      </div>
    </template>
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

/* Sections */
.stats-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-color);
}
.stats-section h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 14px;
}

/* Depth bars */
.depth-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.depth-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.depth-name {
  width: 60px;
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.depth-bar-track {
  flex: 1;
  height: 20px;
  background: #f3f4f6;
  border-radius: 10px;
  overflow: hidden;
}
.depth-bar-fill {
  height: 100%;
  border-radius: 10px;
  transition: width 0.4s ease;
  min-width: 4px;
}
.depth-count {
  width: 28px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
  flex-shrink: 0;
}

/* Chapter bars */
.chapter-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chapter-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.chapter-bar-label {
  width: 80px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}
.chapter-bar-track {
  flex: 1;
  height: 16px;
  background: #f3f4f6;
  border-radius: 8px;
  overflow: hidden;
}
.chapter-bar-fill {
  height: 100%;
  background: #7c3aed;
  border-radius: 8px;
  transition: width 0.4s ease;
  min-width: 3px;
}
.chapter-bar-count {
  width: 56px;
  font-size: 12px;
  color: var(--text-secondary);
  text-align: right;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
</style>
