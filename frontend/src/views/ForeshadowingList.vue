<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getForeshadowings, createForeshadowing, updateForeshadowing, deleteForeshadowing, scanForeshadowings } from '@/api/book'
import { useRequest } from '@/composables/useRequest'
import ModalConfirm from '@/components/ModalConfirm.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const route = useRoute()
const bookId = route.params.id

const items = ref([])
const successMsg = ref('')
const errorMsg = ref('')
let msgTimer = null
const scanning = ref(false)

const typeLabels = { ITEM: '物品', EVENT: '事件', CHARACTER: '角色', RELATIONSHIP: '关系', PLOT_TWIST: '情节转折' }
const typeColors = { ITEM: '#f59e0b', EVENT: '#3b82f6', CHARACTER: '#7c3aed', RELATIONSHIP: '#ec4899', PLOT_TWIST: '#ef4444' }
const importanceLabels = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const importanceColors = { HIGH: '#dc2626', MEDIUM: '#f59e0b', LOW: '#9ca3af' }
const statusLabels = { ACTIVE: '活跃', RECYCLED: '已回收', DROPPED: '已放弃' }
const statusColors = { ACTIVE: '#16a34a', RECYCLED: '#3b82f6', DROPPED: '#9ca3af' }

const filterType = ref('')
const filterStatus = ref('')

// New/edit form
const showForm = ref(false)
const editingId = ref(null)
const form = ref({
  description: '',
  foreshadowing_type: 'EVENT',
  importance: 'MEDIUM',
  status: 'ACTIVE',
  target_entity: '',
})

// Confirm dialog
const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmAction = ref(() => {})

// Scan dialog
const scanStructureId = ref('')

const { execute: listExec, loading } = useRequest(getForeshadowings)

async function load() {
  try {
    const res = await listExec(bookId)
    items.value = res?.data || res || []
  } catch (e) {
    items.value = []
  }
}

onMounted(load)

watch(() => route.params.id, (newId) => {
  if (newId) load()
})

onUnmounted(() => {
  if (msgTimer) clearTimeout(msgTimer)
})

const filteredItems = computed(() => {
  let result = items.value
  if (filterType.value) result = result.filter(i => i.foreshadowing_type === filterType.value)
  if (filterStatus.value) result = result.filter(i => i.status === filterStatus.value)
  return result
})

function resetForm() {
  form.value = { description: '', foreshadowing_type: 'EVENT', importance: 'MEDIUM', status: 'ACTIVE', target_entity: '' }
}

function openEdit(item) {
  editingId.value = item.id
  form.value = {
    description: item.description || '',
    foreshadowing_type: item.foreshadowing_type || 'EVENT',
    importance: item.importance || 'MEDIUM',
    status: item.status || 'ACTIVE',
    target_entity: item.target_entity || '',
  }
  showForm.value = true
}

async function handleSave() {
  try {
    if (editingId.value) {
      await updateForeshadowing(bookId, editingId.value, form.value)
    } else {
      await createForeshadowing(bookId, form.value)
    }
    showForm.value = false
    successMsg.value = editingId.value ? '伏笔已更新' : '伏笔已添加'
    if (msgTimer) clearTimeout(msgTimer)
    msgTimer = setTimeout(() => successMsg.value = '', 2000)
    await load()
  } catch (e) {
    errorMsg.value = '保存失败: ' + (e.message || '未知错误')
  }
}

function confirmDelete(id) {
  confirmTitle.value = '确认删除'
  confirmMessage.value = '删除后不可恢复，确定删除此伏笔？'
  confirmAction.value = async () => {
    try {
      await deleteForeshadowing(bookId, id)
      confirmOpen.value = false
      successMsg.value = '已删除'
      if (msgTimer) clearTimeout(msgTimer)
    msgTimer = setTimeout(() => successMsg.value = '', 2000)
      await load()
    } catch (e) {
      errorMsg.value = '删除失败: ' + (e.message || '未知错误')
    }
  }
  confirmOpen.value = true
}

