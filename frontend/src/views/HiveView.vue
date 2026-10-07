<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const list = ref([])
const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, code: '', location: '', status: '待检' })
const STATUSES = ['正常', '待检', '患病']

const statusType = (s) => (s === '患病' ? 'danger' : s === '待检' ? 'warning' : 'success')

async function load() {
  list.value = await api.listHives()
  emit('changed')
}

const emit = defineEmits(['changed'])

function openCreate() {
  editing.value = false
  form.id = null; form.code = ''; form.location = ''; form.status = '待检'
  dialog.value = true
}

function openEdit(row) {
  editing.value = true
  form.id = row.id; form.code = row.code; form.location = row.location; form.status = row.status
  dialog.value = true
}

async function submit() {
  try {
    if (editing.value) {
      await api.updateHive(form.id, { code: form.code, location: form.location, status: form.status })
      ElMessage.success('已保存')
    } else {
      await api.createHive({ code: form.code, location: form.location, status: form.status })
      ElMessage.success('已新增')
    }
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function remove(row) {
  try {
    await api.deleteHive(row.id)
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
      <h2>蜂箱台账</h2>
      <el-button type="primary" @click="openCreate">新增蜂箱</el-button>
    </div>
    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="箱号" width="110" />
      <el-table-column prop="location" label="摆放点" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑蜂箱' : '新增蜂箱'" width="420px">
      <el-form label-width="72px">
        <el-form-item label="箱号">
          <el-input v-model="form.code" placeholder="如 H-01" />
        </el-form-item>
        <el-form-item label="摆放点">
          <el-input v-model="form.location" placeholder="如 南侧坡地" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
          </el-select>
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
</style>
