<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { listCharacters, createCharacter, updateCharacter, deleteCharacter, getBook } from '@/api/book'
import { randomCharacter, getGenerationStatus, characterFission } from '@/api/generation'
import { useRequest } from '@/composables/useRequest'
import { useDrafts } from '@/composables/useDrafts'
import ModalConfirm from '@/components/ModalConfirm.vue'
import RandomPreviewModal from '@/components/RandomPreviewModal.vue'
import CharacterGraph from '@/components/CharacterGraph.vue'
import CharacterChat from '@/components/CharacterChat.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import DraftsDrawer from '@/components/DraftsDrawer.vue'

const route = useRoute()
const bookId = computed(() => route.params.id)

const characters = ref([])
const expandedIds = reactive(new Set())
const viewMode = ref('grid')
const successMsg = ref('')
const sortBy = ref('default')
const randomDepth = ref('')

const depthOrder = { L3: 4, L2: 3, L1: 2, L0: 1 }

const sortedCharacters = computed(() => {
  const list = [...characters.value]
  switch (sortBy.value) {
    case 'depth-desc': return list.sort((a, b) => (depthOrder[b.depth_level] || 0) - (depthOrder[a.depth_level] || 0))
    case 'depth-asc': return list.sort((a, b) => (depthOrder[a.depth_level] || 0) - (depthOrder[b.depth_level] || 0))
    case 'name-asc': return list.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'zh'))
    case 'name-desc': return list.sort((a, b) => (b.name || '').localeCompare(a.name || '', 'zh'))
    case 'created-desc': return list.sort((a, b) => (b.id || 0) - (a.id || 0))
    case 'created-asc': return list.sort((a, b) => (a.id || 0) - (b.id || 0))
    default: return list
  }
})

// Slide-over edit panel — single instance, one at a time
const slide = ref({
  open: false,
  id: null,       // null = creating new, number = editing existing
  title: '',      // "新建角色" or "编辑角色"
})

const form = ref({
  name: '',
  gender: '',
  age_description: '',
  appearance: '',
  origin: '',
  identity: '',
  depth_level: 'L1',
  relationships: [],
})

const { loading, error, execute: fetchExec } = useRequest(listCharacters)
const { execute: createExec } = useRequest(createCharacter)
const { execute: updateExec } = useRequest(updateCharacter)
const { execute: deleteExec } = useRequest(deleteCharacter)

const randomGenerating = ref(false)
const fissionShow = ref(false)
const fissionChar = ref(null)
const fissionSimilarity = ref(50)
const fissionHint = ref('')
const fissionLoading = ref(false)
const preview = ref({ show: false, type: 'character', data: null })
const { add: addDraft } = useDrafts(bookId.value)
const synopsis = ref('')
const genStatus = ref({})
const statusWarning = ref('')
const bookTitle = ref('')

// Character chat
const chatVisible = ref(false)
const chatCharacter = ref(null)

function openChat(c) {
  chatCharacter.value = c
  chatVisible.value = true
}

async function loadSynopsis() {
  const res = await getBook(bookId.value)
  if (res) {
    synopsis.value = res.data?.core_idea || ''
    bookTitle.value = res.data?.title || ''
  }
}

async function checkStatus() {
  try {
    const s = await getGenerationStatus(bookId.value)
    genStatus.value = s
    if (!s.world_setting) statusWarning.value = '建议先生成「世界观设定」和「大纲」，再生成角色。'
    else if (!s.outline) statusWarning.value = '建议先生成「大纲」，再生成角色。'
    else statusWarning.value = ''
  } catch { /* ignore */ }
}

async function handleRandomCharacter() {
  if (!synopsis.value.trim()) {
    error.value = '请先在「大纲编排」页填写总纲（全书概要），再生成角色'
    return
  }
  randomGenerating.value = true
  error.value = ''
  try {
    const data = await randomCharacter(bookId.value, synopsis.value.trim(), randomDepth.value)
    preview.value = { show: true, type: 'character', data }
  } catch (e) {
    error.value = e.message || '生成失败'
  } finally {
    randomGenerating.value = false
  }
}