async function handleScan() {
  if (!scanStructureId.value) return
  scanning.value = true
  try {
    const res = await scanForeshadowings(bookId, scanStructureId.value)
    const data = res?.data || res || {}
    const newCount = data.new?.length || 0
    const recycledCount = data.recycled?.length || 0
    successMsg.value = `扫描完成：发现 ${newCount} 个新伏笔，${recycledCount} 个已回收`
    setTimeout(() => successMsg.value = '', 3000)
    await load()
  } catch (e) {
    errorMsg.value = '扫描失败: ' + (e.message || '未知错误')
  } finally {
    scanning.value = false
  }
}
</script>

<template>
  <div class="foreshadowing-page">
    <header class="page-header">
      <router-link v-if="route.query.from === 'hub'" to="/modules/foreshadowings" class="back-link">&larr; 返回伏笔工坊</router-link>
      <router-link v-else :to="`/books/${bookId}`" class="back-link">&larr; 返回书籍详情</router-link>
      <h1>伏笔管理</h1>
    </header>

    <div v-if="successMsg" class="success-msg">{{ successMsg }}</div>
    <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

    <div class="toolbar">
      <div class="toolbar-left">
        <button class="btn-primary" @click="showForm = true; editingId = null; resetForm()">添加伏笔</button>
        <div class="scan-group">
          <input
            v-model="scanStructureId"
            type="number"
            placeholder="章节结构ID"
            class="scan-input"
          />
          <button class="btn-scan" :disabled="scanning || !scanStructureId" @click="handleScan">
            {{ scanning ? '扫描中...' : 'AI 扫描章节' }}
          </button>
        </div>
      </div>
      <div class="toolbar-right">
        <select v-model="filterType" class="filter-select">
          <option value="">全部类型</option>
          <option v-for="(label, key) in typeLabels" :key="key" :value="key">{{ label }}</option>
        </select>
        <select v-model="filterStatus" class="filter-select">
          <option value="">全部状态</option>
          <option v-for="(label, key) in statusLabels" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>
    </div>

    <LoadingSpinner v-if="loading" />

    <div v-else-if="!items.length" class="empty-state">
      <p>暂无伏笔记录</p>
      <p class="empty-hint">手动添加或使用 AI 扫描章节自动识别伏笔</p>
    </div>

    <div v-else class="foreshadowing-list">
      <div
        v-for="item in filteredItems"
        :key="item.id"
        class="foreshadowing-card"
        :class="{ recycled: item.status === 'RECYCLED', dropped: item.status === 'DROPPED' }"
      >
        <div class="card-header">
          <span class="card-type" :style="{ background: typeColors[item.foreshadowing_type] || '#9ca3af' }">
            {{ typeLabels[item.foreshadowing_type] || item.foreshadowing_type }}
          </span>
          <span class="card-importance" :style="{ color: importanceColors[item.importance] || '#9ca3af' }">
            {{ importanceLabels[item.importance] || item.importance }}
          </span>
          <span class="card-status" :style="{ color: statusColors[item.status] || '#9ca3af' }">
            {{ statusLabels[item.status] || item.status }}
          </span>
        </div>
        <p class="card-desc">{{ item.description }}</p>
        <div v-if="item.target_entity" class="card-target">关联：{{ item.target_entity }}</div>
        <div class="card-actions">
          <button class="btn-sm" @click="openEdit(item)">编辑</button>
          <button class="btn-sm btn-sm-danger" @click="confirmDelete(item.id)">删除</button>
        </div>
      </div>
    </div>

    <!-- Form modal -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal-content">
        <h2>{{ editingId ? '编辑伏笔' : '添加伏笔' }}</h2>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="form.description" rows="3" placeholder="伏笔描述..."></textarea>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>类型</label>
            <select v-model="form.foreshadowing_type">
              <option v-for="(label, key) in typeLabels" :key="key" :value="key">{{ label }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>重要性</label>
            <select v-model="form.importance">
              <option value="HIGH">高</option>
              <option value="MEDIUM">中</option>
              <option value="LOW">低</option>
            </select>
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="form.status">
              <option value="ACTIVE">活跃</option>
              <option value="RECYCLED">已回收</option>
              <option value="DROPPED">已放弃</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>关联实体</label>
          <input v-model="form.target_entity" placeholder="可选，如角色名/物品名" />
        </div>
        <div class="modal-actions">
          <button class="btn-primary" @click="handleSave" :disabled="!form.description.trim()">保存</button>
          <button class="btn-cancel" @click="showForm = false">取消</button>
        </div>
      </div>
    </div>

    <ModalConfirm
      :visible="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      @confirm="confirmAction"
      @cancel="confirmOpen = false"
    />
  </div>
</template>

<style scoped>
.foreshadowing-page { max-width: 960px; margin: 0 auto; padding: 24px; }
.page-header { margin-bottom: 24px; }
.back-link { color: #5b3cc4; text-decoration: none; font-size: 14px; }
.back-link:hover { color: #4a2fa8; }
.page-header h1 { font-size: 24px; font-weight: 700; margin: 8px 0 0; color: var(--text-primary); }

.success-msg { padding: 8px 16px; margin-bottom: 16px; background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; border-radius: 6px; font-size: 13px; }
.error-msg { padding: 8px 16px; margin-bottom: 16px; background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; border-radius: 6px; font-size: 13px; }

.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; gap: 12px; flex-wrap: wrap; }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 10px; }
.scan-group { display: flex; gap: 6px; }
.scan-input { width: 100px; padding: 6px 10px; font-size: 13px; border: 1px solid var(--border-input); border-radius: 6px; }
.btn-primary { padding: 8px 18px; font-size: 13px; font-weight: 600; background: #5b3cc4; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-family: inherit; }
.btn-primary:hover { background: #4a2fa8; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-scan { padding: 8px 14px; font-size: 12px; font-weight: 500; background: #fff; color: #5b3cc4; border: 1px solid #5b3cc4; border-radius: 6px; cursor: pointer; font-family: inherit; }
.btn-scan:hover { background: #f5f3ff; }
.btn-scan:disabled { opacity: 0.5; cursor: not-allowed; }
.filter-select { padding: 7px 10px; font-size: 13px; border: 1px solid var(--border-input); border-radius: 6px; background: var(--bg-surface); color: var(--text-primary); font-family: inherit; }

.empty-state { text-align: center; padding: 60px 20px; color: var(--text-secondary); }
.empty-hint { font-size: 13px; margin-top: 8px; }

.foreshadowing-list { display: flex; flex-direction: column; gap: 10px; }
.foreshadowing-card { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 14px 18px; }
.foreshadowing-card.recycled { opacity: 0.7; border-left: 3px solid #3b82f6; }
.foreshadowing-card.dropped { opacity: 0.5; }
.card-header { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.card-type { padding: 2px 10px; font-size: 11px; font-weight: 600; color: #fff; border-radius: 4px; }
.card-importance { font-size: 12px; font-weight: 600; }
.card-status { font-size: 12px; font-weight: 500; margin-left: auto; }
.card-desc { font-size: 14px; color: var(--text-primary); line-height: 1.6; margin: 0 0 6px; }
.card-target { font-size: 12px; color: var(--text-secondary); }
.card-actions { display: flex; gap: 6px; margin-top: 10px; }
.btn-sm { padding: 4px 12px; font-size: 12px; font-weight: 500; background: var(--bg-surface-hover); color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 4px; cursor: pointer; font-family: inherit; }
.btn-sm:hover { background: var(--border-color); }
.btn-sm-danger { color: #dc2626; border-color: #fecaca; }
.btn-sm-danger:hover { background: #fee2e2; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal-content { background: var(--bg-surface); border-radius: 12px; padding: 24px 28px; width: 480px; max-width: 90vw; max-height: 80vh; overflow-y: auto; }
.modal-content h2 { font-size: 18px; font-weight: 700; margin: 0 0 18px; color: var(--text-primary); }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px; }
.form-group textarea, .form-group input, .form-group select { width: 100%; padding: 8px 12px; font-size: 14px; border: 1px solid var(--border-input); border-radius: 6px; background: var(--bg-surface); color: var(--text-primary); font-family: inherit; box-sizing: border-box; }
.form-row { display: flex; gap: 12px; }
.form-row .form-group { flex: 1; }
.modal-actions { display: flex; gap: 10px; margin-top: 20px; }
.btn-cancel { padding: 8px 18px; font-size: 13px; background: transparent; color: var(--text-secondary); border: 1px solid var(--border-color); border-radius: 6px; cursor: pointer; font-family: inherit; }
.btn-cancel:hover { background: var(--bg-surface-hover); }
</style>
