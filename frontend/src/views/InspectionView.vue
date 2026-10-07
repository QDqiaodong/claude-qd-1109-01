<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const hives = ref([])
const allRows = ref([])                       // 全部巡检 DTO（一次加载，跨月切换不重复生成处置）
const calDate = ref(new Date())
const selectedDay = ref(todayStr())

const filters = reactive({
  hiveId: '', beeHealth: '', handlingStatus: '', dueFrom: '', dueTo: ''
})
const HEALTHS = ['健康', '一般', '病态']
const HANDLING_STATUS_OPTIONS = [
  { value: '待处置', label: '待处置' },
  { value: '逾期', label: '逾期' },
  { value: '待复检', label: '待复检' },
  { value: '复检不合格', label: '复检不合格（连续异常）' },
  { value: '已解除', label: '已解除' }
]

// ---------------- 巡检新增/编辑 ----------------
const dialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, hiveId: null, inspectDate: '', honeyKg: 0, beeHealth: '健康', note: ''
})

// ---------------- 处置登记 ----------------
const handleDialog = ref(false)
const handleForm = reactive({
  handlingId: null, inspectionId: null, roundNo: 1,
  measure: '', handler: '', dueDate: '', siteNote: ''
})

// ---------------- 处置复检 ----------------
const recheckDialog = ref(false)
const recheckForm = reactive({
  handlingId: null, inspectionId: null, result: '合格', inspector: '', recheckDate: todayStr(), note: ''
})