function openFission(c) {
  fissionChar.value = c
  fissionSimilarity.value = 50
  fissionHint.value = ''
  fissionShow.value = true
}
async function handleFission() {
  if (!fissionChar.value) return
  fissionLoading.value = true
  error.value = ''
  try {
    const data = await characterFission(fissionChar.value.id, fissionSimilarity.value, fissionHint.value.trim())
    fissionShow.value = false
    preview.value = { show: true, type: 'character', data }
  } catch (e) {
    error.value = e.message || '裂变失败'
  } finally {
    fissionLoading.value = false
  }
}

function applyCharacter() {
  const d = preview.value.data
  if (d) {
    slide.value = { open: true, id: null, title: '新建角色（随机生成）' }
    form.value = {
      name: d.name || '',
      gender: d.gender || '',
      age_description: d.age_description || '',
      appearance: d.appearance || '',
      origin: d.origin || '',
      identity: d.identity || '',
	      race: d.race || '',
      depth_level: d.depth_level || 'L1',
      relationships: [],
    }
  }
  preview.value = { show: false, type: 'character', data: null }
}

function draftCharacter() {
  addDraft('character', preview.value.data, preview.value.data?.name || '未命名角色')
  preview.value = { show: false, type: 'character', data: null }
}

function handleDraftApply(data) {
  slide.value = { open: true, id: null, title: '新建角色（草稿导入）' }
  form.value = {
    name: data.name || '',
    gender: data.gender || '',
    age_description: data.age_description || '',
    appearance: data.appearance || '',
    origin: data.origin || '',
    identity: data.identity || '',
	    race: data.race || '',
    depth_level: data.depth_level || 'L1',
    relationships: [],
  }
}

function closeCharacterPreview() {
  preview.value = { show: false, type: 'character', data: null }
}

const confirmModal = ref({
  visible: false,
  title: '',
  message: '',
  resolve: null,
})

const depthLabels = {
  L0: '背景板',
  L1: '配角',
  L2: '重要配角',
  L3: '主角',
}

const avatarColors = [
  '#E8D5F5', '#D5E8F5', '#D5F5E8', '#F5ECD5',
  '#F5D5E8', '#D5F5F5', '#F5F5D5', '#E8E8E8',
]

function getAvatarColor(name) {
  if (!name) return avatarColors[0]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return avatarColors[Math.abs(hash) % avatarColors.length]
}

function showConfirm(title, message) {
  return new Promise((resolve) => {
    confirmModal.value = { visible: true, title, message, resolve }
  })
}

function onConfirmModalConfirm() {
  if (confirmModal.value.resolve) confirmModal.value.resolve(true)
  confirmModal.value.visible = false
}

function onConfirmModalCancel() {
  if (confirmModal.value.resolve) confirmModal.value.resolve(false)
  confirmModal.value.visible = false
}

function showToast() {
  successMsg.value = '保存成功'
  setTimeout(() => { successMsg.value = '' }, 2000)
}

async function fetchCharacters() {
  const res = await fetchExec(bookId.value)
  if (res) characters.value = res.data || []
}

// ── Card expansion ──────────────────────────────
function toggleExpand(id) {
  if (expandedIds.has(id)) { expandedIds.delete(id) } else { expandedIds.add(id) }
}

function isExpanded(id) {
  return expandedIds.has(id)
}

// ── Slide-over panel ────────────────────────────
function openCreate() {
  slide.value = { open: true, id: null, title: '新建角色' }
  resetForm()
}

function openEdit(c) {
  slide.value = { open: true, id: c.id, title: '编辑角色' }
  const rels = parseRelations(c.extra_attributes)
  form.value = {
    name: c.name || '',
    gender: c.gender || '',
    age_description: c.age_description || '',
    appearance: c.appearance || '',
    origin: c.origin || '',
    identity: c.identity || '',
	    race: c.race || '',
    depth_level: c.depth_level || 'L1',
    relationships: rels.length ? rels : [],
  }
}

function closeSlide() {
  slide.value = { open: false, id: null, title: '' }
}

function parseRelations(ea) {
  try {
    const obj = typeof ea === 'string' ? JSON.parse(ea) : (ea || {})
    return obj.relationships || []
  } catch { return [] }
}

function resetForm() {
  form.value = {
    name: '',
    gender: '',
    age_description: '',
    appearance: '',
    origin: '',
	    race: '',
    identity: '',
    depth_level: 'L1',
    relationships: [],
  }
}

