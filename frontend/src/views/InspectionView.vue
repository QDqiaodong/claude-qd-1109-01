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

const calDate = ref(new Date())
const inspectMap = ref({})          // 'YYYY-MM-DD' -> [inspection]
const selectedDay = ref(todayStr())

const dialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, hiveId: null, inspectDate: '', honeyKg: 0, beeHealth: '健康', note: ''
})
const HEALTHS = ['健康', '一般', '病态']

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

async function loadCalendar() {
  const m = monthStr(calDate.value)
  const list = await api.listInspections({ month: m })
  const map = {}
  list.forEach(i => {
    const k = String(i.inspectDate)
    ;(map[k] = map[k] || []).push(i)
  })
  inspectMap.value = map
}

const emit = defineEmits(['changed'])
function notifyChanged() { emit('changed') }

const dayInspections = computed(() => inspectMap.value[selectedDay.value] || [])

const healthColor = (h) =>
  h === '健康' ? '#67c23a' : h === '一般' ? '#e6a23c' : '#f56c6c'

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
function openCreate() {
  editing.value = false
  form.id = null
  form.hiveId = hives.value.length ? hives.value[0].id : null
  form.inspectDate = selectedDay.value
  form.honeyKg = 0
  form.beeHealth = '健康'
  form.note = ''
  dialog.value = true
}
function openEdit(row) {
  editing.value = true
  form.id = row.id; form.hiveId = row.hiveId; form.inspectDate = String(row.inspectDate)
  form.honeyKg = row.honeyKg; form.beeHealth = row.beeHealth; form.note = row.note || ''
  dialog.value = true
}

async function submit() {
  if (form.beeHealth === '病态' && (!form.note || !form.note.trim())) {
    ElMessage.error('蜂群健康为病态时必须填写备注')
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
      ElMessage.success('已记录')
    }
    dialog.value = false
    await loadCalendar(); notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function recheck(row) {
  try {
    await api.recheckInspection(row.id)
    ElMessage.success('已复检')
    await loadCalendar(); notifyChanged()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(calDate, () => loadCalendar())

onMounted(async () => {
  await loadHives()
  await loadCalendar()
})
</script>

<template>
  <div>
    <div class="bar">
      <h2>蜂箱巡检 · 月历</h2>
      <el-button type="primary" @click="openCreate">新增巡检</el-button>
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
            <div class="cell" @click="pickDay(data.day)">
              <div class="cell-num" :class="{ on: data.day === selectedDay }">
                {{ data.day.split('-')[2] }}
              </div>
              <div class="cell-dots">
                <span
                  v-for="ins in (inspectMap[data.day] || [])"
                  :key="ins.id"
                  class="dot"
                  :style="{ background: healthColor(ins.beeHealth) }"
                  :title="hiveCode(ins.hiveId) + ' · ' + ins.beeHealth"
                ></span>
              </div>
            </div>
          </template>
        </el-calendar>
      </div>

      <div class="panel">
        <div class="panel-title">{{ selectedDay }} 的巡检（{{ dayInspections.length }}）</div>
        <el-empty v-if="!dayInspections.length" description="当天暂无巡检" :image-size="60" />
        <div v-for="ins in dayInspections" :key="ins.id" class="ins-card">
          <div class="ins-row">
            <b>{{ hiveCode(ins.hiveId) }}</b>
            <el-tag size="small" :type="ins.beeHealth === '健康' ? 'success' : ins.beeHealth === '一般' ? 'warning' : 'danger'">
              {{ ins.beeHealth }}
            </el-tag>
            <el-tag size="small" :type="ins.status === '已复检' ? 'info' : 'primary'">{{ ins.status }}</el-tag>
          </div>
          <div class="ins-meta">产蜜 {{ ins.honeyKg }} kg</div>
          <div class="ins-meta" v-if="ins.note">备注：{{ ins.note }}</div>
          <div class="ins-actions">
            <el-button size="small" @click="openEdit(ins)">编辑</el-button>
            <el-button size="small" type="success" :disabled="ins.status === '已复检'" @click="recheck(ins)">复检</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑巡检' : '新增巡检'" width="440px">
      <el-form label-width="84px">
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
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="病态时必须填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.bar h2 { margin: 0; font-size: 18px; }
.layout { display: flex; gap: 18px; align-items: flex-start; }
.cal-wrap { flex: 1; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 8px 12px; }
.cal-head { display: flex; align-items: center; justify-content: center; gap: 16px; width: 100%; }
.cal-title { font-weight: 600; }
.cell { height: 100%; cursor: pointer; }
.cell-num { font-size: 13px; }
.cell-num.on { color: var(--primary-deep); font-weight: 700; }
.cell-dots { display: flex; gap: 3px; margin-top: 3px; flex-wrap: wrap; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.panel { width: 320px; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 14px; }
.panel-title { font-weight: 600; margin-bottom: 10px; }
.ins-card { border: 1px solid var(--line); border-radius: 10px; padding: 10px; margin-bottom: 10px; }
.ins-row { display: flex; align-items: center; gap: 6px; }
.ins-meta { font-size: 13px; color: var(--muted); margin-top: 4px; }
.ins-actions { margin-top: 8px; }
</style>
