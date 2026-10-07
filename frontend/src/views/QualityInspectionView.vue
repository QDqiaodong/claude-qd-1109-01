<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const list = ref([])
const hives = ref([])
const loading = ref(false)
const filters = reactive({ batchStatus: '', conclusion: '' })

const batchStatuses = ['待质检', '质检放行', '隔离待整改', '复检合格', '隔离拒收', '已入库', '已作废']
const conclusions = ['待质检', '合格', '不合格']

const inspectDialog = ref(false)
const inspectMode = ref('initial')
const inspectSubmitting = ref(false)
const currentRow = ref(null)
const inspectForm = reactive({
  sampleNo: '',
  moisturePercent: 18,
  sensoryResult: '合格',
  inspector: '',
  inspectedAt: new Date(),
  note: ''
})

const rectifyDialog = ref(false)
const rectifySubmitting = ref(false)
const actionLoadingId = ref(null)
const rectifyForm = reactive({ opinion: '', handler: '' })

const history = reactive({ visible: false, batch: null })

const hiveMap = computed(() => {
  const m = {}
  hives.value.forEach(h => { m[h.id] = h })
  return m
})
const hiveCode = (id) => (hiveMap.value[id] ? hiveMap.value[id].code : ('#' + id))

function statusType(status) {
  if (['质检放行', '复检合格', '已入库'].includes(status)) return 'success'
  if (['隔离待整改', '隔离拒收'].includes(status)) return 'danger'
  if (status === '已作废') return 'info'
  return 'warning'
}
function resultType(result) {
  return result === '合格' ? 'success' : result === '不合格' ? 'danger' : 'info'
}
function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}
function toLocalDateTime(date) {
  const d = date || new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function load() {
  loading.value = true
  try {
    const [rows, h] = await Promise.all([
      api.listQualityBatches({ batchStatus: filters.batchStatus, conclusion: filters.conclusion }),
      api.listHives()
    ])
    list.value = rows
    hives.value = h
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function resetInspectForm() {
  inspectForm.sampleNo = `S-${new Date().getFullYear()}-${String(Date.now()).slice(-6)}`
  inspectForm.moisturePercent = 18
  inspectForm.sensoryResult = '合格'
  inspectForm.inspector = ''
  inspectForm.inspectedAt = new Date()
  inspectForm.note = ''
}

function openInitial(row) {
  currentRow.value = row
  inspectMode.value = 'initial'
  resetInspectForm()
  inspectDialog.value = true
}
function openRecheck(row) {
  currentRow.value = row
  inspectMode.value = 'recheck'
  resetInspectForm()
  inspectForm.sampleNo = ''
  inspectDialog.value = true
}
function openRectification(row) {
  currentRow.value = row
  rectifyForm.opinion = ''
  rectifyForm.handler = ''
  rectifyDialog.value = true
}

async function submitInspection() {
  if (!inspectForm.sampleNo.trim()) return ElMessage.error('抽样编号不能为空')
  if (inspectForm.moisturePercent === null || inspectForm.moisturePercent === undefined) {
    return ElMessage.error('含水率不能为空')
  }
  if (!inspectForm.inspector.trim()) return ElMessage.error('检测人不能为空')
  if (!inspectForm.note.trim()) return ElMessage.error('检测说明不能为空')

  inspectSubmitting.value = true
  try {
    const payload = {
      sampleNo: inspectForm.sampleNo.trim(),
      moisturePercent: Number(inspectForm.moisturePercent),
      sensoryResult: inspectForm.sensoryResult,
      inspector: inspectForm.inspector.trim(),
      inspectedAt: toLocalDateTime(inspectForm.inspectedAt),
      note: inspectForm.note.trim()
    }
    if (inspectMode.value === 'initial') {
      const result = await api.submitInitialQuality(currentRow.value.id, payload)
      ElMessage.success(initialMessage(result))
    } else {
      const result = await api.submitQualityRecheck(currentRow.value.id, payload)
      ElMessage.success(recheckMessage(result))
    }
    inspectDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    inspectSubmitting.value = false
  }
}

function initialMessage(result) {
  return result.activeInspection?.result === '合格'
    ? '初检合格，批次已质检放行，可执行入库'
    : '初检不合格，批次已隔离待整改，请登记整改说明'
}
function recheckMessage(result) {
  return result.activeInspection?.result === '合格'
    ? '复检合格，批次已放行，可执行入库'
    : '复检仍不合格，批次保持隔离并已明确拒绝入库'
}

async function submitRectification() {
  if (!rectifyForm.opinion.trim()) return ElMessage.error('整改说明不能为空')
  if (!rectifyForm.handler.trim()) return ElMessage.error('整改责任人不能为空')
  rectifySubmitting.value = true
  try {
    await api.submitRectification(currentRow.value.id, {
      opinion: rectifyForm.opinion.trim(),
      handler: rectifyForm.handler.trim()
    })
    ElMessage.success('整改说明已登记，现在可以发起唯一一次复检')
    rectifyDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    rectifySubmitting.value = false
  }
}

async function stockIn(row) {
  actionLoadingId.value = row.id
  try {
    await api.stockInHoneyBatch(row.id)
    ElMessage.success('批次已入库')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    actionLoadingId.value = null
  }
}

async function openHistory(row) {
  history.visible = true
  history.batch = null
  try {
    history.batch = await api.getQualityBatch(row.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <h2>蜂蜜批次质检放行</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert
      class="rule"
      type="warning"
      show-icon
      :closable="false"
      title="放行规则：含水率 ≤ 20% 且感官结论合格才可放行入库；任一不合格即隔离。隔离批次必须先登记整改说明，且只能复检一次，复检仍不合格将永久拒绝入库。"
    />

    <div class="filters">
      <span>批次状态</span>
      <el-select v-model="filters.batchStatus" clearable placeholder="全部状态" style="width: 170px" @change="load">
        <el-option v-for="s in batchStatuses" :key="s" :label="s" :value="s" />
      </el-select>
      <span>质检结论</span>
      <el-select v-model="filters.conclusion" clearable placeholder="全部结论" style="width: 150px" @change="load">
        <el-option v-for="s in conclusions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" plain @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe row-key="id">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="action-guide">
            <div><b>初检：</b><span :class="row.inspectAction.enabled ? 'ok' : 'muted'">{{ row.inspectAction.reason }}</span></div>
            <div><b>整改：</b><span :class="row.rectifyAction.enabled ? 'ok' : 'muted'">{{ row.rectifyAction.reason }}</span></div>
            <div><b>复检：</b><span :class="row.recheckAction.enabled ? 'ok' : 'muted'">{{ row.recheckAction.reason }}</span></div>
            <div><b>入库：</b><span :class="row.stockInAction.enabled ? 'ok' : 'muted'">{{ row.stockInAction.reason }}</span></div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="batchNo" label="批次号" width="125" />
      <el-table-column label="蜂箱" width="80">
        <template #default="{ row }">{{ hiveCode(row.hiveId) }}</template>
      </el-table-column>
      <el-table-column prop="weightKg" label="重量(kg)" width="95" />
      <el-table-column label="批次/质检状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="抽样编号" width="130">
        <template #default="{ row }">{{ row.activeInspection?.sampleNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="含水率" width="90">
        <template #default="{ row }">
          <span v-if="row.activeInspection">{{ row.activeInspection.moisturePercent }}%</span>
          <span v-else class="muted">未检</span>
        </template>
      </el-table-column>
      <el-table-column label="感官/结论" width="120">
        <template #default="{ row }">
          <template v-if="row.activeInspection">
            <el-tag :type="resultType(row.activeInspection.sensoryResult)" size="small">{{ row.activeInspection.sensoryResult }}</el-tag>
            <el-tag class="ml4" :type="resultType(row.activeInspection.result)" size="small">{{ row.activeInspection.result }}</el-tag>
          </template>
          <el-tag v-else type="info" size="small">待质检</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="检测人/时间" width="165">
        <template #default="{ row }">
          <div v-if="row.activeInspection">
            <div>{{ row.activeInspection.inspector }}</div>
            <div class="sub">{{ formatTime(row.activeInspection.inspectedAt) }}</div>
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="追溯" width="105">
        <template #default="{ row }">
          <el-button size="small" @click="openHistory(row)">记录({{ row.inspectionCount }})</el-button>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="360" fixed="right">
        <template #default="{ row }">
          <div class="actions">
            <el-tooltip :content="row.inspectAction.reason" :disabled="row.inspectAction.enabled" placement="top">
              <span class="tip-wrap"><el-button size="small" type="primary" :disabled="!row.inspectAction.enabled" @click="openInitial(row)">初检</el-button></span>
            </el-tooltip>
            <el-tooltip :content="row.rectifyAction.reason" :disabled="row.rectifyAction.enabled" placement="top">
              <span class="tip-wrap"><el-button size="small" type="warning" :disabled="!row.rectifyAction.enabled" @click="openRectification(row)">整改</el-button></span>
            </el-tooltip>
            <el-tooltip :content="row.recheckAction.reason" :disabled="row.recheckAction.enabled" placement="top">
              <span class="tip-wrap"><el-button size="small" type="primary" plain :disabled="!row.recheckAction.enabled" @click="openRecheck(row)">复检</el-button></span>
            </el-tooltip>
            <el-tooltip :content="row.stockInAction.reason" :disabled="row.stockInAction.enabled" placement="top">
              <span class="tip-wrap"><el-button size="small" type="success" :loading="actionLoadingId === row.id" :disabled="!row.stockInAction.enabled" @click="stockIn(row)">入库</el-button></span>
            </el-tooltip>
          </div>
          <div class="action-reason">
            <template v-if="row.status === '隔离待整改'">{{ row.rectifyAction.enabled ? row.rectifyAction.reason : row.recheckAction.reason }}</template>
            <template v-else-if="row.status === '隔离拒收'">{{ row.stockInAction.reason }}</template>
            <template v-else-if="row.status === '待质检'">登记合格结果后自动放行；不合格将隔离</template>
            <template v-else>{{ row.stockInAction.reason }}</template>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="inspectDialog" :title="inspectMode === 'initial' ? '登记初检抽样结果' : '登记复检抽样结果'" width="520px">
      <el-alert
        class="dialog-rule"
        :type="inspectMode === 'recheck' ? 'warning' : 'info'"
        :closable="false"
        :title="inspectMode === 'recheck' ? '本批次只有一次复检机会；复检仍不合格将保持隔离并拒绝入库。' : '含水率不超过 20% 且感官合格时自动质检放行。'"
      />
      <el-form label-width="96px">
        <el-form-item label="抽样编号" required>
          <el-input v-model="inspectForm.sampleNo" placeholder="如 S-2026-001" />
        </el-form-item>
        <el-form-item label="含水率(%)" required>
          <el-input-number v-model="inspectForm.moisturePercent" :min="0.01" :max="99.99" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="感官结论" required>
          <el-radio-group v-model="inspectForm.sensoryResult">
            <el-radio value="合格">合格</el-radio>
            <el-radio value="不合格">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检测人" required>
          <el-input v-model="inspectForm.inspector" placeholder="检测工作人员姓名" />
        </el-form-item>
        <el-form-item label="检测时间" required>
          <el-date-picker v-model="inspectForm.inspectedAt" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明" required>
          <el-input v-model="inspectForm.note" type="textarea" :rows="3" placeholder="感官、气味、色泽、储存或不合格处理意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectDialog = false">取消</el-button>
        <el-button type="primary" :loading="inspectSubmitting" @click="submitInspection">
          {{ inspectMode === 'initial' ? '提交初检结果' : '提交复检结果' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rectifyDialog" title="登记隔离批次整改说明" width="520px">
      <el-alert
        class="dialog-rule"
        type="warning"
        :closable="false"
        title="未补充整改说明前不能再次送检；保存后方可发起唯一一次复检。"
      />
      <el-form label-width="96px">
        <el-form-item label="整改说明" required>
          <el-input v-model="rectifyForm.opinion" type="textarea" :rows="4" placeholder="说明脱水、过滤、密封、隔离存放等整改措施" />
        </el-form-item>
        <el-form-item label="责任人" required>
          <el-input v-model="rectifyForm.handler" placeholder="整改责任人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectifyDialog = false">取消</el-button>
        <el-button type="warning" :loading="rectifySubmitting" @click="submitRectification">保存整改说明</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="history.visible" title="质检与处理追溯记录" size="620px">
      <div v-if="history.batch">
        <el-descriptions :column="2" border class="history-head">
          <el-descriptions-item label="批次号">{{ history.batch.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(history.batch.status)">{{ history.batch.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="蜂箱">{{ hiveCode(history.batch.hiveId) }}</el-descriptions-item>
          <el-descriptions-item label="重量">{{ history.batch.weightKg }} kg</el-descriptions-item>
        </el-descriptions>

        <h3>质检记录（初检/复检全历史）</h3>
        <el-table :data="history.batch.inspections" border>
          <el-table-column prop="roundType" label="轮次" width="70" />
          <el-table-column prop="sampleNo" label="抽样编号" width="125" />
          <el-table-column label="含水率" width="85">
            <template #default="{ row }">{{ row.moisturePercent }}%</template>
          </el-table-column>
          <el-table-column prop="sensoryResult" label="感官" width="70" />
          <el-table-column prop="result" label="结论" width="70">
            <template #default="{ row }"><el-tag :type="resultType(row.result)" size="small">{{ row.result }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="inspector" label="检测人" width="80" />
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatTime(row.inspectedAt) }}</template>
          </el-table-column>
          <el-table-column label="生效" width="70">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'info'" size="small">{{ row.active ? '生效' : '历史' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="180" show-overflow-tooltip />
        </el-table>

        <h3>处理意见与整改历史</h3>
        <el-timeline>
          <el-timeline-item v-for="h in history.batch.handlings" :key="h.id" :timestamp="formatTime(h.handledAt)" placement="top">
            <el-tag size="small" :type="h.handlingType === '拒绝入库' ? 'danger' : h.handlingType === '整改' ? 'warning' : 'success'">
              {{ h.handlingType }}
            </el-tag>
            <div class="handling-opinion">{{ h.opinion }}</div>
            <div class="sub">登记人：{{ h.handler }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!history.batch.handlings.length" description="暂无处理/整改记录" :image-size="70" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.bar h2 { margin: 0; font-size: 18px; }
.rule { margin-bottom: 12px; }
.filters { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; }
.filters > span { color: #666; font-size: 13px; }
.actions { display: flex; gap: 4px; flex-wrap: wrap; }
.tip-wrap { display: inline-block; }
.action-reason { margin-top: 6px; color: #888; font-size: 12px; line-height: 1.4; min-height: 32px; }
.action-guide { padding: 10px 16px; background: #fafafa; display: grid; grid-template-columns: 1fr 1fr; gap: 8px 18px; font-size: 13px; }
.ok { color: #67c23a; }
.muted { color: #909399; }
.sub { color: #909399; font-size: 12px; }
.ml4 { margin-left: 4px; }
.dialog-rule { margin-bottom: 14px; }
.history-head { margin-bottom: 16px; }
.history-head h3, h3 { margin: 18px 0 10px; font-size: 15px; }
.handling-opinion { margin: 6px 0 2px; line-height: 1.5; }
</style>
