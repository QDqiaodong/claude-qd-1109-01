<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const list = ref([])
const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, name: '', phone: '', type: '个人' })
const TYPES = ['商超', '个人']

const typeType = (t) => (t === '商超' ? 'warning' : 'success')

async function load() {
  list.value = await api.listCustomers()
  emit('changed')
}
const emit = defineEmits(['changed'])

function openCreate() {
  editing.value = false
  form.id = null; form.name = ''; form.phone = ''; form.type = '个人'
  dialog.value = true
}
function openEdit(row) {
  editing.value = true
  form.id = row.id; form.name = row.name; form.phone = row.phone; form.type = row.type
  dialog.value = true
}

async function submit() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.error('手机号格式不正确（应为 1[3-9] 开头的 11 位号码）')
    return
  }
  try {
    if (editing.value) {
      await api.updateCustomer(form.id, { name: form.name, phone: form.phone, type: form.type })
      ElMessage.success('已保存')
    } else {
      await api.createCustomer({ name: form.name, phone: form.phone, type: form.type })
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
    await api.deleteCustomer(row.id)
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
      <h2>客户管理</h2>
      <el-button type="primary" @click="openCreate">新增客户</el-button>
    </div>
    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="客户名" />
      <el-table-column prop="phone" label="手机号" width="150" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="typeType(row.type)">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑客户' : '新增客户'" width="420px">
      <el-form label-width="72px">
        <el-form-item label="客户名">
          <el-input v-model="form.name" placeholder="如 鲜丰超市" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="1[3-9] 开头 11 位" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
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