async function handleSlideSubmit() {
  if (!form.value.name.trim()) {
    error.value = '请输入角色名'
    return
  }
  // Serialize relationships into extra_attributes
  const payload = { ...form.value }
  const rels = (form.value.relationships || []).filter(r => r.targetId)
  if (rels.length > 0) {
    // Merge with existing extra_attributes
    let ea = {}
    if (slide.value.id) {
      const existing = characters.value.find(c => c.id === slide.value.id)
      ea = typeof existing?.extra_attributes === 'string'
        ? JSON.parse(existing.extra_attributes || '{}')
        : (existing?.extra_attributes || {})
    }
    ea.relationships = rels
    payload.extra_attributes = JSON.stringify(ea)
  } else {
    payload.extra_attributes = JSON.stringify({ relationships: [] })
  }
  delete payload.relationships

  let res
  if (slide.value.id) {
    res = await updateExec(bookId.value, slide.value.id, payload)
  } else {
    res = await createExec(bookId.value, payload)
  }
  if (res) {
    showToast()
    closeSlide()
    await fetchCharacters()
  }
}

async function handleDelete(id, name) {
  const confirmed = await showConfirm('确认删除', `确定删除角色「${name}」？此操作不可恢复。`)
  if (!confirmed) return
  await deleteExec(bookId.value, id)
  expandedIds.clear()
  await fetchCharacters()
}

onMounted(() => { fetchCharacters(); loadSynopsis(); checkStatus() })

watch(() => route.params.id, (newId) => {
  if (newId) { fetchCharacters(); loadSynopsis(); checkStatus() }
})
</script>

