<script setup>
import { ref, computed, watch } from 'vue'
import {
  getAnnotations, resolveAnnotation, reopenAnnotation,
  deleteAnnotation, batchUpdateAnnotations,
} from '@/api/book'

const props = defineProps({
  structureId: { type: Number, required: true },
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'jump-to', 'count-change'])

const annotations = ref([])
const loading = ref(false)
const statusFilter = ref('')
const batchMode = ref(false)
const selectedIds = ref(new Set())

const statusTabs = [
  { key: '', label: '全部' },
  { key: 'OPEN', label: '待处理' },
  { key: 'RESOLVED', label: '已解决' },
  { key: 'DISMISSED', label: '已忽略' },
]

const filteredAnnotations = computed(() => {
  if (!statusFilter.value) return annotations.value
  return annotations.value.filter(a => a.status === statusFilter.value)
})

const openCount = computed(() => annotations.value.filter(a => a.status === 'OPEN').length)

async function load() {
  loading.value = true
  try {
    const res = await getAnnotations(props.structureId)
    annotations.value = res?.data || []
    emit('count-change', annotations.value.filter(a => a.status === 'OPEN').length)
  } catch { annotations.value = [] }
  finally { loading.value = false }
}

function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) { s.delete(id) } else { s.add(id) }
  selectedIds.value = s
}

function toggleSelectAll() {
  if (selectedIds.value.size === filteredAnnotations.value.length) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(filteredAnnotations.value.map(a => a.id))
  }
}

async function doResolve(id) {
  await resolveAnnotation(props.structureId, id, '')
  await load()
}

async function doReopen(id) {
  await reopenAnnotation(props.structureId, id)
  await load()
}

async function doDelete(id) {
  await deleteAnnotation(props.structureId, id)
  await load()
}

async function handleBatchResolve() {
  await batchUpdateAnnotations(props.structureId, {
    ids: [...selectedIds.value].map(id => typeof id === 'number' ? id : Number(id)),
    action: 'resolve',
    resolvedComment: '',
  })
  selectedIds.value = new Set()
  batchMode.value = false
  await load()
}

async function handleBatchDismiss() {
  await batchUpdateAnnotations(props.structureId, {
    ids: [...selectedIds.value].map(id => typeof id === 'number' ? id : Number(id)),
    action: 'dismiss',
  })
  selectedIds.value = new Set()
  batchMode.value = false
  await load()
}

const categoryLabels = {
  grammar: '语法', style: '文风', plot_hole: '剧情漏洞',
  character: '角色', suggestion: '建议',
}

