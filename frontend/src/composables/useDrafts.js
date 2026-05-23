import { ref } from 'vue'

const STORAGE_KEY = 'youmo_drafts'

function loadAll() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]') } catch { return [] }
}

function saveAll(drafts) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(drafts))
}

export function useDrafts(bookId) {
  const drafts = ref(loadAll().filter(d => d.bookId === bookId || d.type === 'book-idea'))

  function add(type, data, label) {
    const all = loadAll()
    all.push({
      id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      bookId,
      type,
      data,
      label: label || type,
      createdAt: new Date().toISOString(),
    })
    saveAll(all)
    refresh()
  }

  function remove(draftId) {
    const all = loadAll().filter(d => d.id !== draftId)
    saveAll(all)
    refresh()
  }

  function refresh() {
    drafts.value = loadAll().filter(d => d.bookId === bookId || d.type === 'book-idea')
  }

  return { drafts, add, remove, refresh }
}
