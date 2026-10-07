import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const params = (o) => ({ params: o || {} })

// 去除对象中的空值，避免把 null/'' 当作查询条件发出去
function clean(o) {
  const r = {}
  if (o) {
    for (const k of Object.keys(o)) {
      if (o[k] !== null && o[k] !== undefined && o[k] !== '') r[k] = o[k]
    }
  }
  return r
}

export default {
  // ---------------- 蜂箱 ----------------
  listHives: (status) => call(http.get('/hives', params(status ? { status } : {}))),
  createHive: (d) => call(http.post('/hives', d)),
  updateHive: (id, d) => call(http.put(`/hives/${id}`, d)),
  deleteHive: (id) => call(http.delete(`/hives/${id}`)),

  // ---------------- 巡检 ----------------
  listInspections: (opts) => call(http.get('/inspections', params(clean(opts)))),
  createInspection: (d) => call(http.post('/inspections', d)),
  updateInspection: (id, d) => call(http.put(`/inspections/${id}`, d)),
  recheckInspection: (id) => call(http.post(`/inspections/${id}/recheck`)),

  // ---------------- 采蜜批次 ----------------
  listHoneyBatches: (status) => call(http.get('/honey-batches', params(status ? { status } : {}))),
  createHoneyBatch: (d) => call(http.post('/honey-batches', d)),
  updateHoneyBatch: (id, d) => call(http.put(`/honey-batches/${id}`, d)),
  stockInHoneyBatch: (id) => call(http.post(`/honey-batches/${id}/stock-in`)),
  deleteHoneyBatch: (id) => call(http.delete(`/honey-batches/${id}`)),

  // ---------------- 蜂蜜批次质检放行 ----------------
  listQualityBatches: (opts) => call(http.get('/quality-inspections', params(clean(opts)))),
  getQualityBatch: (batchId) => call(http.get(`/quality-inspections/batches/${batchId}`)),
  submitInitialQuality: (batchId, d) => call(http.post(`/quality-inspections/batches/${batchId}/initial`, d)),
  submitQualityRecheck: (batchId, d) => call(http.post(`/quality-inspections/batches/${batchId}/recheck`, d)),
  submitRectification: (batchId, d) => call(http.post(`/quality-inspections/batches/${batchId}/rectification`, d)),

  // ---------------- 客户 ----------------
  listCustomers: (type) => call(http.get('/customers', params(type ? { type } : {}))),
  createCustomer: (d) => call(http.post('/customers', d)),
  updateCustomer: (id, d) => call(http.put(`/customers/${id}`, d)),
  deleteCustomer: (id) => call(http.delete(`/customers/${id}`))
}