watch(() => props.visible, (v) => {
  if (v) {
    load()
    statusFilter.value = ''
    batchMode.value = false
    selectedIds.value = new Set()
  }
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="as-overlay" @click.self="emit('close')">
      <div class="as-panel">
        <div class="as-header">
          <h3>批注</h3>
          <span v-if="openCount" class="as-open-count">{{ openCount }} 条待处理</span>
          <button class="as-close" @click="emit('close')">&times;</button>
        </div>

        <!-- Status filter tabs -->
        <div class="as-tabs">
          <button
            v-for="tab in statusTabs" :key="tab.key"
            class="as-tab"
            :class="{ active: statusFilter === tab.key }"
            @click="statusFilter = tab.key"
          >{{ tab.label }}</button>
          <button
            class="as-tab as-batch-toggle"
            :class="{ active: batchMode }"
            @click="batchMode = !batchMode; selectedIds = new Set()"
          >{{ batchMode ? '取消' : '批量' }}</button>
        </div>

        <div class="as-list" v-if="!loading">
          <div v-if="filteredAnnotations.length === 0" class="as-empty">
            {{ statusFilter ? '没有符合条件的批注' : '暂无批注，选中正文后可添加' }}
          </div>
          <div
            v-for="a in filteredAnnotations" :key="a.id"
            class="as-card"
            :class="{ selected: selectedIds.has(a.id) }"
          >
            <div class="as-card-top">
              <label v-if="batchMode" class="as-checkbox">
                <input type="checkbox" :checked="selectedIds.has(a.id)" @change="toggleSelect(a.id)" />
              </label>
              <span class="as-anchor-text" @click="emit('jump-to', a)">{{ a.anchor_text }}</span>
            </div>
            <div class="as-comment">{{ a.comment }}</div>
            <div class="as-meta">
              <span v-if="a.category" class="as-category">{{ categoryLabels[a.category] || a.category }}</span>
              <span v-if="a.severity && a.severity !== 'INFO'" class="as-severity" :class="'sev-' + a.severity?.toLowerCase()">
                {{ a.severity === 'MAJOR' ? '严重' : '轻微' }}
              </span>
              <span class="as-status" :class="'status-' + a.status?.toLowerCase()">
                {{ a.status === 'OPEN' ? '待处理' : a.status === 'RESOLVED' ? '已解决' : '已忽略' }}
              </span>
              <span class="as-time">{{ new Date(a.created_at).toLocaleString('zh-CN', { month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' }) }}</span>
            </div>
            <div v-if="!batchMode" class="as-actions">
              <template v-if="a.status === 'OPEN'">
                <button class="as-btn-resolve" @click="doResolve(a.id)">解决</button>
                <button class="as-btn-delete" @click="doDelete(a.id)">删除</button>
              </template>
              <template v-else-if="a.status === 'RESOLVED'">
                <button class="as-btn-reopen" @click="doReopen(a.id)">重新打开</button>
                <button class="as-btn-delete" @click="doDelete(a.id)">删除</button>
              </template>
              <template v-else>
                <button class="as-btn-reopen" @click="doReopen(a.id)">重新打开</button>
                <button class="as-btn-delete" @click="doDelete(a.id)">删除</button>
              </template>
            </div>
          </div>
        </div>
        <div v-else class="as-loading">加载中...</div>

        <!-- Batch action bar -->
        <div v-if="batchMode && selectedIds.size > 0" class="as-batch-bar">
          <span>已选 {{ selectedIds.size }} 条</span>
          <button class="as-btn-batch-resolve" @click="handleBatchResolve">批量解决</button>
          <button class="as-btn-batch-dismiss" @click="handleBatchDismiss">批量忽略</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.as-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3);
  z-index: 200; display: flex; justify-content: flex-end;
}
.as-panel {
  width: 420px; max-width: 100vw; height: 100vh;
  background: var(--bg-primary, #fff);
  display: flex; flex-direction: column;
  box-shadow: -6px 0 24px rgba(0,0,0,0.12);
}
.as-header {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 20px; border-bottom: 1px solid var(--border-color, #e5e7eb);
  flex-shrink: 0;
}
.as-header h3 { font-size: 16px; font-weight: 600; margin: 0; flex: 1; }
.as-open-count { font-size: 12px; color: #7c3aed; font-weight: 500; }
.as-close {
  width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center;
  background: none; border: none; font-size: 18px; color: var(--text-muted);
  cursor: pointer; border-radius: 4px; flex-shrink: 0;
}
.as-close:hover { background: #f3f4f6; color: var(--text-primary); }

.as-tabs {
  display: flex; gap: 4px; padding: 10px 20px;
  border-bottom: 1px solid var(--border-color, #e5e7eb); flex-shrink: 0;
}
.as-tab {
  padding: 4px 12px; font-size: 12px; font-weight: 500;
  border: 1px solid #d1d5db; border-radius: 12px;
  background: var(--bg-surface); color: var(--text-secondary);
  cursor: pointer; font-family: inherit; transition: all 0.15s;
}
.as-tab:hover { border-color: #7c3aed; color: #7c3aed; }
.as-tab.active { background: #ede9fe; border-color: #7c3aed; color: #7c3aed; font-weight: 600; }
.as-batch-toggle { margin-left: auto; }

.as-list {
  flex: 1; overflow-y: auto; padding: 12px 20px;
  display: flex; flex-direction: column; gap: 10px;
}
.as-empty { text-align: center; padding: 40px 0; color: var(--text-muted); font-size: 13px; }
.as-loading { text-align: center; padding: 40px 0; color: var(--text-muted); font-size: 13px; }

.as-card {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px; padding: 10px 14px; transition: border-color 0.15s;
}
.as-card:hover { border-color: #c4b5fd; }
.as-card.selected { border-color: #7c3aed; background: #f5f3ff; }
.as-card-top { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 6px; }
.as-checkbox { flex-shrink: 0; padding-top: 2px; }
.as-checkbox input { accent-color: #7c3aed; cursor: pointer; }
.as-anchor-text {
  font-size: 13px; color: #7c3aed; font-weight: 500;
  cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  flex: 1; min-width: 0;
}
.as-anchor-text:hover { text-decoration: underline; }
.as-comment {
  font-size: 13px; color: var(--text-primary); line-height: 1.5;
  margin-bottom: 8px; white-space: pre-wrap;
}
.as-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
.as-category {
  font-size: 10px; font-weight: 600; padding: 1px 8px;
  background: #dbeafe; color: #2563eb; border-radius: 8px;
}
.as-severity {
  font-size: 10px; font-weight: 600; padding: 1px 8px; border-radius: 8px;
}
.sev-major { background: #fee2e2; color: #dc2626; }
.sev-minor { background: #fef3c7; color: #d97706; }
.as-status { font-size: 10px; font-weight: 600; padding: 1px 8px; border-radius: 8px; }
.status-open { background: #fef3c7; color: #d97706; }
.status-resolved { background: #dcfce7; color: #16a34a; }
.status-dismissed { background: #f3f4f6; color: #9ca3af; }

.as-time { font-size: 10px; color: var(--text-muted); margin-left: auto; }

.as-actions { display: flex; gap: 6px; }
.as-btn-resolve {
  padding: 3px 12px; font-size: 11px; font-weight: 500;
  background: #059669; color: #fff; border: none; border-radius: 4px;
  cursor: pointer; font-family: inherit;
}
.as-btn-resolve:hover { background: #047857; }
.as-btn-reopen {
  padding: 3px 12px; font-size: 11px; font-weight: 500;
  background: #f3f4f6; color: var(--text-secondary);
  border: 1px solid #d1d5db; border-radius: 4px;
  cursor: pointer; font-family: inherit;
}
.as-btn-reopen:hover { background: #e5e7eb; }
.as-btn-delete {
  padding: 3px 12px; font-size: 11px; font-weight: 500;
  background: transparent; color: var(--text-muted);
  border: none; cursor: pointer; font-family: inherit;
}
.as-btn-delete:hover { color: #dc2626; }

.as-batch-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 20px; border-top: 1px solid var(--border-color);
  background: #f5f3ff; flex-shrink: 0; font-size: 13px; font-weight: 500;
}
.as-btn-batch-resolve {
  padding: 6px 16px; font-size: 12px; font-weight: 500;
  background: #059669; color: #fff; border: none; border-radius: 6px;
  cursor: pointer; font-family: inherit;
}
.as-btn-batch-resolve:hover { background: #047857; }
.as-btn-batch-dismiss {
  padding: 6px 16px; font-size: 12px; font-weight: 500;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid #d1d5db; border-radius: 6px;
  cursor: pointer; font-family: inherit;
}
.as-btn-batch-dismiss:hover { background: var(--bg-surface-hover); }
</style>
