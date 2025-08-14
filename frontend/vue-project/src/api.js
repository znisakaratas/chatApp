// src/api.ts
import axios from 'axios'
import keycloak from './keycloak'

const api = axios.create({ baseURL: 'http://localhost:8080/api' }) 

api.interceptors.request.use(async (config) => {
  try { await keycloak.updateToken(30) } catch {}
  if (keycloak.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${keycloak.token}`
    localStorage.setItem('jwtToken', keycloak.token)
  }
  return config
})

export default api
