import api from './index'

export function listBooks() {
  return api.get('/books')
}

export function getBook(id) {
  return api.get(`/books/${id}`)
}

export function updateBook(id, data) {
  return api.put(`/books/${id}`, data)
}

export function reorderBooks(bookIds) {
  return api.put('/books/reorder', bookIds)
}

export function createBook(data) {
  return api.post('/books', data)
}

export function listCharacters(bookId) {
  return api.get(`/books/${bookId}/characters`)
}

export function createCharacter(bookId, data) {
  return api.post(`/books/${bookId}/characters`, data)
}

export function updateCharacter(bookId, id, data) {
  return api.put(`/books/${bookId}/characters/${id}`, data)
}

export function deleteCharacter(bookId, id) {
  return api.delete(`/books/${bookId}/characters/${id}`)
}

export function getOutline(bookId) {
  return api.get(`/books/${bookId}/outline`)
}

export function createOutlineNode(bookId, data) {
  return api.post(`/books/${bookId}/outline/node`, data)
}

export function updateOutlineNode(bookId, id, data) {
  return api.put(`/books/${bookId}/outline/${id}`, data)
}

export function updateOutlineNodeStatus(bookId, id, status) {
  return api.put(`/books/${bookId}/outline/${id}/status`, status, {
    headers: { 'Content-Type': 'application/json' },
  })
}

export function deleteOutlineNode(bookId, id) {
  return api.delete(`/books/${bookId}/outline/${id}`)
}

export function getWorldSetting(bookId) {
  return api.get(`/books/${bookId}/world-setting`)
}

export function saveWorldSetting(bookId, data) {
  return api.put(`/books/${bookId}/world-setting`, data)
}

export function deleteBook(id) {
  return api.delete(`/books/${id}`)
}

export function getChapterContent(structureId) {
  return api.get(`/chapters/${structureId}/content`)
}

export function saveChapterContent(structureId, data) {
  return api.post(`/chapters/${structureId}/content`, data)
}

export function getVersionHistory(structureId) {
  return api.get(`/chapters/${structureId}/content/versions`)
}

export function getBookStats(bookId) {
  return api.get(`/books/${bookId}/stats`)
}

export function searchBook(bookId, query) {
  return api.get(`/books/${bookId}/search`, { params: { q: query } })
}

// ── Foreshadowing ──
export function getForeshadowings(bookId) {
  return api.get(`/books/${bookId}/foreshadowings`)
}

export function createForeshadowing(bookId, data) {
  return api.post(`/books/${bookId}/foreshadowings`, data)
}

export function updateForeshadowing(bookId, id, data) {
  return api.put(`/books/${bookId}/foreshadowings/${id}`, data)
}

export function deleteForeshadowing(bookId, id) {
  return api.delete(`/books/${bookId}/foreshadowings/${id}`)
}

export function scanForeshadowings(bookId, chapterStructureId) {
  return api.post(`/books/${bookId}/foreshadowings/scan/${chapterStructureId}`)
}

// ── Character Relationships ──
export function getCharacterRelationships(bookId) {
  return api.get(`/books/${bookId}/character-relationships`)
}

export function getCharacterRelationshipGraph(bookId) {
  return api.get(`/books/${bookId}/character-relationships/graph`)
}

export function createCharacterRelationship(bookId, data) {
  return api.post(`/books/${bookId}/character-relationships`, data)
}

export function updateCharacterRelationship(bookId, id, data) {
  return api.put(`/books/${bookId}/character-relationships/${id}`, data)
}

export function deleteCharacterRelationship(bookId, id) {
  return api.delete(`/books/${bookId}/character-relationships/${id}`)
}

// ── Style Profile ──
export function getStyleProfile(bookId) {
  return api.get(`/books/${bookId}/style-profile`)
}

export function analyzeStyleProfile(bookId) {
  return api.post(`/books/${bookId}/style-profile/analyze`)
}

// ── Annotations ──
export function getAnnotations(structureId, params) {
  return api.get(`/chapters/${structureId}/annotations`, { params })
}
export function createAnnotation(structureId, data) {
  return api.post(`/chapters/${structureId}/annotations`, data)
}
export function resolveAnnotation(structureId, id, comment) {
  return api.put(`/chapters/${structureId}/annotations/${id}/resolve`, { resolvedComment: comment || '' })
}
export function reopenAnnotation(structureId, id) {
  return api.put(`/chapters/${structureId}/annotations/${id}/reopen`)
}
export function deleteAnnotation(structureId, id) {
  return api.delete(`/chapters/${structureId}/annotations/${id}`)
}
export function batchUpdateAnnotations(structureId, data) {
  return api.put(`/chapters/${structureId}/annotations/batch`, data)
}

// ── Consistency Check ──
export function checkConsistency(structureId) {
  return api.post(`/chapters/${structureId}/consistency-check`, null, { timeout: 90000 })
}
