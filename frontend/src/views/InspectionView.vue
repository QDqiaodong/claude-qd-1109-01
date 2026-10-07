<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const hives = ref([])
const hiveMap = computed(() => {
  const m = {}
  hives.value.forEach(h => { m[h.id] = h })
  return m
})
const hiveCode = (id) => (hiveMap.value[id] ? hiveMap.value[id].code : ('#' + id))

const activeTab = ref('calendar')
const calDate = ref(new Date())
const listMonth = ref(todayStr().slice(0, 7))
const selectedDay = ref(todayStr())
const inspections = ref([])
const handlings = ref([])
const loading = ref(false)
const filters = reactive({ hiveId: null, beeHealth: '', handlingStatus: '', dueDate: '' })

const inspectMap = ref({})
const dayInspections = computed(() => inspectMap.value[selectedDay.value] || [])

const dialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, hiveId: null, inspectDate: '', honeyKg: 0, beeHealth: '健康', note: ''
})

const handlingDialog = ref(false)
const handlingForm = reactive({ id: null, actionType: '隔离治疗', siteNote: '', ownerName: '', deadline: '' })

const recheckDialog = ref(false)
const recheckForm = reactive({ id: null, beeHealth: '健康', inspector: '', note: '' })

const HEALTHS = ['健康', '一般', '病态']
const HANDLING_STATES = ['待处置', '待复检', '已解除', '复检不合格', '逾期']
const ACTION_TYPES = ['隔离治疗', '换脾消毒', '补饲强群', '更换蜂王', '转场观察', '其他']

const emit = defineEmits(['changed'])
function notifyChanged() { emit('changed') }

