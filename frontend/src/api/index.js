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

const params = (o) => ({ params: o })

export default {
  // ---------------- 包厢 ----------------
  listRooms: (p) => call(http.get('/rooms', params(p))),
  listRoomsWithBookings: () => call(http.get('/rooms/with-bookings')),
  createRoom: (d) => call(http.post('/rooms', d)),
  updateRoomStatus: (id, target) =>
    call(http.put(`/rooms/${id}/status`, null, params({ target }))),
  checkoutRoom: (id, operator) =>
    call(http.post(`/rooms/${id}/checkout`, null, params({ operator }))),
  listRoomCleaningTasks: (id) => call(http.get(`/rooms/${id}/cleaning-tasks`)),

  // ---------------- 清场交接 ----------------
  listCleaningTasks: (p) => call(http.get('/cleaning-tasks', params(p))),
  getCleaningTask: (id) => call(http.get(`/cleaning-tasks/${id}`)),
  claimCleaningTask: (id, operator) =>
    call(http.post(`/cleaning-tasks/${id}/claim`, null, params({ operator }))),
  submitCleaningTask: (id, d) => call(http.post(`/cleaning-tasks/${id}/submit`, d)),
  resolveDeviceIssue: (id, d) => call(http.post(`/cleaning-tasks/${id}/device-resolve`, d)),
  reviewCleaningTask: (id, d) => call(http.post(`/cleaning-tasks/${id}/review`, d)),

  // ---------------- 预订 ----------------
  listBookings: (p) => call(http.get('/bookings', params(p))),
  createBooking: (d) => call(http.post('/bookings', d)),
  cancelBooking: (id) => call(http.post(`/bookings/${id}/cancel`)),

  // ---------------- 消费 ----------------
  listConsumptions: (p) => call(http.get('/consumptions', params(p))),
  createConsumption: (d) => call(http.post('/consumptions', d)),
  settleBooking: (bookingId) =>
    call(http.post('/consumptions/settle', null, params({ bookingId }))),

  // ---------------- 会员 ----------------
  listMembers: (p) => call(http.get('/members', params(p))),
  createMember: (d) => call(http.post('/members', d)),
  updateMemberLevel: (id, level) =>
    call(http.put(`/members/${id}/level`, null, params({ level }))),

  // ---------------- 歌曲 ----------------
  listSongs: (p) => call(http.get('/songs', params(p))),
  createSong: (d) => call(http.post('/songs', d))
}
