<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const bookings = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const settleId = ref('')

const form = ref(blank())
function blank() { return { bookingId: '', itemName: '', amount: '' } }

async function load() {
  loading.value = true; error.value = ''
  try {
    const [c, b] = await Promise.all([api.listConsumptions(), api.listBookings()])
    rows.value = c || []
    bookings.value = b || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function bookingLabel(id) {
  const b = bookings.value.find(x => x.id === id)
  return b ? `预订#${b.id}（${b.partySize}人）` : `预订#${id}`
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    await api.createConsumption({
      bookingId: Number(form.value.bookingId),
      itemName: form.value.itemName,
      amount: Number(form.value.amount)
    })
    tip.value = `已记消费：${form.value.itemName}`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function settle() {
  if (!settleId.value) return
  error.value = ''; tip.value = ''
  try {
    const total = await api.settleBooking(Number(settleId.value))
    tip.value = `结账开单成功，合计 ${total} 元`
    settleId.value = ''
    await load()
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">消费 · 挂单与结账</span>
        <span class="panel-desc">合计须达包厢最低消费方可开单</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">记一笔消费</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="预订" min-width="150">
          <template #default="{ row }">{{ bookingLabel(row.bookingId) }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目" min-width="120" />
        <el-table-column prop="amount" label="金额(元)" width="110" />
        <el-table-column prop="at" label="时间" min-width="150" />
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="记一笔消费" width="560px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="预订">
          <el-select v-model="form.bookingId" filterable placeholder="选择预订">
            <el-option v-for="b in bookings" :key="b.id" :label="bookingLabel(b.id)" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="form.itemName" placeholder="如 啤酒 / 果盘 / 套餐" /></el-form-item>
        <el-form-item label="金额(元)"><el-input v-model="form.amount" type="number" :min="0" step="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">记录</el-button>
      </template>
    </el-dialog>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">结账开单</span>
        <span class="panel-desc">选一笔有效预订结算（低于最低消费会被拒）</span>
      </div>
      <div class="toolbar">
        <el-select v-model="settleId" placeholder="选择预订" style="width: 240px">
          <el-option v-for="b in bookings.filter(x => x.status === '已预订')" :key="b.id" :label="bookingLabel(b.id)" :value="b.id" />
        </el-select>
        <el-button type="success" :disabled="!settleId" @click="settle">结账开单</el-button>
      </div>
    </div>
  </div>
</template>