<template>
  <div class="characters-page">
    <!-- Page header -->
    <div class="page-header">
      <router-link v-if="route.query.from === 'hub'" to="/modules/characters" class="back-link">&larr; 返回角色工坊</router-link>
      <router-link v-else :to="`/books/${bookId}`" class="back-link">&larr; 返回书籍详情</router-link>
    </div>

    <!-- Section header -->
    <div class="section-header">
      <h1>角色管理</h1>
      <div class="header-actions">
        <DraftsDrawer :book-id="bookId" type="character" @apply="handleDraftApply" />
        <div class="view-toggle">
          <button
            :class="['toggle-btn', { active: viewMode === 'grid' }]"
            @click="viewMode = 'grid'"
            title="网格视图"
          >
            ▦
          </button>
          <button
            :class="['toggle-btn', { active: viewMode === 'list' }]"
            @click="viewMode = 'list'"
            title="列表视图"
          >
            ☰
          </button>
          <button
            :class="['toggle-btn', { active: viewMode === 'graph' }]"
            @click="viewMode = 'graph'"
            title="关系图"
          >
            ◉
          </button>
        </div>
        <select v-model="sortBy" class="sort-select" title="排序方式">
          <option value="default">默认排序</option>
          <option value="depth-desc">重要系数 ↓</option>
          <option value="depth-asc">重要系数 ↑</option>
          <option value="name-asc">姓名 A→Z</option>
          <option value="name-desc">姓名 Z→A</option>
          <option value="created-desc">创建时间 新→旧</option>
          <option value="created-asc">创建时间 旧→新</option>
        </select>
        <select v-model="randomDepth" class="depth-select" title="指定角色等级">
          <option value="">任意等级</option>
          <option value="L3">L3 主角</option>
          <option value="L2">L2 重要配角</option>
          <option value="L1">L1 配角</option>
          <option value="L0">L0 背景板</option>
        </select>
        <button class="btn-random-outline" :disabled="randomGenerating" @click="handleRandomCharacter">
          {{ randomGenerating ? '生成中...' : '随机角色' }}
        </button>
        <button class="btn-primary" @click="openCreate">+ 新建角色</button>
      </div>
    </div>

    <!-- Toast: success -->
    <transition name="toast-fade">
      <div v-if="successMsg" class="toast toast-success">{{ successMsg }}</div>
    </transition>

    <!-- Order warning -->
    <div v-if="statusWarning" class="toast toast-warning">{{ statusWarning }}</div>

    <!-- Toast: error -->
    <transition name="toast-fade">
      <div v-if="error" class="toast toast-error">{{ error }}</div>
    </transition>

    <!-- Loading -->
    <LoadingSpinner v-if="loading && characters.length === 0" />

    <!-- Graph view -->
    <div v-if="viewMode === 'graph' && characters.length > 0" class="graph-section">
      <CharacterGraph :characters="sortedCharacters" :book-id="bookId" @select="(id) => { openEdit(characters.find(c => c.id === id)) }" @refresh="fetchExec(bookId)" />
    </div>

    <!-- Empty -->
    <div v-else-if="characters.length === 0" class="state-box empty-state">
      <div class="empty-icon">📖</div>
      <h3>还没有角色</h3>
      <p>为你的故事添加第一个角色吧</p>
      <button class="btn-primary" @click="openCreate">创建第一个角色</button>
    </div>

    <!-- Character cards -->
    <div
      v-else-if="viewMode !== 'graph'"
      :class="['character-list', viewMode === 'grid' ? 'grid-view' : 'list-view']"
    >
      <div
        v-for="c in sortedCharacters"
        :key="c.id"
        :class="['character-card', { 'is-expanded': isExpanded(c.id) }]"
        @click="toggleExpand(c.id)"
      >
        <!-- Card top row (always visible) -->
        <div class="card-top">
          <div class="avatar-circle" :style="{ backgroundColor: getAvatarColor(c.name) }">
            <span class="avatar-text">{{ c.name?.charAt(0)?.toUpperCase() || '?' }}</span>
          </div>
          <div class="card-title-area">
            <span class="char-name">{{ c.name }}</span>
            <span :class="['depth-badge', 'depth-' + (c.depth_level || 'L1')]">
              {{ depthLabels[c.depth_level] || c.depth_level }}
            </span>
          </div>
          <span class="expand-arrow">{{ isExpanded(c.id) ? '▾' : '▸' }}</span>
        </div>

        <!-- Summary meta (always visible) -->
        <div class="card-meta">
          <span v-if="c.gender" class="meta-item">{{ c.gender }}</span>
          <span v-if="c.gender && c.identity" class="meta-sep">|</span>
          <span v-if="c.identity" class="meta-item">{{ c.identity }}</span>
        </div>

        <!-- Expanded detail (only visible when expanded) -->
        <div v-if="isExpanded(c.id)" class="card-detail" @click.stop>
          <div v-if="c.age_description" class="detail-row">
            <span class="detail-label">年龄段</span>
            <span class="detail-value">{{ c.age_description }}</span>
          </div>
          <div v-if="c.identity" class="detail-row">
            <span class="detail-label">身份</span>
            <span class="detail-value">{{ c.identity }}</span>
          </div>
          <div v-if="c.race" class="detail-row">
            <span class="detail-label">种族</span>
            <span class="detail-value">{{ c.race }}</span>
          </div>
          <div v-if="c.origin" class="detail-row">
            <span class="detail-label">出身</span>
            <span class="detail-value">{{ c.origin }}</span>
          </div>
          <div v-if="c.appearance" class="detail-row">
            <span class="detail-label">外貌</span>
            <span class="detail-value">{{ c.appearance }}</span>
          </div>

          <div class="card-actions">
            <button class="btn-outline" @click.stop="openChat(c)">对话</button>
            <button class="btn-outline" @click.stop="openEdit(c)">编辑</button>
            <button class="btn-outline btn-fission" @click.stop="openFission(c)">裂变</button>
            <button class="btn-outline btn-outline-danger" @click.stop="handleDelete(c.id, c.name)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══════════════════════════════════════
         Slide-over Edit Panel
         ═══════════════════════════════════════ -->
    <Teleport to="body">
      <transition name="slide">
        <div v-if="slide.open" class="slide-overlay" @click.self="closeSlide">
          <div class="slide-panel">
            <div class="slide-header">
              <h2>{{ slide.title }}</h2>
              <button class="slide-close" @click="closeSlide">&times;</button>
            </div>
            <form class="slide-body" @submit.prevent="handleSlideSubmit">
              <div class="form-group">
                <label>角色名 <span class="required">*</span></label>
                <input v-model="form.name" placeholder="角色名" maxlength="100" />
              </div>
              <div class="form-row two-cols">
                <div class="form-group">
                  <label>性别</label>
                  <select v-model="form.gender">
                    <option value="">不限</option>
                    <option value="男">男</option>
                    <option value="女">女</option>
                    <option value="其他">其他</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>深度等级</label>
                  <select v-model="form.depth_level">
                    <option value="L0">L0 - 背景板</option>
                    <option value="L1">L1 - 配角</option>
                    <option value="L2">L2 - 重要配角</option>
                    <option value="L3">L3 - 主角</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label>年龄段描述</label>
                <input v-model="form.age_description" placeholder="如：二十五岁、中年、少年" />
              </div>
              <div class="form-group">
                <label>种族</label>
                <input v-model="form.race" placeholder="如：人类、精灵、兽人、仙族、魔族..." />
              </div>
              <div class="form-group">
                <label>身份</label>
                <input v-model="form.identity" placeholder="如：剑客、魔法学院学生" />
              </div>
              <div class="form-group">
                <label>出身背景</label>
                <textarea v-model="form.origin" rows="3" placeholder="出身、成长环境"></textarea>
              </div>
              <div class="form-group">
                <label>外貌</label>
                <textarea v-model="form.appearance" rows="3" placeholder="外貌特征描述"></textarea>
              </div>

              <!-- ── Relationships ── -->
              <div class="form-group">
                <label>角色关系</label>
                <div class="rel-list">
                  <div v-for="(rel, idx) in form.relationships" :key="idx" class="rel-row">
                    <select v-model="rel.targetId" class="rel-target">
                      <option :value="null" disabled>选择角色</option>
                      <option
                        v-for="c in characters.filter(x => slide.id === null || x.id !== slide.id)"
                        :key="c.id"
                        :value="c.id"
                      >{{ c.name }}</option>
                    </select>
                    <select v-model="rel.type" class="rel-type">
                      <option value="">关系</option>
                      <option value="挚友">挚友</option>
                      <option value="朋友">朋友</option>
                      <option value="恋人">恋人</option>
                      <option value="夫妻">夫妻</option>
                      <option value="亲人">亲人</option>
                      <option value="师徒">师徒</option>
                      <option value="仇敌">仇敌</option>
                      <option value="对手">对手</option>
                      <option value="同门">同门</option>
                      <option value="陌生">陌生</option>
                      <option value="其他">其他</option>
                    </select>
                    <button type="button" class="rel-remove" @click="form.relationships.splice(idx, 1)">&times;</button>
                  </div>
                </div>
                <button type="button" class="rel-add" @click="form.relationships.push({ targetId: null, type: '', description: '' })">
                  + 添加关系
                </button>
              </div>

              <div class="slide-footer">
                <button type="submit" class="btn-primary" :disabled="loading">
                  {{ slide.id ? '保存修改' : '创建角色' }}
                </button>
                <button type="button" class="btn-cancel" @click="closeSlide">取消</button>
              </div>
            </form>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- Delete confirm -->
    <ModalConfirm
      :visible="confirmModal.visible"
      :title="confirmModal.title"
      :message="confirmModal.message"
      danger
      @confirm="onConfirmModalConfirm"
      @cancel="onConfirmModalCancel"
    />

    <RandomPreviewModal
      :visible="preview.show"
      type="character"
      :data="preview.data"
      :loading="false"
      @apply="applyCharacter"
      @draft="draftCharacter"
      @close="closeCharacterPreview"
    />

    <!-- Fission modal -->
    <Teleport to="body">
      <div v-if="fissionShow" class="fission-overlay" @click.self="fissionShow = false">
        <div class="fission-modal">
          <div class="fission-header">
            <h3>角色裂变</h3>
            <button class="fission-close" @click="fissionShow = false">&times;</button>
          </div>
          <div class="fission-source">
            源角色：<strong>{{ fissionChar?.name }}</strong>
            <span v-if="fissionChar?.depth_level" class="depth-badge-sm">{{ fissionChar.depth_level }}</span>
          </div>
          <div class="fission-field">
            <label>相似度 <span class="fission-val">{{ fissionSimilarity }}%</span></label>
            <input type="range" v-model.number="fissionSimilarity" min="0" max="100" step="5" class="fission-slider" />
            <div class="fission-range-labels"><span>完全不同</span><span>克隆变体</span></div>
          </div>
          <div class="fission-field">
            <label>额外提示（可选）</label>
            <textarea v-model="fissionHint" rows="2" placeholder="例如：生成一个反派、改为女性角色、来自敌对势力..." class="fission-hint"></textarea>
          </div>
          <div class="fission-actions">
            <button class="btn btn-primary" :disabled="fissionLoading" @click="handleFission">{{ fissionLoading ? '裂变中...' : '开始裂变' }}</button>
            <button class="btn btn-outline" @click="fissionShow = false">取消</button>
          </div>
        </div>
      </div>
    </Teleport>

    <CharacterChat
      :character="chatCharacter"
      :visible="chatVisible"
      @close="chatVisible = false"
    />
  </div>
