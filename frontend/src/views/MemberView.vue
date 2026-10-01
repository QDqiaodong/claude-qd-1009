<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const levelFilter = ref('')
const dialog = ref(false)
const saving = ref(false)
const levels = ['普通', '银卡', '金卡', '钻石']

const form = ref(blank())
function blank() {
  return { memberNo: '', memberName: '', phone: '', level: '普通', balance: 0 }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listMembers(levelFilter.value ? { level: levelFilter.value } : {})
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, balance: Number(form.value.balance) }
    await api.createMember(d)
    tip.value = `会员 ${d.memberName} 已建档`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function changeLevel(row, level) {
  error.value = ''; tip.value = ''
  try {
    await api.updateMemberLevel(row.id, level)
    tip.value = `${row.memberName} 等级已更新为「${level}」`
    await load()
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">会员 · 等级与预存</span>
        <span class="panel-desc">会员号 / 手机号唯一，等级须合法</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">会员数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">钻石</div><div class="v">{{ rows.filter(r => r.level === '钻石').length }}</div></div>
        <div class="stat"><div class="k">金卡</div><div class="v">{{ rows.filter(r => r.level === '金卡').length }}</div></div>
        <div class="stat"><div class="k">银卡</div><div class="v">{{ rows.filter(r => r.level === '银卡').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="levelFilter" placeholder="全部等级" clearable style="width: 140px" @change="load">
          <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建会员</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="memberNo" label="会员号" width="100" />
        <el-table-column prop="memberName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="balance" label="预存(元)" width="110" />
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.level === '钻石' ? 'danger' : row.level === '金卡' ? 'warning' : 'success'">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调整等级" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-for="l in levels" :key="l" size="small" :disabled="row.level === l" @click="changeLevel(row, l)">{{ l }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建会员" width="560px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="会员号"><el-input v-model="form.memberNo" placeholder="如 M005" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.memberName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="预存(元)"><el-input v-model="form.balance" type="number" :min="0" step="1" /></el-form-item>
        <el-form-item label="等级" class="full">
          <el-radio-group v-model="form.level">
            <el-radio v-for="l in levels" :key="l" :value="l">{{ l }}</el-radio>
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
