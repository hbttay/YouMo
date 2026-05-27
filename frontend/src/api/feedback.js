import api from './index'

export function createFeedback(data) {
  return api.post('/feedback', data)
}

export function listFeedback(params) {
  return api.get('/feedback', { params })
}

export function getFeedback(id) {
  return api.get(`/feedback/${id}`)
}

export function updateFeedbackStatus(id, status) {
  return api.put(`/feedback/${id}/status`, { status })
}

export function analyzeFeedback(id) {
  return api.post(`/feedback/${id}/analyze`, null, { timeout: 60000 })
}

export function getFeedbackStats() {
  return api.get('/feedback/stats')
}

export function deleteFeedback(id) {
  return api.delete(`/feedback/${id}`)
}
