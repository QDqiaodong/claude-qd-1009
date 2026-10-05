<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const props = defineProps({ filter: Object })
const emit = defineEmits(['goto-cleaning'])

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const statusFilter = ref('')
const dialog = ref(false)
const saving = ref(false)
const statuses = ['空闲', '预订', '使用中', '待清场']

// 散场清场弹窗
const checkoutDialog = ref(false)
const checkoutTarget = ref(null)
const checkoutOperator = ref(localStorage.getItem('cleaning-operator') || '')

// 包厢详情抽屉（含清场交接记录追溯）
const drawer = ref(false)
const detail = ref(null)
const detailTasks = ref([])
const detailLoading = ref(false)

const form = ref(blank())
function blank() {
  return { roomCode: '', roomName: '', capacity: '', minSpend: '', status: '空闲' }
}

// 推荐：容量达标 且 当前可接待（空闲且无未闭环清场任务）
const recommendedSet = computed(() => {
  const ps = (props.filter && props.filter.partySize) || 1
  return new Set(rows.value.filter(r => r.capacity >= ps && r.available).map(r => r.id))
})

const filteredRows = computed(() =>
  statusFilter.value ? rows.value.filter(r => r.status === statusFilter.value) : rows.value)

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listRoomsWithBookings()
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = {
      ...form.value,
      capacity: Number(form.value.capacity),
      minSpend: Number(form.value.minSpend)
    }
    await api.createRoom(d)
    tip.value = `包厢 ${d.roomName} 已建档`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function changeStatus(row, target) {
  error.value = ''; tip.value = ''
  try {
    await api.updateRoomStatus(row.id, target)
    tip.value = `${row.roomName} 已置为「${target}」`
    await load()
  } catch (e) { error.value = e.message; await load() }
}

// ---------------- 散场清场 ----------------
function openCheckout(row) {
  checkoutTarget.value = row
  checkoutDialog.value = true
  error.value = ''; tip.value = ''
}

async function doCheckout() {
  const row = checkoutTarget.value
  saving.value = true; error.value = ''
  try {
    await api.checkoutRoom(row.id, checkoutOperator.value.trim() || '前台')
    tip.value = `${row.roomName} 已散场，清场任务已生成；验收放行前暂停接待`
    checkoutDialog.value = false
    await load()
  } catch (e) { error.value = e.message; await load() } finally { saving.value = false }
}

// ---------------- 详情追溯 ----------------
async function openDetail(row) {
  drawer.value = true
  detail.value = row
  detailTasks.value = []
  detailLoading.value = true
  try {
    detailTasks.value = (await api.listRoomCleaningTasks(row.id)) || []
  } catch (e) { error.value = e.message } finally { detailLoading.value = false }
}

function cleaningTagType(status) {
  return {
    待领取: 'info', 清场中: 'warning', 待验收: 'primary',
    整改中: 'danger', 已放行: 'success'
  }[status] || 'info'
}

