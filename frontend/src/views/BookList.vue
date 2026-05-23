<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore } from '@/stores/book'
import { deleteBook } from '@/api/book'
import { useRequest } from '@/composables/useRequest'
import ModalConfirm from '@/components/ModalConfirm.vue'
import { STATUS_LABEL, CREATION_LABEL } from '@/utils/labels'

const router = useRouter()
const store = useBookStore()
const deleteTarget = ref(null)

onMounted(() => {
  store.fetchBooks()
})

const { error, execute } = useRequest(deleteBook)

function goToBook(id) {
  router.push(`/books/${id}`)
}

async function handleDelete(book, e) {
  e.stopPropagation()
  deleteTarget.value = book
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  await execute(deleteTarget.value.id)
  if (!error.value) {
    deleteTarget.value = null
    store.fetchBooks()
  }
}

function cancelDelete() {
  deleteTarget.value = null
}
</script>

<template>
  <div class="book-list">
    <div class="page-header">
      <h1>我的书籍</h1>
      <router-link to="/books/create" class="btn-primary">+ 新建书籍</router-link>
    </div>

    <div v-if="store.loading" class="loading">加载中...</div>

    <div v-else-if="store.error" class="error">{{ store.error }}</div>

    <div v-else-if="store.books.length === 0" class="empty">
      <p>还没有书籍，创建你的第一本书吧</p>
      <router-link to="/books/create" class="btn-primary">新建书籍</router-link>
    </div>

    <div v-else class="book-grid">
      <div
        v-for="book in store.books"
        :key="book.id"
        class="book-card"
        @click="goToBook(book.id)"
      >
        <span class="book-title">{{ book.title }}</span>
        <div class="book-meta">
          <span class="status-tag">{{ STATUS_LABEL[book.status] || book.status || '草稿' }}</span>
          <span v-if="book.creation_mode" class="mode-tag">{{ CREATION_LABEL[book.creation_mode] || book.creation_mode }}</span>
        </div>
        <p v-if="book.core_idea" class="book-desc">{{ book.core_idea }}</p>
        <button class="btn-sm btn-danger" @click="handleDelete(book, $event)">删除</button>
      </div>
    </div>
  </div>

  <ModalConfirm
    :visible="deleteTarget !== null"
    title="确认删除"
    :message="`确定删除书籍「${deleteTarget?.title}」？此操作不可撤销。`"
    :danger="true"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
}

.empty {
  text-align: center;
  padding: 60px 20px;
  color: #888;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.book-card {
  position: relative;
  background: var(--bg-surface);
  border-radius: 8px;
  padding: 20px 20px 44px;
  border: 1px solid var(--border-color);
  transition: box-shadow 0.2s;
  cursor: pointer;
}

.book-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.book-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  display: block;
  margin-bottom: 8px;
}

.book-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.status-tag, .mode-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--bg-surface-hover);
  color: var(--text-secondary);
}

.book-desc {
  font-size: 13px;
  color: #888;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-card .btn-danger {
  position: absolute;
  bottom: 12px;
  right: 12px;
}
</style>
