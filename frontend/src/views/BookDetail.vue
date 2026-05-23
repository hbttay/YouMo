<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useBookStore } from '@/stores/book'
import { updateBook } from '@/api/book'
import { STATUS_LABEL, CREATION_LABEL, LENGTH_LABEL } from '@/utils/labels'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const route = useRoute()
const store = useBookStore()

const newTaskText = ref('')
const taskSaving = ref(false)

// ── 负向约束 ──
const newConstraintText = ref('')
const constraintSaving = ref(false)

function parseConstraints() {
  if (!store.currentBook?.negative_constraints) return []
  return store.currentBook.negative_constraints
    .split('\n')
    .map(s => s.trim())
    .filter(s => s)
}

const constraints = computed(() => parseConstraints())

async function saveConstraints(newList) {
  constraintSaving.value = true
  const payload = { negative_constraints: newList.join('\n') }
  const res = await updateBook(route.params.id, payload)
  if (res && res.data) {
    store.currentBook.negative_constraints = res.data.negative_constraints
  }
  constraintSaving.value = false
}

async function addConstraint() {
  const text = newConstraintText.value.trim()
  if (!text) return
  const updated = [...constraints.value, text]
  await saveConstraints(updated)
  newConstraintText.value = ''
}

async function deleteConstraint(index) {
  const updated = constraints.value.filter((_, i) => i !== index)
  await saveConstraints(updated)
}

function parseTasks() {
  if (!store.currentBook?.extra_attributes) return []
  try {
    const parsed = typeof store.currentBook.extra_attributes === 'string'
      ? JSON.parse(store.currentBook.extra_attributes)
      : store.currentBook.extra_attributes
    return parsed.tasks || []
  } catch { return [] }
}

const tasks = computed(() => parseTasks())

const totalTasks = computed(() => tasks.value.length)
const doneTasks = computed(() => tasks.value.filter(t => t.done).length)

function genId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

async function saveTasks(newTasks) {
  taskSaving.value = true
  const payload = { extra_attributes: JSON.stringify({ tasks: newTasks }) }
  const res = await updateBook(route.params.id, payload)
  if (res && res.data) {
    store.currentBook.extra_attributes = res.data.extra_attributes
  }
  taskSaving.value = false
}

async function addTask() {
  const text = newTaskText.value.trim()
  if (!text) return
  const updated = [...tasks.value, { id: genId(), text, done: false }]
  await saveTasks(updated)
  newTaskText.value = ''
}

async function toggleTask(taskId) {
  const updated = tasks.value.map(t => t.id === taskId ? { ...t, done: !t.done } : t)
  await saveTasks(updated)
}

async function deleteTask(taskId) {
  const updated = tasks.value.filter(t => t.id !== taskId)
  await saveTasks(updated)
}

async function handleExport() {
  const token = localStorage.getItem('token')
  const resp = await fetch(`/api/books/${route.params.id}/export`, {
    headers: { 'Authorization': token ? `Bearer ${token}` : '' },
  })
  if (!resp.ok) return
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (store.currentBook?.title || 'export') + '.md'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  store.fetchBook(route.params.id)
})
</script>

