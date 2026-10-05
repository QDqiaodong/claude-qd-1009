<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

// ------------------------------------------------------------------
// 清场交接管理台：保洁待办 / 经理待验收 / 整改积压 三条队列。
// 顶部选择当前身份（保洁员 / 值班经理），操作按钮按角色显隐；
// 所有状态迁移以后端为准，冲突时后端返回可理解提示并自动刷新。
// ------------------------------------------------------------------

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)

// 当前操作身份（localStorage 持久化，刷新后保持）
const operator = ref(localStorage.getItem('cleaning-operator') || '')
const role = ref(localStorage.getItem('cleaning-role') || '保洁员')

// 详情抽屉
const drawer = ref(false)
const detail = ref(null)

// 提交清场弹窗
const submitDialog = ref(false)
const submitTarget = ref(null)
const submitForm = ref({ hygieneResult: '合格', leftoverItems: '', deviceIssue: '' })

// 验收弹窗
const reviewDialog = ref(false)
const reviewTarget = ref(null)
const reviewForm = ref({ returnReason: '', deviceResolution: '' })

// 异常处置弹窗
const resolveDialog = ref(false)
const resolveTarget = ref(null)
const resolveForm = ref({ resolution: '' })

const saving = ref(false)

const pendingClaim = computed(() => rows.value.filter(r => r.status === '待领取'))
const cleaning = computed(() => rows.value.filter(r => r.status === '清场中'))
const pendingReview = computed(() => rows.value.filter(r => r.status === '待验收'))
const rectifying = computed(() => rows.value.filter(r => r.status === '整改中'))
const released = computed(() => rows.value.filter(r => r.status === '已放行'))

// 保洁待办 = 待领取 + 清场中 + 整改中
const cleanerTodo = computed(() =>
  rows.value.filter(r => ['待领取', '清场中', '整改中'].includes(r.status)))

function persistIdentity() {
  localStorage.setItem('cleaning-operator', operator.value)
  localStorage.setItem('cleaning-role', role.value)
}