function logTagType(action) {
  return {
    生成任务: 'info', 领取: 'warning', 提交清场: 'primary',
    验收放行: 'success', 退回整改: 'danger', 异常处置: 'warning'
  }[action] || 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">包厢 · 房态与容量</span>
        <span class="panel-desc">刻度尺选定 {{ filter?.partySize || 1 }} 人，容量达标且可接待的包厢标「推荐」；待清场包厢验收放行前不可订</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">包厢数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">空闲</div><div class="v">{{ rows.filter(r => r.status === '空闲').length }}</div></div>
        <div class="stat"><div class="k">预订</div><div class="v">{{ rows.filter(r => r.status === '预订').length }}</div></div>
        <div class="stat"><div class="k">使用中</div><div class="v">{{ rows.filter(r => r.status === '使用中').length }}</div></div>
        <div class="stat"><div class="k">待清场</div><div class="v">{{ rows.filter(r => r.status === '待清场').length }}</div></div>
        <div class="stat"><div class="k">可接待</div><div class="v">{{ rows.filter(r => r.available).length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建包厢</el-button>
      </div>

      <el-table :data="filteredRows" v-loading="loading" border stripe>
        <el-table-column prop="roomCode" label="编号" width="90" />
        <el-table-column prop="roomName" label="名称" min-width="110" />
        <el-table-column prop="capacity" label="容量(人)" width="90" />
        <el-table-column prop="minSpend" label="最低消费(元)" width="120" />
        <el-table-column label="预订数" width="90">
          <template #default="{ row }">{{ (row.bookings || []).length }}</template>
        </el-table-column>
        <el-table-column label="推荐" width="80">
          <template #default="{ row }">
            <el-tag v-if="recommendedSet.has(row.id)" type="success">推荐</el-tag>
            <span v-else style="color:#bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '空闲' ? 'success' : row.status === '使用中' ? 'danger' : row.status === '待清场' ? 'info' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="清场" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.cleaningStatus" :type="cleaningTagType(row.cleaningStatus)">{{ row.cleaningStatus }}</el-tag>
            <span v-else style="color:#bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column label="可接待" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.available" type="success">可接待</el-tag>
            <el-tag v-else type="info">暂停</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '使用中'" size="small" type="danger" plain
                       @click="openCheckout(row)">散场清场</el-button>
            <el-button v-if="row.status === '待清场'" size="small" type="warning" plain
                       @click="emit('goto-cleaning')">去清场管理</el-button>
            <el-button size="small" type="warning" plain
                       :disabled="row.status === '使用中' || row.status === '待清场'"
                       @click="changeStatus(row, '使用中')">置使用中</el-button>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建包厢" width="560px">
      <el-form :model="form" label-width="110px" class="dialog-form">
        <el-form-item label="包厢编号"><el-input v-model="form.roomCode" placeholder="如 K07" /></el-form-item>
        <el-form-item label="包厢名称"><el-input v-model="form.roomName" placeholder="如 中包" /></el-form-item>
        <el-form-item label="容量(人)"><el-input v-model="form.capacity" type="number" :min="1" /></el-form-item>
        <el-form-item label="最低消费(元)"><el-input v-model="form.minSpend" type="number" :min="0" step="1" /></el-form-item>
        <el-form-item label="状态" class="full">
          <el-radio-group v-model="form.status">
            <el-radio v-for="s in ['空闲', '预订', '使用中']" :key="s" :value="s">{{ s }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>

    <!-- 散场清场确认 -->
    <el-dialog v-model="checkoutDialog" :title="`散场清场 · ${checkoutTarget?.roomName || ''}`" width="480px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
                title="散场后将生成待清场任务，包厢转入「待清场」；验收放行前不对外显示可用、不可安排新场次。" />
      <el-form label-width="90px">
        <el-form-item label="操作人">
          <el-input v-model="checkoutOperator" placeholder="前台操作人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutDialog = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doCheckout">确认散场，生成清场任务</el-button>
      </template>
    </el-dialog>

    <!-- 包厢详情：预订 + 清场交接记录追溯 -->
    <el-drawer v-model="drawer" :title="detail ? `${detail.roomCode} ${detail.roomName} · 详情` : ''" size="500px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === '空闲' ? 'success' : detail.status === '使用中' ? 'danger' : detail.status === '待清场' ? 'info' : 'warning'">{{ detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="可接待">
            <el-tag :type="detail.available ? 'success' : 'info'">{{ detail.available ? '可接待' : '暂停' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="容量">{{ detail.capacity }} 人</el-descriptions-item>
          <el-descriptions-item label="最低消费">{{ detail.minSpend }} 元</el-descriptions-item>
        </el-descriptions>

        <div style="margin: 16px 0 8px; font-weight: 800; color: var(--primary-deep)">清场交接记录</div>
        <div v-loading="detailLoading">
          <el-empty v-if="!detailTasks.length" description="暂无清场任务" :image-size="60" />
          <div v-for="t in detailTasks" :key="t.id" class="trace-card">
            <div class="trace-head">
              <el-tag size="small" :type="cleaningTagType(t.status)">{{ t.status }}</el-tag>
              <span class="trace-title">任务 #{{ t.id }}</span>
              <span class="trace-sub">负责人：{{ t.cleanerName || '待领取' }}</span>
              <span v-if="t.returnCount" class="trace-sub" style="color:#c0392b">退回 {{ t.returnCount }} 次</span>
            </div>
            <el-timeline style="margin-top: 10px; padding-left: 4px">
              <el-timeline-item v-for="log in t.logs" :key="log.id"
                                :timestamp="`${log.createdAt} · ${log.operator}`"
                                :type="logTagType(log.action)">
                <el-tag size="small" :type="logTagType(log.action)" style="margin-right: 6px">{{ log.action }}</el-tag>
                <span style="font-size: 13px">{{ log.detail }}</span>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>

        <div style="margin: 16px 0 8px; font-weight: 800; color: var(--primary-deep)">预订记录</div>
        <el-table :data="detail.bookings || []" size="small" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="startAt" label="开始" min-width="140" />
          <el-table-column prop="endAt" label="结束" min-width="140" />
          <el-table-column prop="status" label="状态" width="80" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.trace-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fff;
}
.trace-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.trace-title { font-weight: 800; }
.trace-sub { font-size: 12px; color: var(--muted); }
</style>