</template>

<style scoped>
.characters-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

/* ── Page header ── */
.page-header {
  margin-bottom: 20px;
}
.back-link {
  color: #5b3cc4;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;
}
.back-link:hover {
  color: #4a2fa8;
}

/* ── Section header ── */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.section-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ── View toggle ── */
.view-toggle {
  display: flex;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
}
.toggle-btn {
  padding: 6px 12px;
  border: none;
  background: var(--bg-surface);
  color: #888;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  transition: all 0.2s;
}
.toggle-btn.active {
  background: #f0e6ff;
  color: #7c3aed;
}
.toggle-btn:not(.active):hover {
  background: #f5f5f5;
}

/* ── Toast ── */
.toast {
  padding: 10px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 500;
}
.toast-success { background: var(--bg-success-soft); color: var(--color-success); border: 1px solid var(--border-success-soft); }
.toast-error { background: var(--bg-error-soft); color: var(--color-danger); border: 1px solid var(--border-error-soft); }
.toast-warning { background: #fefce8; color: #854d0e; border: 1px solid #fde68a; }
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ── Character list ── */
.character-list {
  display: grid;
  gap: 16px;
}
.grid-view {
  grid-template-columns: 1fr 1fr;
}
.list-view {
  grid-template-columns: 1fr;
}

/* ── Character card ── */
.character-card {
  background: var(--bg-surface);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: 1px solid var(--border-color);
  padding: 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
  user-select: none;
}
.character-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}
.character-card.is-expanded {
  border-color: #d8c8f0;
  box-shadow: 0 4px 16px rgba(124, 58, 237, 0.1);
}

