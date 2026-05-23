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
