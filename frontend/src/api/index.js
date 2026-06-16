import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

// 请求拦截器：自动带 Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：401 跳登录
api.interceptors.response.use(
  res => res.data,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default {
  // 用户
  login: data => api.post('/user/login', data),
  register: data => api.post('/user/register', data),
  getUserInfo: () => api.get('/user/info'),
  // 文件
  uploadFile: formData => api.post('/user/file/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  getFileList: () => api.get('/user/file/list'),
  // 解析
  triggerParse: data => api.post('/data/trigger-parse', data),
  getParseStatus: fileId => api.get(`/data/status/${fileId}`),
  getPreview: fileId => api.get(`/data/preview/${fileId}`),
  // AI
  aiQuery: data => api.post('/ai/query', data),
  aiChat: data => api.post('/ai/chat', data),
  getConversations: () => api.get('/ai/conversations'),
  // 可视化
  getChart: (type, fileId, params) => api.post(`/viz/chart/${type}/${fileId}`, params),
  getReport: fileId => api.get(`/viz/report/${fileId}`),
  getTemplates: () => api.get('/viz/templates'),
}