/* ── Card top row ── */
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar-circle {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.card-title-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.char-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.expand-arrow {
  font-size: 14px;
  color: #aaa;
  flex-shrink: 0;
}

/* ── Depth badge ── */
.depth-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 12px;
  white-space: nowrap;
}
.depth-L0 { background: var(--bg-surface-hover); color: var(--text-secondary); }
.depth-L1 { background: #e8f0fe; color: #1a56db; }
.depth-L2 { background: #fef3e0; color: #b45309; }
.depth-L3 { background: #f0e6ff; color: #7c3aed; }

/* ── Card meta ── */
.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
}
.meta-item { color: var(--text-secondary); }
.meta-sep { color: var(--border-color); }

/* ── Expanded detail ── */
.card-detail {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border-color);
  cursor: default;
}
.detail-row {
  margin-bottom: 10px;
}
.detail-label {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: #999;
  width: 48px;
  flex-shrink: 0;
  letter-spacing: 0.5px;
}
.detail-value {
  font-size: 13px;
  color: #444;
  line-height: 1.6;
}

/* ── Card actions ── */
.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

/* ── Buttons ── */
.btn-primary {
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  background: #7c3aed;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-primary:hover { background: #6d28d9; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-random-outline {
  padding: 8px 18px;
  border: 1px solid var(--color-brand);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--color-brand);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}
