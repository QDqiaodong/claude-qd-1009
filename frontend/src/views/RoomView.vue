<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const props = defineProps({ filter: Object })

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const statusFilter = ref('')
const dialog = ref(false)
const saving = ref(false)
const statuses = ['空闲', '预订', '使用中']

const form = ref(blank())
function blank() {
  return { roomCode: '', roomName: '', capacity: '', minSpend: '', status: '空闲' }
}

const recommendedSet = computed(() => {
  const ps = (props.filter && props.filter.partySize) || 1
  return new Set(rows.value.filter(r => r.capacity >= ps).map(r => r.id))
})

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
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">包厢 · 房态与容量</span>
        <span class="panel-desc">刻度尺选定 {{ filter?.partySize || 1 }} 人，容量达标包厢标「推荐」</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">包厢数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">空闲</div><div class="v">{{ rows.filter(r => r.status === '空闲').length }}</div></div>
        <div class="stat"><div class="k">预订</div><div class="v">{{ rows.filter(r => r.status === '预订').length }}</div></div>
        <div class="stat"><div class="k">使用中</div><div class="v">{{ rows.filter(r => r.status === '使用中').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建包厢</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
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
            <el-tag :type="row.status === '空闲' ? 'success' : row.status === '使用中' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.status === '空闲'" @click="changeStatus(row, '空闲')">置空闲</el-button>
            <el-button size="small" type="warning" plain :disabled="row.status === '使用中'" @click="changeStatus(row, '使用中')">置使用中</el-button>
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
            <el-radio v-for="s in statuses" :key="s" :value="s">{{ s }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>
  </div>
</template>
