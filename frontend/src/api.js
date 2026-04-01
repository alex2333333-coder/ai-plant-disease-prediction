const BASE_URL = import.meta.env.VITE_BASE_URL || 'http://localhost:8080'

function normalizeBaseUrl(url) {
  // 保证形如「协议://域名:端口」；避免双端口/多斜杠。
  return String(url).replace(/\/+$/, '')
}

export const API_BASE_URL = normalizeBaseUrl(BASE_URL)

async function request(path, options) {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers || {}),
    },
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok) {
    const msg = json?.message || `HTTP ${res.status}`
    throw new Error(msg)
  }
  return json
}

export async function fetchConfig() {
  return request('/api/v1/config', { method: 'GET' })
}

export async function fetchRecords(params) {
  const qs = new URLSearchParams(params || {}).toString()
  return request(`/api/v1/records?${qs}`, { method: 'GET' })
}

export async function recognizeByImageBase64({ imageBase64, cropType }) {
  // cropType 可选：用于后端动态替换专业Prompt
  return request('/api/v1/recognize', {
    method: 'POST',
    body: JSON.stringify({ imageBase64, cropType: cropType || undefined }),
  })
}