async function load() {
  loading.value = true; error.value = ''
  try {
    rows.value = (await api.listCleaningTasks()) || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function roomLabel(row) {
  return row.room ? `${row.room.roomCode} ${row.room.roomName}` : `#${row.id}`
}

function tagType(status) {
  return {
    待领取: 'info', 清场中: 'warning', 待验收: 'primary',
    整改中: 'danger', 已放行: 'success'
  }[status] || 'info'
}

// 设备异常未处置：有异常描述但无处置结论
function issueOpen(row) {
  return !!(row.deviceIssue && !row.deviceResolution)
}

function requireOperator() {
  if (!operator.value.trim()) {
    error.value = '请先在顶部「操作人」填写姓名'
    return false
  }
  return true
}

async function run(action, okTip) {
  saving.value = true; error.value = ''; tip.value = ''
  try {
    await action()
    tip.value = okTip
    await load()
    return true
  } catch (e) {
    error.value = e.message
    await load() // 冲突提示后刷新，保证页面与库中状态一致
    return false
  } finally { saving.value = false }
}

// ---------------- 保洁：领取 ----------------
async function claim(row) {
  if (!requireOperator()) return
  await run(() => api.claimCleaningTask(row.id, operator.value.trim()),
    `已领取 ${roomLabel(row)} 的清场任务`)
}

// ---------------- 保洁：提交清场 ----------------
function openSubmit(row) {
  if (!requireOperator()) return
  submitTarget.value = row
  // 整改重新提交时预填上次内容，便于对照退回原因修改
  submitForm.value = {
    hygieneResult: row.hygieneResult || '合格',
    leftoverItems: row.leftoverItems || '',
    deviceIssue: row.deviceIssue || ''
  }
  submitDialog.value = true
}

async function doSubmit() {
  const row = submitTarget.value
  const ok = await run(() => api.submitCleaningTask(row.id, {
    operator: operator.value.trim(),
    hygieneResult: submitForm.value.hygieneResult,
    leftoverItems: submitForm.value.leftoverItems,
    deviceIssue: submitForm.value.deviceIssue
  }), `${roomLabel(row)} 清场结果已提交，待经理验收`)
  if (ok) submitDialog.value = false
}

// ---------------- 异常处置登记 ----------------
function openResolve(row) {
  if (!requireOperator()) return
  resolveTarget.value = row
  resolveForm.value = { resolution: '' }
  resolveDialog.value = true
}

async function doResolve() {
  const row = resolveTarget.value
  const ok = await run(() => api.resolveDeviceIssue(row.id, {
    operator: operator.value.trim(),
    resolution: resolveForm.value.resolution
  }), `${roomLabel(row)} 设备异常处置已登记`)
  if (ok) resolveDialog.value = false
}

// ---------------- 经理：验收 ----------------
function openReview(row) {
  if (!requireOperator()) return
  reviewTarget.value = row
  reviewForm.value = { returnReason: '', deviceResolution: '' }
  reviewDialog.value = true
}

async function doReview(approve) {
  const row = reviewTarget.value
  const ok = await run(() => api.reviewCleaningTask(row.id, {
    manager: operator.value.trim(),
    approve,
    returnReason: reviewForm.value.returnReason,
    deviceResolution: reviewForm.value.deviceResolution
  }), approve ? `${roomLabel(row)} 验收通过，包厢已放行` : `${roomLabel(row)} 已退回整改`)
  if (ok) reviewDialog.value = false
}

// ---------------- 详情 ----------------
async function openDetail(row) {
  error.value = ''
  try {
    detail.value = await api.getCleaningTask(row.id)
    drawer.value = true
  } catch (e) { error.value = e.message }
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
    <!-- 身份条 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">清场交接 · 验收放行闭环</span>
        <span class="panel-desc">散场 → 保洁清场 → 经理验收 → 放行；未验收放行的包厢一律不对外显示可用</span>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <span class="ruler-label">操作人</span>
        <el-input v-model="operator" placeholder="姓名（如 王保洁 / 赵经理）" style="width: 220px"
                  @change="persistIdentity" />
        <span class="ruler-label">角色</span>
        <el-radio-group v-model="role" @change="persistIdentity">
          <el-radio value="保洁员">保洁员</el-radio>
          <el-radio value="值班经理">值班经理</el-radio>
        </el-radio-group>
        <span class="spacer"></span>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div v-if="error" class="err">{{ error }}</div>
    <div v-if="tip" class="ok-tip">{{ tip }}</div>

    <!-- 队列统计 -->
    <div class="panel">
      <div class="stat-row" style="margin-bottom: 0">
        <div class="stat"><div class="k">待领取</div><div class="v">{{ pendingClaim.length }}</div></div>
        <div class="stat"><div class="k">清场中</div><div class="v">{{ cleaning.length }}</div></div>
        <div class="stat"><div class="k">待验收</div><div class="v">{{ pendingReview.length }}</div></div>
        <div class="stat"><div class="k">整改积压</div><div class="v">{{ rectifying.length }}</div></div>
        <div class="stat"><div class="k">已放行</div><div class="v">{{ released.length }}</div></div>
      </div>
    </div>

    <!-- 队列一：保洁待办 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">保洁待办</span>
        <span class="panel-desc">待领取 / 清场中 / 整改中 —— 领取后填写卫生结果、遗留物与设备异常</span>
      </div>
      <el-table :data="cleanerTodo" v-loading="loading" border stripe>
        <el-table-column label="任务" width="80"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="包厢" min-width="110">
          <template #default="{ row }">{{ roomLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="cleanerName" label="负责人" width="90">
          <template #default="{ row }">{{ row.cleanerName || '—' }}</template>
        </el-table-column>
        <el-table-column label="退回原因 / 上次问题" min-width="220">
          <template #default="{ row }">
            <span v-if="row.status === '整改中'" style="color:#c0392b">
              退回{{ row.returnCount }}次：{{ row.lastReturnReason }}
            </span>
            <span v-else style="color:#bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" min-width="150" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="role === '保洁员'">
              <el-button v-if="row.status === '待领取'" size="small" type="primary"
                         :loading="saving" @click="claim(row)">领取</el-button>
              <el-button v-if="row.status === '清场中' || row.status === '整改中'" size="small" type="primary"
                         :loading="saving" @click="openSubmit(row)">
                {{ row.status === '整改中' ? '整改后重新提交' : '提交清场' }}
              </el-button>
              <el-button v-if="issueOpen(row) && row.status !== '待领取'" size="small" type="warning" plain
                         :loading="saving" @click="openResolve(row)">登记异常处置</el-button>
            </template>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无保洁待办任务</template>
      </el-table>
    </div>

    <!-- 队列二：经理待验收 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">经理待验收</span>
        <span class="panel-desc">只能验收放行或退回整改；带未处置设备异常的任务须先完成异常处置或留下停用结论</span>
      </div>
      <el-table :data="pendingReview" v-loading="loading" border stripe>
        <el-table-column label="任务" width="80"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="包厢" min-width="110">
          <template #default="{ row }">{{ roomLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="cleanerName" label="保洁" width="90" />
        <el-table-column label="卫生" width="80">
          <template #default="{ row }">
            <el-tag :type="row.hygieneResult === '合格' ? 'success' : 'danger'">{{ row.hygieneResult || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="遗留物" min-width="120">
          <template #default="{ row }">{{ row.leftoverItems || '无' }}</template>
        </el-table-column>
        <el-table-column label="设备异常" min-width="180">
          <template #default="{ row }">
            <template v-if="row.deviceIssue">
              <el-tag :type="issueOpen(row) ? 'danger' : 'success'" style="margin-right:4px">
                {{ issueOpen(row) ? '未处置' : '已处置' }}
              </el-tag>{{ row.deviceIssue }}
              <div v-if="row.deviceResolution" style="color:#888;font-size:12px">处置：{{ row.deviceResolution }}</div>
            </template>
            <span v-else style="color:#bbb">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" min-width="150" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="role === '值班经理'">
              <el-button size="small" type="primary" :loading="saving" @click="openReview(row)">验收</el-button>
              <el-button v-if="issueOpen(row)" size="small" type="warning" plain
                         :loading="saving" @click="openResolve(row)">登记异常处置</el-button>
            </template>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无待验收任务</template>
      </el-table>
    </div>

    <!-- 队列三：整改积压 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">整改积压</span>
        <span class="panel-desc">被验收退回的任务，前一次问题与处理记录完整保留，整改后须重新提交验收</span>
      </div>
      <el-table :data="rectifying" v-loading="loading" border stripe>
        <el-table-column label="任务" width="80"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="包厢" min-width="110">
          <template #default="{ row }">{{ roomLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="cleanerName" label="负责人" width="90" />
        <el-table-column label="退回次数" width="90">
          <template #default="{ row }"><el-tag type="danger">{{ row.returnCount }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastReturnReason" label="最近退回原因" min-width="220" />
        <el-table-column prop="updatedAt" label="退回时间" min-width="150" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">追溯记录</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无整改积压</template>
      </el-table>
    </div>

    <!-- 全部任务 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">全部清场任务</span>
        <span class="panel-desc">含已放行历史；点「详情」查看整条交接记录</span>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="任务" width="80"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="包厢" min-width="110">
          <template #default="{ row }">{{ roomLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="cleanerName" label="负责人" width="90">
          <template #default="{ row }">{{ row.cleanerName || '—' }}</template>
        </el-table-column>
        <el-table-column label="卫生" width="80">
          <template #default="{ row }">{{ row.hygieneResult || '—' }}</template>
        </el-table-column>
        <el-table-column label="关联场次" width="90">
          <template #default="{ row }">{{ row.bookingId ? `#${row.bookingId}` : '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" min-width="150" />
        <el-table-column prop="finishedAt" label="放行时间" min-width="150">
          <template #default="{ row }">{{ row.finishedAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 提交清场弹窗 -->
    <el-dialog v-model="submitDialog" :title="`提交清场 · ${submitTarget ? roomLabel(submitTarget) : ''}`" width="560px">
      <el-alert v-if="submitTarget && submitTarget.status === '整改中'" type="error" :closable="false"
                style="margin-bottom: 12px"
                :title="`退回原因（第 ${submitTarget.returnCount} 次）：${submitTarget.lastReturnReason}`" />
      <el-form :model="submitForm" label-width="110px">
        <el-form-item label="卫生结果">
          <el-radio-group v-model="submitForm.hygieneResult">
            <el-radio value="合格">合格</el-radio>
            <el-radio value="不合格">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="遗留物">
          <el-input v-model="submitForm.leftoverItems" placeholder="客人遗留物品；无则留空" />
        </el-form-item>
        <el-form-item label="设备异常">
          <el-input v-model="submitForm.deviceIssue" type="textarea" :rows="2"
                    placeholder="麦克风、点歌屏等设备异常；无则留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSubmit">提交，转经理验收</el-button>
      </template>
    </el-dialog>

    <!-- 验收弹窗 -->
    <el-dialog v-model="reviewDialog" :title="`验收 · ${reviewTarget ? roomLabel(reviewTarget) : ''}`" width="600px">
      <template v-if="reviewTarget">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="保洁 / 提交时间">
            {{ reviewTarget.cleanerName || '—' }} / {{ reviewTarget.submittedAt || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="卫生结果">{{ reviewTarget.hygieneResult || '—' }}</el-descriptions-item>
          <el-descriptions-item label="遗留物">{{ reviewTarget.leftoverItems || '无' }}</el-descriptions-item>
          <el-descriptions-item label="设备异常">
            <span v-if="reviewTarget.deviceIssue">
              {{ reviewTarget.deviceIssue }}
              <el-tag :type="issueOpen(reviewTarget) ? 'danger' : 'success'" size="small" style="margin-left:6px">
                {{ issueOpen(reviewTarget) ? '未处置' : '已处置' }}
              </el-tag>
              <div v-if="reviewTarget.deviceResolution" style="color:#888;font-size:12px">
                处置：{{ reviewTarget.deviceResolution }}
              </div>
            </span>
            <span v-else>无</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="issueOpen(reviewTarget)" type="warning" :closable="false" style="margin-bottom: 12px"
                  title="该任务存在未处置的设备异常：即使卫生合格也不能直接放行。请先在列表中「登记异常处置」，或在下方填写明确的停用结论后放行。" />
        <el-form label-width="110px">
          <el-form-item v-if="issueOpen(reviewTarget)" label="停用结论">
            <el-input v-model="reviewForm.deviceResolution" type="textarea" :rows="2"
                      placeholder="如：2号麦克风停用待修，已张贴故障告示" />
          </el-form-item>
          <el-form-item label="退回原因">
            <el-input v-model="reviewForm.returnReason" type="textarea" :rows="2"
                      placeholder="选择「退回整改」时必填，将随交接记录保留" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button type="danger" plain :loading="saving" @click="doReview(false)">退回整改</el-button>
        <el-button type="primary" :loading="saving" @click="doReview(true)">验收放行</el-button>
      </template>
    </el-dialog>

    <!-- 异常处置弹窗 -->
    <el-dialog v-model="resolveDialog" :title="`设备异常处置 · ${resolveTarget ? roomLabel(resolveTarget) : ''}`" width="520px">
      <el-alert v-if="resolveTarget" type="warning" :closable="false" style="margin-bottom: 12px"
                :title="`异常：${resolveTarget.deviceIssue}`" />
      <el-input v-model="resolveForm.resolution" type="textarea" :rows="3"
                placeholder="处置结果，如：已更换电池恢复正常 / 联系工程部维修完成" />
      <template #footer>
        <el-button @click="resolveDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doResolve">登记处置</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情抽屉：整条交接记录 -->
    <el-drawer v-model="drawer" :title="detail ? `清场任务 #${detail.id} · ${roomLabel(detail)}` : ''" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="tagType(detail.status)">{{ detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.cleanerName || '待领取' }}</el-descriptions-item>
          <el-descriptions-item label="卫生结果">{{ detail.hygieneResult || '—' }}</el-descriptions-item>
          <el-descriptions-item label="遗留物">{{ detail.leftoverItems || '无' }}</el-descriptions-item>
          <el-descriptions-item label="设备异常">{{ detail.deviceIssue || '无' }}</el-descriptions-item>
          <el-descriptions-item label="异常处置">{{ detail.deviceResolution || '—' }}</el-descriptions-item>
          <el-descriptions-item label="退回次数">{{ detail.returnCount }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.lastReturnReason" label="最近退回原因">
            {{ detail.lastReturnReason }}
          </el-descriptions-item>
          <el-descriptions-item label="关联场次">{{ detail.bookingId ? `#${detail.bookingId}` : '—' }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin: 16px 0 8px; font-weight: 800; color: var(--primary-deep)">交接记录</div>
        <el-timeline>
          <el-timeline-item v-for="log in detail.logs" :key="log.id"
                            :timestamp="`${log.createdAt} · ${log.operator}`"
                            :type="logTagType(log.action)">
            <el-tag size="small" :type="logTagType(log.action)" style="margin-right: 6px">{{ log.action }}</el-tag>
            <span style="font-size: 13px">{{ log.detail }}</span>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>
