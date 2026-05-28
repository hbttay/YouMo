<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { searchBook } from '@/api/book'

const props = defineProps({
  bookId: { type: [String, Number], required: true },
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])
const router = useRouter()

const query = ref('')
const loading = ref(false)
const results = ref(null)
const error = ref('')

const typeIcons = { chapter: '📄', outline: '📑', character: '👤', world: '🌍' }
const typeLabels = { chapter: '正文', outline: '大纲', character: '角色', world: '世界观' }

let debounce = null

watch(() => props.visible, (v) => {
  if (v) {
    query.value = ''
    results.value = null
    error.value = ''
  }
})

function onInput() {
  if (debounce) clearTimeout(debounce)
  if (query.value.trim().length < 2) {
    results.value = null
    return
  }
  debounce = setTimeout(doSearch, 300)
}

async function doSearch() {
  const q = query.value.trim()
  if (q.length < 2) return
  loading.value = true
  error.value = ''
  try {
    const res = await searchBook(props.bookId, q)
    results.value = res.data || res
  } catch (e) {
    error.value = e.message || '搜索失败'
    results.value = null
  } finally {
    loading.value = false
  }
}

function navigate(match) {
  if (match.type === 'character' && match.character_id) {
    router.push(`/books/${props.bookId}/characters`)
  } else if ((match.type === 'chapter' || match.type === 'outline') && match.structure_id) {
    router.push(`/books/${props.bookId}/write/${match.structure_id}?title=${encodeURIComponent(match.title)}&node_type=${match.node_type || 'SCENE'}`)
  } else if (match.type === 'world') {
    router.push(`/books/${props.bookId}/world`)
  }
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="search-overlay" @click.self="emit('close')">
      <div class="search-modal">
        <div class="search-input-row">
          <span class="search-icon">🔍</span>
          <input
            ref="inputRef"
            v-model="query"
            type="text"
            class="search-input"
            placeholder="搜索章节内容、角色、世界观..."
            @input="onInput"
            @keyup.enter="doSearch"
          />
          <button class="btn-search-close" @click="emit('close')">✕</button>
        </div>

        <div v-if="loading" class="search-status">搜索中...</div>
        <div v-else-if="error" class="search-status error">{{ error }}</div>
        <div v-else-if="results && results.total_matches === 0" class="search-status">未找到匹配结果</div>

        <div v-else-if="results" class="search-results">
          <div class="search-count">{{ results.total_matches }} 个结果</div>
          <div
            v-for="(m, i) in results.matches"
            :key="i"
            class="search-item"
            @click="navigate(m)"
          >
            <span class="search-item-type">{{ typeIcons[m.type] || '📄' }} {{ typeLabels[m.type] || m.type }}</span>
            <span class="search-item-title">{{ m.title }}</span>
            <span v-if="m.snippet" class="search-item-snippet">{{ m.snippet }}</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.search-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.4);
  display: flex; justify-content: center; padding-top: 15vh;
}
.search-modal {
  background: var(--bg-surface);
  border-radius: 12px;
  width: 600px; max-width: 95vw;
  max-height: 70vh;
  display: flex; flex-direction: column;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  overflow: hidden;
}
.search-input-row {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 20px; border-bottom: 1px solid var(--border-color);
}
.search-icon { font-size: 18px; flex-shrink: 0; }
.search-input {
  flex: 1; border: none; outline: none; font-size: 16px;
  background: transparent; color: var(--text-primary);
}
.search-input::placeholder { color: var(--text-secondary); }
.btn-search-close {
  width: 28px; height: 28px; border: none; background: none;
  font-size: 16px; cursor: pointer; color: var(--text-secondary);
  border-radius: 4px; display: flex; align-items: center; justify-content: center;
}
.btn-search-close:hover { background: var(--bg-surface-hover); }

.search-status { padding: 32px 20px; text-align: center; color: var(--text-secondary); font-size: 14px; }
.search-status.error { color: #dc2626; }
.search-results { overflow-y: auto; }
.search-count { padding: 10px 20px; font-size: 12px; color: var(--text-secondary); border-bottom: 1px solid var(--border-color); }
.search-item {
  display: flex; flex-direction: column; gap: 2px;
  padding: 12px 20px; cursor: pointer;
  border-bottom: 1px solid var(--border-color);
  transition: background 0.15s;
}
.search-item:hover { background: var(--bg-surface-hover); }
.search-item-type { font-size: 11px; color: var(--color-brand); }
.search-item-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.search-item-snippet { font-size: 12px; color: var(--text-secondary); line-height: 1.5; }
</style>
