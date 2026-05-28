<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useBookStore } from '@/stores/book'
import { deleteBook, reorderBooks } from '@/api/book'
import { useRequest } from '@/composables/useRequest'
import ModalConfirm from '@/components/ModalConfirm.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { STATUS_LABEL, CREATION_LABEL, LENGTH_LABEL } from '@/utils/labels'

const router = useRouter()
const route = useRoute()
const store = useBookStore()
const deleteTarget = ref(null)

const sortBy = ref('sequence')
const filterStatus = ref('')

const draggedIndex = ref(-1)
const dragOverIndex = ref(-1)

const STATUS_COLORS = {
  DRAFT: { bg: '#f3f4f6', color: '#6b7280' },
  SERIALIZING: { bg: '#dbeafe', color: '#1d4ed8' },
  COMPLETED: { bg: '#d1fae5', color: '#065f46' },
  ARCHIVED: { bg: '#fce7f3', color: '#9d174d' },
}

onMounted(() => {
  const s = route.query.status
  if (s === 'in_progress') filterStatus.value = 'in_progress'
  else if (s) filterStatus.value = s
  store.fetchBooks()
})

const { execute } = useRequest(deleteBook)

const sortedBooks = computed(() => {
  let list = [...store.books]
  if (filterStatus.value === 'in_progress') list = list.filter(b => b.status === 'DRAFT' || b.status === 'SERIALIZING')
  else if (filterStatus.value) list = list.filter(b => b.status === filterStatus.value)
  if (sortBy.value === 'updated') {
    list.sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0))
  } else if (sortBy.value === 'title') {
    list.sort((a, b) => (a.title || '').localeCompare(b.title || '', 'zh'))
  } else if (sortBy.value === 'created') {
    list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
  }
  // 'sequence' mode: use backend order (already sorted by API), no additional sort
  return list
})

// Drag & drop
function onDragStart(idx, e) {
  draggedIndex.value = idx
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', idx)
}

function onDragOver(idx, e) {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'move'
  dragOverIndex.value = idx
}

function onDragLeave(idx) {
  if (dragOverIndex.value === idx) dragOverIndex.value = -1
}

async function onDrop(idx) {
  const from = draggedIndex.value
  draggedIndex.value = -1
  dragOverIndex.value = -1
  if (from === idx || from < 0) return

  const list = store.books
  const item = list[from]
  list.splice(from, 1)
  list.splice(idx, 0, item)

  // Clear non-sequence sort so user sees the new order
  sortBy.value = 'sequence'
  filterStatus.value = ''

  try {
    await reorderBooks(list.map(b => b.id))
  } catch {
    store.fetchBooks() // revert on failure
  }
}

function onDragEnd() {
  draggedIndex.value = -1
  dragOverIndex.value = -1
}

function formatDate(d) {
  if (!d) return ''
  const date = new Date(d)
  const now = new Date()
  const diff = now - date
  const days = Math.floor(diff / 86400000)
  if (days < 1) return '今天'
  if (days < 7) return `${days} 天前`
  return date.toLocaleDateString('zh-CN')
}

function goToBook(id) { router.push(`/books/${id}`) }
function handleDelete(book, e) { e.stopPropagation(); deleteTarget.value = book }

async function confirmDelete() {
  if (!deleteTarget.value) return
  await execute(deleteTarget.value.id)
  deleteTarget.value = null
  store.fetchBooks()
}
function cancelDelete() { deleteTarget.value = null }
</script>