.btn-random-outline:hover:not(:disabled) { background: #f5f3ff; }
.btn-random-outline:disabled { opacity: 0.5; cursor: not-allowed; }

.sort-select, .depth-select {
  padding: 7px 10px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 13px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  max-width: 130px;
}
.sort-select:focus, .depth-select:focus { outline: none; border-color: #7c3aed; }

.btn-cancel {
  padding: 8px 20px;
  border: 1px solid #d0d0d0;
  border-radius: 8px;
  background: var(--bg-surface);
  color: #555;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-cancel:hover { background: #f5f5f5; }

.btn-outline {
  padding: 6px 16px;
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  background: var(--bg-surface);
  color: #555;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-outline:hover {
  border-color: #7c3aed;
  color: #7c3aed;
}
.btn-outline-danger {
  color: #e74c3c;
  border-color: #e74c3c;
}
.btn-outline-danger:hover {
  background: #fef2f2;
}
.btn-fission { color: #7c3aed; border-color: #c4b5fd; }
.btn-fission:hover { background: #f5f3ff; }

/* ── Fission modal ── */
.fission-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center;
}
.fission-modal {
  background: var(--bg-surface); border-radius: 12px;
  padding: 24px; width: 420px; max-width: 90vw;
  box-shadow: 0 8px 30px rgba(0,0,0,0.15);
}
.fission-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.fission-header h3 { font-size: 18px; font-weight: 700; margin: 0; }
.fission-close { font-size: 22px; background: none; border: none; cursor: pointer; color: var(--text-secondary); }
.fission-source { font-size: 14px; color: var(--text-secondary); margin-bottom: 16px; padding: 8px 12px; background: var(--bg-surface-hover); border-radius: 6px; }
.depth-badge-sm { display: inline-block; margin-left: 6px; padding: 1px 6px; font-size: 11px; font-weight: 600; background: #ede9fe; color: #6d28d9; border-radius: 4px; }
.fission-field { margin-bottom: 16px; }
.fission-field label { display: block; font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; }
.fission-val { color: #7c3aed; font-weight: 700; }
.fission-slider { width: 100%; accent-color: #7c3aed; }
.fission-range-labels { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.fission-hint { width: 100%; padding: 8px 12px; font-size: 13px; font-family: inherit; border: 1px solid var(--border-input); border-radius: 6px; resize: vertical; }
.fission-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 20px; }

/* ── Form controls ── */
.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #444;
  margin-bottom: 6px;
}
.required { color: #e74c3c; }

.form-row {
  display: flex;
  gap: 16px;
}
.two-cols > .form-group {
  flex: 1;
  min-width: 0;
}

input, select, textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-surface);
  box-sizing: border-box;
  transition: border-color 0.2s;
  font-family: inherit;
}
input:focus, select:focus, textarea:focus {
  outline: none;
  border-color: #7c3aed;
}
textarea { resize: vertical; }

/* ── Slide-over panel ── */
.slide-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.slide-panel {
  width: 420px;
  max-width: 100vw;
  height: 100vh;
  background: var(--bg-surface);
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.slide-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.slide-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.slide-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #888;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}
.slide-close:hover { color: var(--text-primary); }
.slide-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}
.slide-footer {
  display: flex;
  gap: 10px;
  padding-top: 20px;
  border-top: 1px solid var(--border-color);
  margin-top: 20px;
}

/* Slide transition */
.slide-enter-active,
.slide-leave-active {
  transition: opacity 0.25s ease;
}
.slide-enter-active .slide-panel,
.slide-leave-active .slide-panel {
  transition: transform 0.25s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
}
.slide-enter-from .slide-panel {
  transform: translateX(100%);
}
.slide-leave-to .slide-panel {
  transform: translateX(100%);
}

/* ── State boxes ── */
.state-box {
  text-align: center;
  padding: 80px 20px;
  color: #888;
  font-size: 14px;
}
.empty-icon { font-size: 64px; margin-bottom: 16px; }
.state-box h3 { margin: 0 0 8px; font-size: 20px; color: var(--text-primary); }
.state-box p { margin: 0 0 24px; }
.spinner {
  width: 32px; height: 32px;
  border: 3px solid #e0e0e0;
  border-top-color: #7c3aed;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  margin: 0 auto 12px;
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 640px) {
  .grid-view { grid-template-columns: 1fr; }
  .section-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .two-cols { flex-direction: column; gap: 0; }
  .slide-panel { width: 100vw; }
}

/* ── Graph section ── */
.graph-section {
  margin-bottom: 24px;
}

/* ── Relationship edit ── */
.rel-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.rel-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.rel-target {
  flex: 2;
  padding: 7px 10px;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  background: var(--bg-input);
  color: var(--text-primary);
}

.rel-type {
  flex: 1;
  padding: 7px 10px;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  background: var(--bg-input);
  color: var(--text-primary);
}

.rel-remove {
  background: none;
  border: none;
  color: #ef4444;
  font-size: 20px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1;
  transition: background 0.12s;
  font-family: inherit;
}

.rel-remove:hover {
  background: #fef2f2;
}

.rel-add {
  padding: 6px 14px;
  background: var(--bg-surface);
  color: var(--color-brand);
  border: 1px dashed var(--color-brand);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.rel-add:hover {
  background: #f5f3ff;
  border-style: solid;
}
</style>
