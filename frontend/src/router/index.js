import { createRouter, createWebHashHistory } from 'vue-router'

// 注意：main.js 里没有 app.use(router)，路由实例由 App.vue 直接驱动，
// 所以这里全部用静态 import —— <component :is> 拿到的是组件对象本身。
import HiveView from '../views/HiveView.vue'
import InspectionView from '../views/InspectionView.vue'
import HoneyBatchView from '../views/HoneyBatchView.vue'
import QualityInspectionView from '../views/QualityInspectionView.vue'
import CustomerView from '../views/CustomerView.vue'

const routes = [
  { path: '/', redirect: '/hive' },
  { path: '/hive', name: 'hive', component: HiveView },
  { path: '/inspection', name: 'inspection', component: InspectionView },
  { path: '/honey-batch', name: 'honey-batch', component: HoneyBatchView },
  { path: '/quality-inspection', name: 'quality-inspection', component: QualityInspectionView },
  { path: '/customer', name: 'customer', component: CustomerView },
  { path: '/:pathMatch(.*)*', redirect: '/hive' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
