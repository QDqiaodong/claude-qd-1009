<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const langFilter = ref('')
const dialog = ref(false)
const saving = ref(false)
const languages = ['国语', '粤语', '英文', '日文']

const form = ref(blank())
function blank() {
  return { songCode: '', songName: '', singer: '', language: '国语', durationSec: '' }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listSongs(langFilter.value ? { language: langFilter.value } : {})
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function fmtDur(s) {
  if (s == null) return '—'
  const m = Math.floor(s / 60)
  const ss = String(s % 60).padStart(2, '0')
  return `${m}:${ss}`
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, durationSec: form.value.durationSec ? Number(form.value.durationSec) : null }
    await api.createSong(d)
    tip.value = `歌曲 ${d.songName} 已入库`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">歌曲库 · 语种与时长</span>
        <span class="panel-desc">编码唯一，语种须为 国语/粤语/英文/日文</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <el-select v-model="langFilter" placeholder="全部语种" clearable style="width: 140px" @change="load">
          <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">入库歌曲</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="songCode" label="编码" width="90" />
        <el-table-column prop="songName" label="歌名" min-width="140" />
        <el-table-column prop="singer" label="歌手" width="130" />
        <el-table-column prop="language" label="语种" width="90">
          <template #default="{ row }"><el-tag>{{ row.language }}</el-tag></template>
        </el-table-column>
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ fmtDur(row.durationSec) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="入库歌曲" width="560px">
      <el-form :model="form" label-width="90px" class="dialog-form">
        <el-form-item label="编码"><el-input v-model="form.songCode" placeholder="如 S006" /></el-form-item>
        <el-form-item label="歌名"><el-input v-model="form.songName" /></el-form-item>
        <el-form-item label="歌手"><el-input v-model="form.singer" /></el-form-item>
        <el-form-item label="语种">
          <el-select v-model="form.language">
            <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(秒)"><el-input v-model="form.durationSec" type="number" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>
