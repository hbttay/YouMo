import api from './index'

export function login(data) {
  return api.post('/users/login', data)
}

export function register(data) {
  return api.post('/users/register', data)
}

export function getMe() {
  return api.get('/users/me')
}

export function changePassword(data) {
  return api.put('/users/password', data)
}

export function updateProfile(data) {
  return api.put('/users/profile', data)
}