function monthStr(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function loadHives() {
  hives.value = await api.listHives()
}

function queryOptions(includeMonth = true) {
  return {
    hiveId: filters.hiveId,
    beeHealth: filters.beeHealth,
    handlingStatus: filters.handlingStatus,
    dueDate: filters.dueDate,
    month: includeMonth ? (activeTab.value === 'calendar' ? monthStr(calDate.value) : listMonth.value) : ''
  }
}

async function load() {
  loading.value = true
  try {
    const [list, handlingList] = await Promise.all([
      api.listInspections(queryOptions(true)),
      api.listInspectionHandlings(queryOptions(true))
    ])
    inspections.value = list
    handlings.value = handlingList
    const map = {}
    list.forEach(i => {
      const k = String(i.inspectDate)
      ;(map[k] = map[k] || []).push(i)
    })
    inspectMap.value = map
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function prevMonth() {
  const d = calDate.value
  calDate.value = new Date(d.getFullYear(), d.getMonth() - 1, 1)
}
function nextMonth() {
  const d = calDate.value
  calDate.value = new Date(d.getFullYear(), d.getMonth() + 1, 1)
}
function pickDay(day) {
  selectedDay.value = day
}

const healthType = (h) => (h === '健康' ? 'success' : h === '一般' ? 'warning' : 'danger')
function healthColor(h) {
  return h === '健康' ? '#67c23a' : h === '一般' ? '#e6a23c' : '#f56c6c'
}
function riskType(row) {
  const state = row.riskState
  if (!state) return 'info'
  if (state === '逾期') return 'danger'
  if (state === '待复检') return 'warning'
  if (state === '已解除') return 'success'
  return 'primary'
}
function handlingType(row) {
  const status = row.status
  if (status === '逾期') return 'danger'
  if (status === '待复检') return 'warning'
  if (status === '已解除') return 'success'
  if (status === '复检不合格') return 'danger'
  return 'primary'
}
function riskText(row) {
  return row.riskState || (row.status === '已记录' ? '无风险' : row.status)
}
function canComplete(row) {
  return row.activeHandling && row.activeHandling.status === '待处置'
}
function canRecheck(row) {
  return row.activeHandling && row.activeHandling.status === '待复检'
}
function lockReason(row) {
  if (canComplete(row)) return ''
  if (canRecheck(row)) return '请先登记复检结果'
  if (row.activeHandling) return `当前：${row.activeHandling.status}`
  return ''
}

function openCreate(day = selectedDay.value) {
  editing.value = false
  form.id = null
  form.hiveId = filters.hiveId || (hives.value.length ? hives.value[0].id : null)
  form.inspectDate = day
  form.honeyKg = 0
  form.beeHealth = '健康'
  form.note = ''
  dialog.value = true
}
function openEdit(row) {
  editing.value = true
  form.id = row.id
  form.hiveId = row.hiveId
  form.inspectDate = String(row.inspectDate)
  form.honeyKg = row.honeyKg
  form.beeHealth = row.beeHealth
  form.note = row.note || ''
  dialog.value = true
}

async function submit() {
  if (form.beeHealth === '病态' && (!form.note || !form.note.trim())) {
    ElMessage.error('蜂群健康为病态时必须填写现场说明')
    return
  }
  try {
    const payload = {
      hiveId: form.hiveId,
      inspectDate: form.inspectDate,
      honeyKg: Number(form.honeyKg),
      beeHealth: form.beeHealth,
      note: form.note
    }
    if (editing.value) {
      await api.updateInspection(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await api.createInspection(payload)
      ElMessage.success('已记录；异常已进入待处置')
    }
    dialog.value = false
    await load()
    notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openHandling(row) {
  const h = row.activeHandling
  handlingForm.id = h.id
  handlingForm.actionType = h.actionType || '隔离治疗'
  handlingForm.siteNote = h.siteNote || ''
  handlingForm.ownerName = h.ownerName || ''
  handlingForm.deadline = h.deadline ? String(h.deadline) : ''
  handlingDialog.value = true
}

async function submitHandling() {
  if (!handlingForm.actionType) return ElMessage.error('请选择处置措施')
  if (!handlingForm.ownerName.trim()) return ElMessage.error('请填写负责人')
  if (!handlingForm.deadline) return ElMessage.error('请选择截止日期')
  if (!handlingForm.siteNote.trim()) return ElMessage.error('请填写处置现场说明')
  try {
    await api.completeInspectionHandling(handlingForm.id, {
      actionType: handlingForm.actionType,
      ownerName: handlingForm.ownerName,
      deadline: handlingForm.deadline,
      siteNote: handlingForm.siteNote
    })
    ElMessage.success('处置已完成，进入待复检')
    handlingDialog.value = false
    await load()
    notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openRecheck(row) {
  recheckForm.id = row.activeHandling.id
  recheckForm.beeHealth = '健康'
  recheckForm.inspector = ''
  recheckForm.note = ''
  recheckDialog.value = true
}

async function submitRecheck() {
  if (!recheckForm.inspector.trim()) return ElMessage.error('请填写复检人')
  if (!recheckForm.note.trim()) return ElMessage.error('请填写复检说明')
  try {
    await api.recheckInspectionHandling(recheckForm.id, {
      beeHealth: recheckForm.beeHealth,
      inspector: recheckForm.inspector,
      note: recheckForm.note
    })
    ElMessage.success(recheckForm.beeHealth === '健康' ? '复检合格，风险提醒已解除' : '复检不合格，已顺延期限并重新待处置')
    recheckDialog.value = false
    await load()
    notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function resetFilters() {
  filters.hiveId = null
  filters.beeHealth = ''
  filters.handlingStatus = ''
  filters.dueDate = ''
  listMonth.value = todayStr().slice(0, 7)
}

watch([calDate, activeTab, listMonth, () => filters.hiveId, () => filters.beeHealth,
  () => filters.handlingStatus, () => filters.dueDate], load)

onMounted(async () => {
  await loadHives()
  await load()
})
</script>

<template>
  <div v-loading="loading">
    <div class="bar">
      <h2>蜂箱巡检 · 异常处置与复检提醒</h2>
      <el-button type="primary" @click="openCreate()">新增巡检</el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <div class="filters">
        <el-select v-model="filters.hiveId" placeholder="全部蜂箱" clearable style="width: 150px">
          <el-option v-for="h in hives" :key="h.id" :label="h.code" :value="h.id" />
        </el-select>
        <el-select v-model="filters.beeHealth" placeholder="健康状态" clearable style="width: 130px">
          <el-option v-for="h in HEALTHS" :key="h" :label="h" :value="h" />
        </el-select>
        <el-select v-model="filters.handlingStatus" placeholder="处置状态" clearable style="width: 140px">
          <el-option v-for="s in HANDLING_STATES" :key="s" :label="s" :value="s" />
        </el-select>
        <el-date-picker
          v-if="activeTab !== 'calendar'"
          v-model="listMonth"
          type="month"
          value-format="YYYY-MM"
          placeholder="巡检月份"
          style="width: 130px"
        />
        <el-date-picker
          v-model="filters.dueDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="截止日期"
          style="width: 150px"
        />
        <el-button @click="resetFilters">重置</el-button>
        <span class="legend">
          <em class="lg overdue"></em>逾期
          <em class="lg waiting"></em>待复检
          <em class="lg resolved"></em>已解除
        </span>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="月历" name="calendar">
        <div class="layout">
          <div class="cal-wrap">
            <el-calendar v-model="calDate">
              <template #header>
                <div class="cal-head">
                  <el-button size="small" @click="prevMonth">‹ 上月</el-button>
                  <span class="cal-title">{{ calDate.getFullYear() }} 年 {{ calDate.getMonth() + 1 }} 月</span>
                  <el-button size="small" @click="nextMonth">下月 ›</el-button>
                </div>
              </template>
              <template #date-cell="{ data }">
                <div class="cell" :class="{ selected: data.day === selectedDay }" @click="pickDay(data.day)">
                  <div class="cell-num">{{ data.day.split('-')[2] }}</div>
                  <div class="cell-dots">
                    <span
                      v-for="ins in (inspectMap[data.day] || [])"
                      :key="ins.id"
                      class="dot"
                      :class="{ risk: ins.openRisk, overdue: ins.overdue }"
                      :style="{ background: healthColor(ins.beeHealth) }"
                      :title="`${hiveCode(ins.hiveId)} · ${ins.beeHealth} · ${riskText(ins)}`"
                    ></span>
                  </div>
                  <div v-if="(inspectMap[data.day] || []).some(i => i.overdue)" class="cell-badge">逾</div>
                </div>
              </template>
            </el-calendar>
          </div>

          <div class="panel">
            <div class="panel-title">{{ selectedDay }} 的巡检（{{ dayInspections.length }}）</div>
            <el-empty v-if="!dayInspections.length" description="当天暂无巡检" :image-size="60" />
            <div v-for="ins in dayInspections" :key="ins.id" class="ins-card" :class="{ 'risk-card': ins.openRisk }">
              <div class="ins-row">
                <b>{{ hiveCode(ins.hiveId) }}</b>
                <el-tag size="small" :type="healthType(ins.beeHealth)">{{ ins.beeHealth }}</el-tag>
                <el-tag size="small" :type="riskType(ins)">{{ riskText(ins) }}</el-tag>
                <el-tag v-if="ins.consecutiveAbnormal" size="small" type="danger">连续异常</el-tag>
              </div>
              <div class="ins-meta">产蜜 {{ ins.honeyKg }} kg · 巡检状态：{{ ins.status }}</div>
              <div class="ins-meta" v-if="ins.note">现场说明：{{ ins.note }}</div>
              <div v-if="ins.activeHandling" class="handling-box" :class="{ overdue: ins.overdue }">
                <div>负责人：{{ ins.activeHandling.ownerName || '待登记' }}｜截止：{{ ins.activeHandling.deadline || '待登记' }}</div>
                <div>措施：{{ ins.activeHandling.actionType || '待登记' }}｜第 {{ ins.activeHandling.roundNo }} 轮</div>
                <div v-if="ins.activeHandling.siteNote">处置说明：{{ ins.activeHandling.siteNote }}</div>
              </div>
              <div class="ins-actions">
                <el-button size="small" @click="openEdit(ins)">编辑</el-button>
                <el-button size="small" type="warning" :disabled="!canComplete(ins)" @click="openHandling(ins)">登记处置</el-button>
                <el-button size="small" type="success" :disabled="!canRecheck(ins)" @click="openRecheck(ins)">登记复检</el-button>
                <span v-if="lockReason(ins)" class="action-tip">{{ lockReason(ins) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="巡检/处置列表" name="list">
        <el-table :data="inspections" border stripe>
          <el-table-column prop="hiveCode" label="蜂箱" width="90" />
          <el-table-column prop="inspectDate" label="巡检日期" width="115" />
          <el-table-column label="健康状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="healthType(row.beeHealth)">{{ row.beeHealth }}</el-tag>
              <el-tag v-if="row.consecutiveAbnormal" size="small" type="danger" style="margin-left: 4px">连续异常</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="巡检状态" width="100" />
          <el-table-column label="处置状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="riskType(row)">{{ riskText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="措施 / 负责人" min-width="170">
            <template #default="{ row }">
              <template v-if="row.activeHandling">
                {{ row.activeHandling.actionType || '待登记' }} / {{ row.activeHandling.ownerName || '待登记' }}
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="截止日期" width="115">
            <template #default="{ row }">{{ row.activeHandling?.deadline || '—' }}</template>
          </el-table-column>
          <el-table-column prop="note" label="现场说明" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="warning" :disabled="!canComplete(row)" @click="openHandling(row)">处置</el-button>
              <el-button size="small" type="success" :disabled="!canRecheck(row)" @click="openRecheck(row)">复检</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="处置记录链" name="handlings">
        <el-table :data="handlings" border stripe row-key="id">
          <el-table-column prop="hiveCode" label="蜂箱" width="90" />
          <el-table-column label="来源巡检" width="120">
            <template #default="{ row }">{{ row.sourceInspectDate }}（{{ row.sourceBeeHealth }}）</template>
          </el-table-column>
          <el-table-column prop="roundNo" label="轮次" width="70" />
          <el-table-column label="处置状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="handlingType(row)">{{ row.overdue ? '逾期' : row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="actionType" label="措施" width="110" />
          <el-table-column prop="ownerName" label="负责人" width="90" />
          <el-table-column prop="deadline" label="截止日期" width="115" />
          <el-table-column prop="completedAt" label="完成时间" min-width="160" />
          <el-table-column label="复检结果" width="100">
            <template #default="{ row }">{{ row.recheckResult || '—' }}</template>
          </el-table-column>
          <el-table-column prop="recheckInspector" label="复检人" width="90" />
          <el-table-column prop="recheckNote" label="复检说明" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" :title="editing ? '编辑巡检' : '新增巡检'" width="460px">
      <el-form label-width="92px">
        <el-form-item label="蜂箱">
          <el-select v-model="form.hiveId" style="width: 100%">
            <el-option v-for="h in hives" :key="h.id" :label="h.code + '（' + (h.location || '—') + '）'" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检日期">
          <el-date-picker v-model="form.inspectDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="产蜜(kg)">
          <el-input-number v-model="form.honeyKg" :min="0" :precision="2" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="蜂群健康">
          <el-select v-model="form.beeHealth" style="width: 100%">
            <el-option v-for="h in HEALTHS" :key="h" :label="h" :value="h" />
          </el-select>
        </el-form-item>
        <el-form-item label="现场说明">
          <el-input v-model="form.note" type="textarea" :rows="3" placeholder="病态必须填写；风险未解除时不能直接登记健康" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handlingDialog" title="登记异常处置" width="500px">
      <el-form label-width="96px">
        <el-form-item label="处置措施" required>
          <el-select v-model="handlingForm.actionType" style="width: 100%">
            <el-option v-for="a in ACTION_TYPES" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" required>
          <el-input v-model="handlingForm.ownerName" maxlength="32" />
        </el-form-item>
        <el-form-item label="截止日期" required>
          <el-date-picker v-model="handlingForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="现场说明" required>
          <el-input v-model="handlingForm.siteNote" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handlingDialog = false">取消</el-button>
        <el-button type="primary" @click="submitHandling">完成处置并发起复检</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recheckDialog" title="登记复检结果（仅此一次）" width="500px">
      <el-form label-width="96px">
        <el-alert type="info" show-icon :closable="false" style="margin-bottom: 12px"
          title="复检合格才解除提醒；复检不合格会保留原异常、重新生成待处置项并顺延风险期限。" />
        <el-form-item label="复检健康">
          <el-select v-model="recheckForm.beeHealth" style="width: 100%">
            <el-option v-for="h in HEALTHS" :key="h" :label="h" :value="h" />
          </el-select>
        </el-form-item>
        <el-form-item label="复检人" required>
          <el-input v-model="recheckForm.inspector" maxlength="32" />
        </el-form-item>
        <el-form-item label="复检说明" required>
          <el-input v-model="recheckForm.note" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recheckDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRecheck">提交复检结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.bar h2 { margin: 0; font-size: 18px; }
.filter-card { margin-bottom: 12px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.legend { margin-left: auto; color: var(--muted); font-size: 13px; display: flex; align-items: center; gap: 5px; }
.lg { width: 10px; height: 10px; border-radius: 50%; display: inline-block; margin-left: 8px; }
.lg.overdue { background: #f56c6c; }
.lg.waiting { background: #e6a23c; }
.lg.resolved { background: #67c23a; }
.tabs { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 8px 12px; }
.layout { display: flex; gap: 18px; align-items: flex-start; }
.cal-wrap { flex: 1; }
.cal-head { display: flex; align-items: center; justify-content: center; gap: 16px; width: 100%; }
.cal-title { font-weight: 600; }
.cell { height: 100%; min-height: 62px; cursor: pointer; position: relative; padding: 2px; border-radius: 6px; }
.cell.selected { background: rgba(230, 162, 60, 0.12); }
.cell-num { font-size: 13px; }
.cell-dots { display: flex; gap: 3px; margin-top: 3px; flex-wrap: wrap; }
.dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.dot.risk { box-shadow: 0 0 0 2px #f56c6c; }
.dot.overdue { box-shadow: 0 0 0 2px #901111; }
.cell-badge { position: absolute; right: 5px; top: 3px; color: #fff; background: #f56c6c; border-radius: 50%; width: 18px; height: 18px; font-size: 11px; line-height: 18px; text-align: center; }
.panel { width: 360px; border-left: 1px solid var(--line); padding-left: 16px; }
.panel-title { font-weight: 600; margin-bottom: 10px; }
.ins-card { border: 1px solid var(--line); border-radius: 10px; padding: 10px; margin-bottom: 10px; background: #fff; }
.ins-card.risk-card { border-color: #f56c6c; background: #fff8f8; }
.ins-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.ins-meta { font-size: 13px; color: var(--muted); margin-top: 4px; }
.handling-box { margin-top: 7px; padding: 7px; border-radius: 8px; background: #fff7e6; color: #7a4a00; font-size: 13px; line-height: 1.6; }
.handling-box.overdue { background: #fff0f0; color: #901111; }
.ins-actions { margin-top: 8px; display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.action-tip { font-size: 12px; color: var(--muted); }
.muted { color: var(--muted); }
</style>
