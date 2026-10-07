<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import router from './router'
import api from './api'

import HiveView from './views/HiveView.vue'
import InspectionView from './views/InspectionView.vue'
import HoneyBatchView from './views/HoneyBatchView.vue'
import QualityInspectionView from './views/QualityInspectionView.vue'
import CustomerView from './views/CustomerView.vue'

// ------------------------------------------------------------------
// 导航形态：顶部横向导航（el-menu horizontal）。
// 四个业务模块一字排开，点击切换；导航上实时显示各模块记录数。
// ------------------------------------------------------------------

const modules = [
  { path: '/hive',        label: '蜂箱',   sub: '箱号 · 状态', comp: HiveView },
  { path: '/inspection',  label: '巡检',   sub: '月历 · 健康', comp: InspectionView },
  { path: '/honey-batch', label: '采蜜批次', sub: '重量 · 入库', comp: HoneyBatchView },
  { path: '/quality-inspection', label: '质检放行', sub: '抽样 · 复检 · 整改', comp: QualityInspectionView },
  { path: '/customer',    label: '客户',   sub: '商超 · 个人', comp: CustomerView }
]

const currentPath = ref(router.currentRoute.value.path)
const counts = ref({})

const currentView = computed(() => {
  const m = modules.find((mm) => mm.path === currentPath.value)
  return m ? m.comp : HiveView
})

async function loadCounts() {
  try {
    const [h, i, b, q, c] = await Promise.all([
      api.listHives(), api.listInspections(), api.listHoneyBatches(), api.listQualityBatches(), api.listCustomers()
    ])
    counts.value = {
      '/hive': h.length, '/inspection': i.length,
      '/honey-batch': b.length, '/quality-inspection': q.length, '/customer': c.length
    }
  } catch (e) { /* 计数失败不影响主流程 */ }
}

function go(path) {
  router.push(path)
}

function onSelect(index) {
  go(index)
}

watch(() => router.currentRoute.value.path, (p) => { currentPath.value = p })

onMounted(async () => {
  currentPath.value = router.currentRoute.value.path
  await loadCounts()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">蜂</span>
        <div class="brand-text">
          <div class="brand-title">养蜂场 · 蜂箱台账与蜂蜜质检放行</div>
          <div class="brand-sub">顶部横向导航 · 蜜黄主题 · 巡检月历</div>
        </div>
      </div>
      <el-menu
        class="topmenu"
        mode="horizontal"
        :default-active="currentPath"
        @select="onSelect"
      >
        <el-menu-item v-for="m in modules" :key="m.path" :index="m.path">
          <span class="mi-label">{{ m.label }}</span>
          <span class="mi-count" v-if="counts[m.path] !== undefined">{{ counts[m.path] }}</span>
        </el-menu-item>
      </el-menu>
    </header>

    <main class="content">
      <component :is="currentView" @changed="loadCounts" />
    </main>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; display: flex; flex-direction: column; }

.topbar {
  display: flex; align-items: center; gap: 18px;
  padding: 0 22px; height: 62px;
  background: linear-gradient(100deg, var(--primary-deep) 0%, var(--primary) 60%, var(--primary-soft) 100%);
  color: #4a4334; box-shadow: 0 2px 12px rgba(201, 151, 0, 0.22);
}
.brand { display: flex; align-items: center; gap: 12px; }
.brand-mark {
  width: 38px; height: 38px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: #fff; color: var(--primary-deep); font-weight: 700; font-size: 20px;
}
.brand-title { font-size: 17px; font-weight: 600; }
.brand-sub { font-size: 12px; opacity: 0.78; }

.topmenu {
  flex: 1;
  background: transparent !important;
  border-bottom: none !important;
}
.topmenu :deep(.el-menu-item) {
  background: transparent !important;
  color: #5a4f2e !important;
  font-weight: 600;
  height: 62px; line-height: 62px;
}
.topmenu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.55) !important;
  color: var(--primary-deep) !important;
  border-bottom: 3px solid #fff !important;
}
.topmenu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.4) !important;
}
.mi-label { font-size: 15px; }
.mi-count {
  margin-left: 6px; min-width: 20px; height: 20px; padding: 0 5px;
  border-radius: 10px; background: var(--primary-deep); color: #fff;
  font-size: 12px; display: inline-flex; align-items: center; justify-content: center;
}

.content { flex: 1; padding: 20px 26px 30px; }
</style>