<template>
  <div class="book-list">
    <div class="page-header">
      <router-link to="/" class="back-link">&larr; 返回首页</router-link>
      <h1>我的书籍</h1>
      <router-link to="/books/create" class="btn-primary">+ 新建书籍</router-link>
    </div>

    <LoadingSpinner v-if="store.loading" />
    <div v-else-if="store.error" class="error">{{ store.error }}</div>

    <div v-else-if="store.books.length === 0" class="empty">
      <p>还没有书籍，创建你的第一本书吧</p>
      <router-link to="/books/create" class="btn-primary">新建书籍</router-link>
    </div>

    <template v-else>
      <div class="toolbar">
        <select v-model="filterStatus" class="filter-select">
          <option value="">全部状态</option>
          <option v-for="(label, key) in STATUS_LABEL" :key="key" :value="key">{{ label }}</option>
        </select>
        <div class="sort-group">
          <span class="sort-label">排序：</span>
          <button class="sort-btn" :class="{ active: sortBy === 'sequence' }" @click="sortBy = 'sequence'">我的顺序</button>
          <button class="sort-btn" :class="{ active: sortBy === 'updated' }" @click="sortBy = 'updated'">最近更新</button>
          <button class="sort-btn" :class="{ active: sortBy === 'created' }" @click="sortBy = 'created'">创建时间</button>
          <button class="sort-btn" :class="{ active: sortBy === 'title' }" @click="sortBy = 'title'">书名</button>
        </div>
      </div>

      <div class="book-grid">
        <div
          v-for="(book, idx) in sortedBooks"
          :key="book.id"
          class="book-card"
          :class="{ 'is-dragging': draggedIndex === idx, 'drag-over-before': dragOverIndex === idx && draggedIndex !== idx }"
          draggable="true"
          @click="goToBook(book.id)"
          @dragstart="onDragStart(idx, $event)"
          @dragover="onDragOver(idx, $event)"
          @dragleave="onDragLeave(idx)"
          @drop="onDrop(idx)"
          @dragend="onDragEnd"
        >
          <span class="drag-handle" @mousedown.stop @click.stop title="拖动排序">⠿</span>
          <div class="card-top">
            <span class="book-title">{{ book.title }}</span>
            <span class="status-tag"
              :style="{ background: (STATUS_COLORS[book.status] || STATUS_COLORS.DRAFT).bg, color: (STATUS_COLORS[book.status] || STATUS_COLORS.DRAFT).color }">
              {{ STATUS_LABEL[book.status] || '草稿' }}
            </span>
          </div>
          <p v-if="book.core_idea" class="book-desc">{{ book.core_idea }}</p>
          <div class="card-meta">
            <span v-if="book.lengthType" class="meta-item">{{ LENGTH_LABEL[book.lengthType] || book.lengthType }}</span>
            <span v-if="book.creationMode" class="meta-item">{{ CREATION_LABEL[book.creationMode] || book.creationMode }}</span>
            <span v-if="book.updatedAt" class="meta-item">{{ formatDate(book.updatedAt) }}</span>
          </div>
          <button class="btn-delete" @click="handleDelete(book, $event)" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2m3 0v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6h14"/></svg>
          </button>
        </div>
      </div>
    </template>

    <ModalConfirm
      :visible="deleteTarget !== null"
      title="确认删除"
      :message="`确定删除书籍「${deleteTarget?.title}」？此操作不可撤销。`"
      :danger="true"
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />
  </div>
</template>

<style scoped>
.book-list { max-width: 1140px; margin: 0 auto; }

.page-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 24px;
}
.back-link { color: #5b3cc4; text-decoration: none; font-size: 13px; }
.back-link:hover { color: #4a2fa8; }
.page-header h1 { font-size: 24px; font-weight: 600; color: var(--text-primary); margin: 0; }

/* Toolbar */
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.filter-select { padding: 7px 10px; font-size: 13px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-surface); color: var(--text-primary); font-family: inherit; }
.sort-group { display: flex; align-items: center; gap: 8px; }
.sort-label { font-size: 13px; color: var(--text-muted); }
.sort-btn { padding: 4px 12px; font-size: 12px; background: transparent; color: var(--text-secondary); border: 1px solid var(--border-color); border-radius: 14px; cursor: pointer; font-family: inherit; transition: all 0.15s; }
.sort-btn:hover { border-color: var(--color-brand); color: var(--color-brand); }
.sort-btn.active { background: #f5f3ff; border-color: var(--color-brand); color: var(--color-brand); }

/* Empty */
.empty { text-align: center; padding: 60px 20px; color: var(--text-muted); }

/* Grid */
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }

/* Card */
.book-card {
  position: relative; background: var(--bg-surface); border-radius: 8px;
  padding: 20px 20px 44px; border: 1px solid var(--border-color);
  transition: box-shadow 0.2s; cursor: pointer;
}
.book-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
.card-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.book-title { font-size: 17px; font-weight: 600; color: var(--text-primary); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: 12px; white-space: nowrap; flex-shrink: 0; }

.book-desc {
  font-size: 13px; color: #888; margin: 0 0 8px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta { display: flex; gap: 10px; flex-wrap: wrap; }
.meta-item { font-size: 12px; color: var(--text-muted); }

/* Delete — hover reveal */
.btn-delete {
  position: absolute; bottom: 12px; right: 12px;
  width: 30px; height: 30px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  background: transparent; color: #9ca3af; border: none; cursor: pointer;
  opacity: 0; transition: opacity 0.15s, color 0.15s, background 0.15s;
  padding: 0;
}
.book-card:hover .btn-delete { opacity: 1; }
.btn-delete:hover { color: #dc2626; background: #fee2e2; }

/* Drag & drop */
.drag-handle {
  position: absolute; top: 6px; left: 8px;
  font-size: 18px; color: #c4b5fd; cursor: grab; user-select: none;
  opacity: 0; transition: opacity 0.15s; line-height: 1;
}
.book-card:hover .drag-handle { opacity: 1; }
.drag-handle:active { cursor: grabbing; color: var(--color-brand); }

.book-card.is-dragging { opacity: 0.4; }
.book-card.drag-over-before {
  border-top: 2px solid var(--color-brand);
  border-top-left-radius: 0; border-top-right-radius: 0;
}
</style>
