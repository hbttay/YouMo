<script setup>
import { ref, reactive } from 'vue'
import { useDrafts } from '@/composables/useDrafts'

const props = defineProps({
  bookId: { type: [String, Number], required: true },
  type: { type: String, required: true },  // 'character' | 'world-setting' | 'outline'
})

const emit = defineEmits(['apply'])

const { drafts, remove, refresh } = useDrafts(props.bookId)
const open = ref(false)
const expandedIds = reactive(new Set())

const typeLabels = {
  'character': '角色',
  'world-setting': '世界观',
  'outline': '大纲',
  'book-idea': '书名创意',
}

function filtered() {
  return drafts.value.filter(d => d.type === props.type)
}

function toggleExpand(id) {
  if (expandedIds.has(id)) { expandedIds.delete(id) } else { expandedIds.add(id) }
}

function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const fieldLabels = {
  name: '姓名', gender: '性别', age_description: '年龄段',
  appearance: '外貌', origin: '出身', identity: '身份',
  race: '种族', depth_level: '深度',
}

function formatDraftValue(key, val) {
  if (val == null || val === '') return null
  const label = fieldLabels[key] || key
  return { label, value: typeof val === 'object' ? JSON.stringify(val) : String(val) }
}

function handleApply(draft) {
  emit('apply', draft.data)
  open.value = false
}

function handleDelete(id) {
  remove(id)
}
</script>

<template>
  <!-- Toggle button -->
  <button class="btn-drafts" @click="open = true; refresh()">
    草稿箱
    <span v-if="filtered().length" class="draft-count">{{ filtered().length }}</span>
  </button>

  <!-- Slide-over panel -->
  <Teleport to="body">
    <Transition name="slide">
      <div v-if="open" class="drafts-overlay" @click.self="open = false">
        <div class="drafts-panel">
          <div class="drafts-header">
            <h2>草稿箱 · {{ typeLabels[type] || type }}</h2>
            <button class="drafts-close" @click="open = false">&times;</button>
          </div>

          <div class="drafts-body">
            <div v-if="filtered().length === 0" class="drafts-empty">
              暂无草稿
            </div>
            <div v-for="d in filtered()" :key="d.id" class="draft-item" :class="{ expanded: expandedIds.has(d.id) }">
              <div class="draft-row" @click="toggleExpand(d.id)">
                <span class="expand-arrow">{{ expandedIds.has(d.id) ? '▾' : '▸' }}</span>
                <div class="draft-info">
                  <span class="draft-label">{{ d.label }}</span>
                  <span class="draft-time">{{ formatDate(d.createdAt) }}</span>
                </div>
                <div class="draft-actions" @click.stop>
                  <button class="btn-sm btn-apply" @click="handleApply(d)">应用</button>
                  <button class="btn-sm btn-delete" @click="handleDelete(d.id)">删除</button>
                </div>
              </div>
              <div v-if="expandedIds.has(d.id)" class="draft-preview">
                <div v-for="(v, k) in d.data" :key="k">
                  <template v-if="formatDraftValue(k, v)">
                    <span class="dp-label">{{ formatDraftValue(k, v).label }}</span>
                    <span class="dp-value">{{ formatDraftValue(k, v).value }}</span>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.btn-drafts {
  position: relative;
  padding: 8px 18px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}
.btn-drafts:hover { border-color: var(--color-brand); color: var(--color-brand); }
.draft-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--color-brand);
  color: #fff;
  font-size: 11px;
  margin-left: 6px;
  padding: 0 5px;
}

/* ── Overlay & panel ── */
.drafts-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 1500;
  display: flex;
  justify-content: flex-end;
}
.drafts-panel {
  width: 380px;
  max-width: 100vw;
  height: 100vh;
  background: var(--bg-surface);
  box-shadow: -4px 0 24px rgba(0,0,0,0.12);
  display: flex;
  flex-direction: column;
}
.drafts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.drafts-header h2 { margin: 0; font-size: 16px; font-weight: 700; }
.drafts-close {
  background: none; border: none; font-size: 24px; color: #888; cursor: pointer; padding: 0; line-height: 1;
}
.drafts-body {
  flex: 1; overflow-y: auto; padding: 16px 24px;
}
.drafts-empty {
  text-align: center; padding: 60px 0; color: #aaa; font-size: 14px;
}

/* ── Draft item ── */
.draft-item {
  border-bottom: 1px solid var(--border-color-light, #f0f0f0);
}
.draft-item.expanded {
  background: #f5f3ff; margin: 0 -24px; padding: 0 24px;
}
.draft-row {
  display: flex; align-items: center; gap: 6px;
  padding: 12px 0; cursor: pointer; user-select: none;
}
.expand-arrow { font-size: 10px; color: var(--text-muted); flex-shrink: 0; width: 12px; }
.draft-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.draft-label { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.draft-time { font-size: 12px; color: #aaa; }
.draft-actions { display: flex; gap: 8px; flex-shrink: 0; }

/* ── Draft preview ── */
.draft-preview {
  padding: 0 18px 14px; display: flex; flex-direction: column; gap: 6px;
}
.draft-preview > div {
  display: flex; gap: 8px; font-size: 13px; line-height: 1.5;
}
.dp-label {
  flex-shrink: 0; min-width: 48px; font-weight: 600; color: var(--text-secondary);
}
.dp-value { color: var(--text-primary); }
.btn-sm {
  padding: 5px 14px; border-radius: 6px; font-size: 12px; font-weight: 500; cursor: pointer; font-family: inherit;
  border: none; transition: background 0.15s;
}
.btn-apply { background: var(--color-brand); color: #fff; }
.btn-apply:hover { background: var(--color-brand-hover); }
.btn-delete { background: #fee2e2; color: #dc2626; }
.btn-delete:hover { background: #fecaca; }

/* Transition */
.slide-enter-active, .slide-leave-active { transition: opacity 0.2s ease; }
.slide-enter-active .drafts-panel, .slide-leave-active .drafts-panel { transition: transform 0.2s ease; }
.slide-enter-from, .slide-leave-to { opacity: 0; }
.slide-enter-from .drafts-panel { transform: translateX(100%); }
.slide-leave-to .drafts-panel { transform: translateX(100%); }
</style>