<template>
  <div class="book-detail">
    <LoadingSpinner v-if="store.loading" />

    <div v-else-if="store.error" class="error">{{ store.error }}</div>

    <template v-else-if="store.currentBook">
      <div class="page-header">
        <router-link to="/" class="back-link">&larr; 返回列表</router-link>
        <button class="btn-export" @click="handleExport">导出 MD</button>
      </div>

      <div class="detail-card">
        <h1>{{ store.currentBook.title }}</h1>
        <div class="meta">
          <span class="status-tag">{{ STATUS_LABEL[store.currentBook.status] || store.currentBook.status }}</span>
          <span v-if="store.currentBook.creation_mode">{{ CREATION_LABEL[store.currentBook.creation_mode] || store.currentBook.creation_mode }}</span>
          <span v-if="store.currentBook.length_type">{{ LENGTH_LABEL[store.currentBook.length_type] || store.currentBook.length_type }}</span>
        </div>
        <p v-if="store.currentBook.core_idea" class="desc">
          {{ store.currentBook.core_idea }}
        </p>
      </div>

      <div class="nav-cards">
        <router-link :to="`/books/${route.params.id}/characters`" class="nav-card">
          <h3>角色管理</h3>
          <p>创建和管理书籍角色</p>
        </router-link>
        <router-link :to="`/books/${route.params.id}/outline`" class="nav-card">
          <h3>大纲编排</h3>
          <p>规划卷/章/节结构</p>
        </router-link>
        <router-link :to="`/books/${route.params.id}/world-setting`" class="nav-card">
          <h3>世界观设定</h3>
          <p>时代、地理、政治等背景</p>
        </router-link>
      </div>

      <!-- Task Checklist -->
      <div class="task-section">
        <div class="task-header">
          <h2>任务清单</h2>
          <span class="task-progress" v-if="totalTasks">
            {{ doneTasks }}/{{ totalTasks }}
          </span>
        </div>

        <!-- Add task -->
        <form class="task-add" @submit.prevent="addTask">
          <input
            v-model="newTaskText"
            type="text"
            class="task-input"
            placeholder="添加新任务..."
          />
          <button type="submit" class="btn-add-task" :disabled="!newTaskText.trim() || taskSaving">
            添加
          </button>
        </form>

        <!-- Task list -->
        <ul v-if="tasks.length" class="task-list">
          <li
            v-for="task in tasks"
            :key="task.id"
            class="task-item"
            :class="{ done: task.done }"
          >
            <label class="task-label">
              <input
                type="checkbox"
                :checked="task.done"
                @change="toggleTask(task.id)"
              />
              <span class="task-text">{{ task.text }}</span>
            </label>
            <button class="btn-task-del" title="删除" @click="deleteTask(task.id)">&times;</button>
          </li>
        </ul>

        <p v-else class="task-empty">暂无任务，在上方添加待办事项</p>
      </div>

      <!-- 负向约束 -->
      <div class="constraint-section">
        <div class="constraint-header">
          <h2>负向约束</h2>
          <span class="constraint-desc">AI 续写时禁用的词汇/句式</span>
        </div>

        <form class="constraint-add" @submit.prevent="addConstraint">
          <input
            v-model="newConstraintText"
            type="text"
            class="constraint-input"
            placeholder="添加禁用词或句式..."
          />
          <button type="submit" class="btn-add-constraint" :disabled="!newConstraintText.trim() || constraintSaving">
            添加
          </button>
        </form>

        <ul v-if="constraints.length" class="constraint-list">
          <li v-for="(item, idx) in constraints" :key="idx" class="constraint-item">
            <span class="constraint-text">{{ item }}</span>
            <button class="btn-constraint-del" title="删除" @click="deleteConstraint(idx)">&times;</button>
          </li>
        </ul>

        <p v-else class="constraint-empty">暂无负向约束，添加以降低 AI 套路化表达</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.btn-export {
  padding: 6px 16px;
  background: var(--bg-surface);
  color: var(--color-brand);
  border: 1px solid var(--color-brand);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.btn-export:hover { background: #f5f3ff; }

.detail-card {
  background: var(--bg-surface);
  border-radius: 8px;
  padding: 32px;
  border: 1px solid var(--border-color);
  margin-bottom: 24px;
}

.detail-card h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
}

.meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.meta span {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 4px;
  background: var(--bg-surface-hover);
  color: var(--text-secondary);
}

.desc {
  color: var(--text-secondary);
  line-height: 1.8;
}

.nav-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.nav-card {
  background: var(--bg-surface);
  border-radius: 8px;
  padding: 24px;
  border: 1px solid var(--border-color);
  text-decoration: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.nav-card:hover {
  border-color: #5b3cc4;
  box-shadow: 0 2px 12px rgba(91, 60, 196, 0.12);
}

.nav-card h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.nav-card p {
  font-size: 13px;
  color: #888;
}

/* ── Task Checklist ── */
.task-section {
  background: var(--bg-surface);
  border-radius: 10px;
  border: 1px solid var(--border-color);
  padding: 24px 28px;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.task-header h2 {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.task-progress {
  font-size: 13px;
  color: #5b3cc4;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.task-add {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.task-input {
  flex: 1;
  padding: 9px 14px;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.task-input:focus {
  outline: none;
  border-color: #5b3cc4;
  box-shadow: 0 0 0 3px rgba(91, 60, 196, 0.08);
}

.btn-add-task {
  padding: 9px 18px;
  background: #5b3cc4;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
  transition: background 0.15s;
}

.btn-add-task:hover:not(:disabled) {
  background: #4a2fa8;
}

.btn-add-task:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.task-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  transition: background 0.12s;
}

.task-item:hover {
  background: var(--bg-surface-hover);
}

.task-item.done .task-text {
  text-decoration: line-through;
  color: var(--text-muted);
}

.task-label {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  min-width: 0;
}

.task-label input[type="checkbox"] {
  width: 17px;
  height: 17px;
  accent-color: #5b3cc4;
  cursor: pointer;
  flex-shrink: 0;
}

.task-text {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.5;
  word-break: break-word;
}

.btn-task-del {
  background: none;
  border: none;
  color: #d1d5db;
  font-size: 18px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1;
  transition: color 0.12s, background 0.12s;
  font-family: inherit;
  flex-shrink: 0;
}

.btn-task-del:hover {
  color: #dc2626;
  background: #fef2f2;
}

.task-empty {
  color: var(--text-muted);
  font-size: 14px;
  text-align: center;
  padding: 20px 0;
  margin: 0;
}

/* ── 负向约束 ── */
.constraint-section {
  background: var(--bg-surface);
  border-radius: 10px;
  border: 1px solid var(--border-color);
  padding: 24px 28px;
  margin-top: 24px;
}

.constraint-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.constraint-header h2 {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.constraint-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.constraint-add {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.constraint-input {
  flex: 1;
  padding: 9px 14px;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.constraint-input:focus {
  outline: none;
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.08);
}

.btn-add-constraint {
  padding: 9px 18px;
  background: #ef4444;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
  transition: background 0.15s;
}

.btn-add-constraint:hover:not(:disabled) {
  background: #dc2626;
}

.btn-add-constraint:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.constraint-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.constraint-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 20px;
  font-size: 13px;
  transition: background 0.12s;
}

.constraint-item:hover {
  background: #fee2e2;
}

.constraint-text {
  color: #991b1b;
  font-size: 13px;
}

.btn-constraint-del {
  background: none;
  border: none;
  color: #f87171;
  font-size: 16px;
  cursor: pointer;
  padding: 0 2px;
  border-radius: 50%;
  line-height: 1;
  transition: color 0.12s;
  font-family: inherit;
}

.btn-constraint-del:hover {
  color: #dc2626;
}

.constraint-empty {
  color: var(--text-muted);
  font-size: 14px;
  text-align: center;
  padding: 20px 0;
  margin: 0;
}
</style>