function monthStr(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const emit = defineEmits(['changed'])
function notifyChanged() { emit('changed') }

const hiveMap = computed(() => {
  const m = {}
  hives.value.forEach(h => { m[h.id] = h })
  return m
})
const hiveCode = (id) => (hiveMap.value[id] ? hiveMap.value[id].code : ('#' + id))

// ---------------- 筛选（全部在已加载数据上计算，月历/列表/日面板共用） ----------------
const rows = computed(() => {
  return allRows.value.filter(r => {
    if (filters.hiveId && r.hiveId !== Number(filters.hiveId)) return false
    if (filters.beeHealth && r.beeHealth !== filters.beeHealth) return false
    if (filters.handlingStatus && !matchHandling(r, filters.handlingStatus)) return false
    if ((filters.dueFrom || filters.dueTo) && !r.dueDate) return false
    if (filters.dueFrom && r.dueDate < filters.dueFrom) return false
    if (filters.dueTo && r.dueDate > filters.dueTo) return false
    return true
  })
})

function matchHandling(r, s) {
  if (s === '逾期') return r.overdue
  if (s === '待复检') return r.status === '待复检'
  if (s === '已解除') return r.status === '已解除'
  if (s === '待处置') return r.status === '待处置'
  if (s === '复检不合格') return (r.handlings || []).some(h => h.status === '复检不合格')
  return true
}

function resetFilters() {
  filters.hiveId = ''; filters.beeHealth = ''; filters.handlingStatus = ''
  filters.dueFrom = ''; filters.dueTo = ''
}

const inspectMap = computed(() => {
  // 月历只显示当前所在月的已筛选数据
  const m = monthStr(calDate.value)
  const map = {}
  rows.value.forEach(r => {
    const k = String(r.inspectDate)
    if (k.startsWith(m)) (map[k] = map[k] || []).push(r)
  })
  return map
})

const dayRows = computed(() => inspectMap.value[selectedDay.value] || [])

const healthTag = (h) =>
  h === '健康' ? 'success' : h === '一般' ? 'warning' : 'danger'

function statusTagType(s) {
  if (s === '已解除') return 'success'
  if (s === '待复检') return 'warning'
  if (s === '待处置') return 'danger'
  return 'info'
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
  if (!day.startsWith(monthStr(calDate.value))) {
    calDate.value = new Date(day + 'T00:00:00')
  }
}

// ---------------- 数据加载 ----------------
async function loadHives() {
  hives.value = await api.listHives()
}
async function loadAll() {
  // 一次性拉全量（演示数据量），月历跨月仅前端切换，绝不重复触发后端生成处置
  allRows.value = await api.listInspections()
}

// ---------------- 新增 / 编辑巡检 ----------------
function openCreate(day) {
  editing.value = false
  form.id = null
  form.hiveId = hives.value.length ? hives.value[0].id : null
  form.inspectDate = day || selectedDay.value
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

const targetHiveOpenRisk = computed(() => {
  if (!form.hiveId) return null
  return allRows.value.find(r =>
    r.hiveId === form.hiveId && r.id !== form.id && r.activeHandling) || null
})
const editingOpenRisk = computed(() => {
  if (!editing.value) return null
  const r = allRows.value.find(x => x.id === form.id)
  return r && r.activeHandling ? r : null
})

async function submit() {
  if (form.beeHealth === '病态' && (!form.note || !form.note.trim())) {
    ElMessage.error('蜂群健康为病态时必须填写现场异常说明')
    return
  }
  if (form.beeHealth === '健康' && targetHiveOpenRisk.value) {
    ElMessage.error('该蜂箱存在未完成处置，新巡检不能直接登记为「健康」')
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
      ElMessage.success(form.beeHealth === '健康' ? '已记录' : '巡检异常，已生成待处置任务')
    }
    dialog.value = false
    await loadAll(); notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 处置登记 ----------------
function openHandle(row) {
  const h = row.activeHandling
  if (!h) {
    ElMessage.warning('该巡检没有进行中的处置项')
    return
  }
  handleForm.handlingId = h.id
  handleForm.inspectionId = row.id
  handleForm.roundNo = h.roundNo
  handleForm.measure = h.measure || ''
  handleForm.handler = h.handler || ''
  handleForm.dueDate = h.dueDate ? String(h.dueDate) : ''
  handleForm.siteNote = h.siteNote || ''
  handleRow.value = row
  handleDialog.value = true
}
const handleRow = ref(null)

async function submitHandle(keepPending) {
  if (!handleForm.measure.trim()) { ElMessage.error('请填写处置措施（处置说明）'); return }
  if (!handleForm.handler.trim()) { ElMessage.error('请填写负责人'); return }
  if (!handleForm.dueDate) { ElMessage.error('请选择处置截止日期'); return }
  try {
    await api.registerHandling(handleForm.handlingId, {
      measure: handleForm.measure,
      handler: handleForm.handler,
      dueDate: handleForm.dueDate,
      siteNote: handleForm.siteNote,
      keepPending
    })
    ElMessage.success(keepPending ? '处置信息已登记，仍为待处置' : '处置完成，已进入待复检')
    handleDialog.value = false
    await loadAll(); notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 处置复检 ----------------
function openRecheck(row) {
  const h = row.activeHandling
  if (!h || h.status !== '待复检') {
    ElMessage.warning('只有已完成处置的处置项才能发起复检')
    return
  }
  recheckForm.handlingId = h.id
  recheckForm.inspectionId = row.id
  recheckForm.result = '合格'
  recheckForm.inspector = h.handler || ''
  recheckForm.recheckDate = todayStr()
  recheckForm.note = ''
  recheckRow.value = row
  recheckDialog.value = true
}
const recheckRow = ref(null)

async function submitRecheck() {
  if (!recheckForm.inspector.trim()) { ElMessage.error('请填写复检人'); return }
  if (!recheckForm.recheckDate) { ElMessage.error('请选择复检日期'); return }
  try {
    const dto = await api.recheckHandling(recheckForm.handlingId, {
      result: recheckForm.result,
      inspector: recheckForm.inspector,
      recheckDate: recheckForm.recheckDate,
      note: recheckForm.note
    })
    if (recheckForm.result === '合格') {
      ElMessage.success('复检合格，风险提醒已解除')
    } else {
      ElMessage.success('复检不合格：原异常保留，已重新生成待处置并顺延风险期限 7 天')
    }
    recheckDialog.value = false
    await loadAll(); notifyChanged()
    return dto
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(calDate, () => {
  const m = monthStr(calDate.value)
  if (!selectedDay.value.startsWith(m)) selectedDay.value = `${m}-01`
})

onMounted(async () => {
  await loadHives()
  await loadAll()
})
</script>

<template>
  <div>
    <div class="bar">
      <h2>蜂箱巡检 · 异常处置与复检提醒</h2>
      <el-button type="primary" @click="openCreate()">新增巡检</el-button>
    </div>

    <!-- 筛选：健康状态 / 处置状态 / 截止日期 / 蜂箱 -->
    <div class="filter-bar">
      <el-select v-model="filters.hiveId" placeholder="全部蜂箱" clearable style="width: 150px">
        <el-option v-for="h in hives" :key="h.id" :label="h.code" :value="h.id" />
      </el-select>
      <el-select v-model="filters.beeHealth" placeholder="健康状态" clearable style="width: 130px">
        <el-option v-for="h in HEALTHS" :key="h" :label="h" :value="h" />
      </el-select>
      <el-select v-model="filters.handlingStatus" placeholder="处置状态" clearable style="width: 200px">
        <el-option v-for="o in HANDLING_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <span class="due-label">截止日期</span>
      <el-date-picker v-model="filters.dueFrom" type="date" value-format="YYYY-MM-DD"
                      placeholder="起" style="width: 132px" />
      <span class="due-sep">~</span>
      <el-date-picker v-model="filters.dueTo" type="date" value-format="YYYY-MM-DD"
                      placeholder="止" style="width: 132px" />
      <el-button @click="resetFilters">重置</el-button>
      <span class="legend">
        <i class="lg lg-risk"></i>风险中
        <i class="lg lg-overdue"></i>逾期
        <i class="lg lg-recheck"></i>待复检
        <i class="lg lg-done"></i>已解除
      </span>
    </div>

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
            <div class="cell" :class="{ 'cell-risk': (inspectMap[data.day] || []).some(r => r.activeHandling) }"
                 @click="pickDay(data.day)">
              <div class="cell-num" :class="{ on: data.day === selectedDay }">
                {{ data.day.split('-')[2] }}
              </div>
              <div class="cell-dots">
                <span v-for="r in (inspectMap[data.day] || [])" :key="r.id"
                      class="dot"
                      :class="{
                        'dot-risk': r.status === '待复检',
                        'dot-overdue': r.overdue,
                        'dot-done': r.status === '已解除'
                      }"
                      :style="r.status === '待复检' || r.overdue || r.status === '已解除'
                        ? {} : { background: r.beeHealth === '健康' ? '#67c23a' : r.beeHealth === '一般' ? '#e6a23c' : '#f56c6c' }"
                      :title="`${hiveCode(r.hiveId)} · ${r.beeHealth} · ${r.status}` + (r.overdue ? '（逾期）' : '')"
                ></span>
              </div>
            </div>
          </template>
        </el-calendar>
      </div>

      <div class="panel">
        <div class="panel-title">{{ selectedDay }} 的巡检（{{ dayRows.length }}）</div>
        <el-empty v-if="!dayRows.length" description="当天暂无巡检" :image-size="60" />
        <div v-for="r in dayRows" :key="r.id" class="ins-card"
             :class="{ 'card-risk': r.activeHandling, 'card-overdue': r.overdue, 'card-done': r.status === '已解除' }">
          <div class="ins-row">
            <b>{{ hiveCode(r.hiveId) }}</b>
            <el-tag size="small" :type="healthTag(r.beeHealth)">{{ r.beeHealth }}</el-tag>
            <el-tag size="small" :type="statusTagType(r.status)">{{ r.status }}</el-tag>
            <el-tag v-if="r.overdue" size="small" type="danger" effect="dark">逾期</el-tag>
            <el-tag v-else-if="r.status === '待复检'" size="small" type="warning" effect="dark">待复检</el-tag>
            <el-tag v-if="r.continuousAbnormal" size="small" type="danger" effect="plain">
              连续异常·第{{ r.handlingRound }}轮
            </el-tag>
            <el-tag v-if="r.hiveDeleted" size="small" type="info">蜂箱已删</el-tag>
          </div>
          <div class="ins-meta">产蜜 {{ r.honeyKg }} kg</div>
          <div class="ins-meta" v-if="r.note">现场说明：{{ r.note }}</div>

          <div v-if="r.activeHandling" class="handling-box">
            <div class="ins-meta">
              负责人：<b>{{ r.activeHandling.handler || '未指派' }}</b>
              <span class="due" :class="{ overdue: r.overdue }">
                截止：{{ r.activeHandling.dueDate || '未设定' }}{{ r.overdue ? '（已逾期）' : '' }}
              </span>
            </div>
            <div class="ins-meta" v-if="r.activeHandling.measure">措施：{{ r.activeHandling.measure }}</div>
            <div class="ins-meta" v-if="r.activeHandling.siteNote">处置备注：{{ r.activeHandling.siteNote }}</div>
            <div class="ins-actions">
              <el-button size="small" @click="openEdit(r)">编辑巡检</el-button>
              <el-button v-if="r.activeHandling.status === '待处置'" size="small" type="primary"
                         @click="openHandle(r)">登记处置</el-button>
              <el-button v-if="r.activeHandling.status === '待复检'" size="small" type="warning"
                         @click="openRecheck(r)">发起复检</el-button>
            </div>
          </div>
          <div class="ins-actions" v-else>
            <el-button size="small" @click="openEdit(r)">编辑</el-button>
          </div>

          <div v-if="(r.handlings || []).length" class="history">
            <div v-for="h in r.handlings" :key="h.id" class="history-item">
              <el-tag size="small" :type="statusTagType(h.status)" effect="plain">第{{ h.roundNo }}轮 · {{ h.status }}</el-tag>
              <span class="history-text">
                {{ h.measure || '待登记处置措施' }}
                <template v-if="h.handler"> · {{ h.handler }}</template>
                <template v-if="h.dueDate"> · 截止 {{ h.dueDate }}</template>
              </span>
            </div>
            <div v-for="rc in r.rechecks" :key="rc.id" class="history-item">
              <el-tag size="small" :type="rc.result === '合格' ? 'success' : 'danger'" effect="plain">
                复检{{ rc.result }}
              </el-tag>
              <span class="history-text">{{ rc.recheckDate }} · {{ rc.inspector }}<template v-if="rc.note"> · {{ rc.note }}</template></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 异常处置列表 -->
    <div class="list-title">
      巡检与处置列表（{{ rows.length }}）
      <span class="list-hint">逾期、待复检、连续异常与已解除记录在此明确区分</span>
    </div>
    <el-table :data="rows" border stripe size="small">
      <el-table-column prop="inspectDate" label="巡检日期" width="110" />
      <el-table-column label="蜂箱" width="90">
        <template #default="{ row }">
          {{ hiveCode(row.hiveId) }}
          <el-tag v-if="row.hiveDeleted" size="small" type="info">已删</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="健康状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="healthTag(row.beeHealth)">{{ row.beeHealth }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="honeyKg" label="产蜜kg" width="80" />
      <el-table-column label="处置状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          <el-tag v-if="row.overdue" size="small" type="danger" effect="dark" style="margin-left:4px">逾期</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="轮次" width="70">
        <template #default="{ row }">{{ row.handlingRound ? `第${row.handlingRound}轮` : '—' }}</template>
      </el-table-column>
      <el-table-column label="负责人" width="90">
        <template #default="{ row }">{{ row.activeHandling?.handler || '—' }}</template>
      </el-table-column>
      <el-table-column label="截止日期" width="110">
        <template #default="{ row }">
          <span :class="{ 'text-overdue': row.overdue }">{{ row.dueDate || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="风险标记" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.overdue" size="small" type="danger" effect="dark">逾期未处置</el-tag>
          <el-tag v-else-if="row.status === '待复检'" size="small" type="warning">待复检</el-tag>
          <el-tag v-else-if="row.continuousAbnormal" size="small" type="danger">连续异常</el-tag>
          <el-tag v-else-if="row.status === '已解除'" size="small" type="success">已解除</el-tag>
          <el-tag v-else-if="row.status === '待处置'" size="small" type="danger">待处置</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="note" label="现场说明" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.activeHandling?.status === '待处置'" size="small" type="primary"
                     @click="openHandle(row)">登记处置</el-button>
          <el-button v-if="row.activeHandling?.status === '待复检'" size="small" type="warning"
                     @click="openRecheck(row)">发起复检</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑巡检 -->
    <el-dialog v-model="dialog" :title="editing ? '编辑巡检' : '新增巡检'" width="460px">
      <el-alert v-if="targetHiveOpenRisk" type="error" :closable="false" show-icon style="margin-bottom: 12px"
                :title="`${hiveCode(form.hiveId)} 存在未完成处置（${targetHiveOpenRisk.status}），本次巡检不能直接登记为「健康」`" />
      <el-alert v-else-if="editingOpenRisk && form.beeHealth === '健康'" type="error" :closable="false" show-icon
                style="margin-bottom: 12px" title="该异常处置尚未完成，健康状态不能直接改回「健康」" />
      <el-form label-width="96px">
        <el-form-item label="蜂箱">
          <el-select v-model="form.hiveId" style="width: 100%">
            <el-option v-for="h in hives" :key="h.id"
                       :label="h.code + '（' + (h.location || '—') + '）'" :value="h.id" />
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
          <el-input v-model="form.note" type="textarea" :rows="2"
                    placeholder="一般/病态时记录异常现象；病态必填" />
        </el-form-item>
        <div class="form-tip" v-if="form.beeHealth !== '健康'">
          保存后该巡检进入「待处置」，需登记处置措施、负责人与截止日期，完成后才能发起复检。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 登记/完成处置 -->
    <el-dialog v-model="handleDialog" :title="`登记异常处置（第 ${handleForm.roundNo} 轮）`" width="500px">
      <el-form label-width="96px">
        <el-form-item label="处置措施" required>
          <el-input v-model="handleForm.measure" type="textarea" :rows="2"
                    placeholder="如：隔离病群、施药治疗、换王、补饲等" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="负责人" required>
          <el-input v-model="handleForm.handler" placeholder="负责处置的工作人员" maxlength="32" />
        </el-form-item>
        <el-form-item label="截止日期" required>
          <el-date-picker v-model="handleForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="现场说明">
          <el-input v-model="handleForm.siteNote" type="textarea" :rows="2"
                    placeholder="现场处置情况、用药、观察记录等" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div v-if="handleRow && (handleRow.handlings || []).length > 1" class="prev-rounds">
        <div class="prev-title">历史轮次（原异常保留可追溯）：</div>
        <div v-for="h in [...handleRow.handlings].reverse()" :key="h.id" class="history-item">
          <el-tag size="small" :type="statusTagType(h.status)" effect="plain">第{{ h.roundNo }}轮 · {{ h.status }}</el-tag>
          <span class="history-text">{{ h.measure || '待登记' }} · {{ h.handler || '—' }} · 截止 {{ h.dueDate || '—' }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="handleDialog = false">取消</el-button>
        <el-button @click="submitHandle(true)">保存登记（暂不完成）</el-button>
        <el-button type="primary" @click="submitHandle(false)">完成处置，进入待复检</el-button>
      </template>
    </el-dialog>

    <!-- 处置复检（每条处置仅一次） -->
    <el-dialog v-model="recheckDialog" title="处置复检（每条处置仅一次）" width="480px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                title="复检合格解除提醒；复检不合格将保留原异常、重新生成待处置项并顺延风险期限 7 天" />
      <el-form label-width="84px">
        <el-form-item label="复检结论">
          <el-radio-group v-model="recheckForm.result">
            <el-radio value="合格">合格（解除提醒）</el-radio>
            <el-radio value="不合格">不合格（重新待处置）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复检人" required>
          <el-input v-model="recheckForm.inspector" maxlength="32" />
        </el-form-item>
        <el-form-item label="复检日期" required>
          <el-date-picker v-model="recheckForm.recheckDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="复检说明">
          <el-input v-model="recheckForm.note" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recheckDialog = false">取消</el-button>
        <el-button :type="recheckForm.result === '合格' ? 'success' : 'danger'" @click="submitRecheck">提交复检结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.bar h2 { margin: 0; font-size: 18px; }

.filter-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; margin-bottom: 14px; }
.due-label { color: var(--muted); font-size: 13px; }
.due-sep { color: var(--muted); }
.legend { margin-left: auto; font-size: 12px; color: var(--muted); display: flex; align-items: center; gap: 6px; }
.lg { width: 9px; height: 9px; border-radius: 50%; display: inline-block; margin-left: 6px; }
.lg-risk { background: #f56c6c; }
.lg-overdue { background: #fff; border: 2px solid #f56c6c; }
.lg-recheck { background: #e6a23c; }
.lg-done { background: #67c23a; }

.layout { display: flex; gap: 18px; align-items: flex-start; }
.cal-wrap { flex: 1; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 8px 12px; }
.cal-head { display: flex; align-items: center; justify-content: center; gap: 16px; width: 100%; }
.cal-title { font-weight: 600; }
.cell { height: 100%; cursor: pointer; border-radius: 6px; padding: 2px 4px; }
.cell-risk { background: var(--primary-wash); }
.cell-num { font-size: 13px; }
.cell-num.on { color: var(--primary-deep); font-weight: 700; }
.cell-dots { display: flex; gap: 3px; margin-top: 3px; flex-wrap: wrap; }
.dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.dot-risk { background: #e6a23c; }
.dot-overdue { background: #fff; border: 2px solid #f56c6c; box-sizing: border-box; }
.dot-done { background: #67c23a; }

.panel { width: 360px; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 14px; }
.panel-title { font-weight: 600; margin-bottom: 10px; }
.ins-card { border: 1px solid var(--line); border-radius: 10px; padding: 10px; margin-bottom: 10px; }
.card-risk { border-color: #f3c184; background: #fdf8ee; }
.card-overdue { border-color: #f56c6c; background: #fef0f0; }
.card-done { border-color: #b3e19d; background: #f0f9eb; }
.ins-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.ins-meta { font-size: 13px; color: var(--muted); margin-top: 4px; }
.due { margin-left: 8px; }
.due.overdue, .text-overdue { color: #f56c6c; font-weight: 700; }
.handling-box { margin-top: 6px; padding-top: 6px; border-top: 1px dashed var(--line); }
.ins-actions { margin-top: 8px; }
.history { margin-top: 8px; padding-top: 6px; border-top: 1px dashed var(--line); }
.history-item { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.history-text { font-size: 12px; color: var(--muted); }

.list-title { margin: 20px 0 10px; font-weight: 600; font-size: 16px; }
.list-hint { font-size: 12px; color: var(--muted); font-weight: 400; margin-left: 10px; }

.form-tip { font-size: 12px; color: var(--primary-deep); background: var(--primary-wash);
  border-radius: 6px; padding: 6px 8px; }
.prev-rounds { background: #faf6ea; border-radius: 8px; padding: 8px 10px; margin-top: 4px; }
.prev-title { font-size: 12px; color: var(--muted); margin-bottom: 4px; }
</style>
