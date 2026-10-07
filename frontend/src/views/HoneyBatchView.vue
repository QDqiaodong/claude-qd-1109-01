<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const list = ref([])
const hives = ref([])
const hiveMap = computed(() => {
  const m = {}
  hives.value.forEach(h => { m[h.id] = h })
  return m
})
const hiveCode = (id) => (hiveMap.value[id] ? hiveMap.value[id].code : ('#' + id))

const dialog = ref(false)
const editing = ref(false)
const statusFilter = ref('')
const form = reactive({ id: null, version: null, hiveId: null, batchNo: '', weightKg: 0 })
const STATUSES = ['待质检', '质检放行', '隔离待整改', '复检合格', '隔离拒收', '已入库', '已作废']
const RELEASED_STATUSES = ['质检放行', '复检合格']

const statusType = (s) => {
  if (['质检放行', '复检合格', '已入库'].includes(s)) return 'success'
  if (['隔离待整改', '隔离拒收'].includes(s)) return 'danger'
  if (s === '已作废') return 'info'
  return 'warning'
}

async function load() {
  const [b, h] = await Promise.all([
    api.listHoneyBatches(statusFilter.value),
    api.listHives()
  ])
  list.value = b
  hives.value = h
  emit('changed')
}
const emit = defineEmits(['changed'])

function openCreate() {
  editing.value = false
  form.id = null
  form.version = null
  form.hiveId = hives.value.length ? hives.value[0].id : null
  form.batchNo = ''
  form.weightKg = 0
  dialog.value = true
}
function openEdit(row) {
  editing.value = true
  form.id = row.id
  form.version = row.version
  form.hiveId = row.hiveId
  form.batchNo = row.batchNo
  form.weightKg = row.weightKg
  dialog.value = true
}

async function submit() {
  if (!form.weightKg || Number(form.weightKg) <= 0) {
    ElMessage.error('重量必须大于 0')
    return
  }
  try {
    const payload = {
      hiveId: form.hiveId,
      batchNo: form.batchNo,
      weightKg: Number(form.weightKg),
      version: form.version
    }
    if (editing.value) {
      await api.updateHoneyBatch(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await api.createHoneyBatch(payload)
      ElMessage.success('已新增')
    }
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function stockInReason(row) {
  if (RELEASED_STATUSES.includes(row.status)) return '质检已放行，可入库'
  if (row.status === '待质检') return '尚未质检合格，不能入库'
  if (row.status === '隔离待整改') return '批次隔离待整改，复检合格前不能入库'
  if (row.status === '隔离拒收') return '复检不合格，已明确拒绝入库'
  if (row.status === '已入库') return '批次已入库，请勿重复操作'
  if (row.status === '已作废') return '批次已作废，不能入库'
  return '质检未放行，不能入库'
}

async function stockIn(row) {
  try {
    await api.stockInHoneyBatch(row.id)
    ElMessage.success('已入库')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}
async function remove(row) {
  try {
    await api.deleteHoneyBatch(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <h2>采蜜批次</h2>
      <el-button type="primary" @click="openCreate">新增批次</el-button>
    </div>
    <div class="filters">
      <span>批次状态</span>
      <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 170px" @change="load">
        <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" plain @click="load">查询</el-button>
    </div>
    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="蜂箱" width="100">
        <template #default="{ row }">{{ hiveCode(row.hiveId) }}</template>
      </el-table-column>
      <el-table-column prop="batchNo" label="批次号" width="130" />
      <el-table-column prop="weightKg" label="重量(kg)" width="110" />
      <el-table-column label="批次状态" width="125">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质检状态" width="125">
        <template #default="{ row }">
          <el-tag :type="statusType(row.qualityStatus || row.status)">{{ row.qualityStatus || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230">
        <template #default="{ row }">
          <el-tooltip :content="row.status === '待质检' ? '可修改批次基础信息' : '批次已进入质检流转，基础信息锁定'" placement="top">
            <span class="tip-wrap"><el-button size="small" :disabled="row.status !== '待质检'" @click="openEdit(row)">编辑</el-button></span>
          </el-tooltip>
          <el-tooltip :content="stockInReason(row)" :disabled="RELEASED_STATUSES.includes(row.status)" placement="top">
            <span class="tip-wrap"><el-button size="small" type="success" :disabled="!RELEASED_STATUSES.includes(row.status)" @click="stockIn(row)">入库</el-button></span>
          </el-tooltip>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑批次' : '新增批次'" width="420px">
      <el-form label-width="72px">
        <el-form-item label="蜂箱">
          <el-select v-model="form.hiveId" style="width: 100%">
            <el-option v-for="h in hives" :key="h.id" :label="h.code + '（' + (h.location || '—') + '）'" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="form.batchNo" placeholder="如 B-2026-001" />
        </el-form-item>
        <el-form-item label="重量(kg)">
          <el-input-number v-model="form.weightKg" :min="0.01" :precision="2" :step="0.5" style="width: 100%" />
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
.filters { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.filters > span { color: #666; font-size: 13px; }
.tip-wrap { display: inline-block; }
</style>
