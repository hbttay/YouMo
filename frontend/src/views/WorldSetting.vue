<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getWorldSetting, saveWorldSetting, getBook, updateBook } from '@/api/book'
import { randomWorldSetting } from '@/api/generation'
import { useRequest } from '@/composables/useRequest'
import { useDrafts } from '@/composables/useDrafts'
import RandomPreviewModal from '@/components/RandomPreviewModal.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import DraftsDrawer from '@/components/DraftsDrawer.vue'

const route = useRoute()
const bookId = route.params.id
const { add: addDraft } = useDrafts(bookId)

// ── AI injection settings ──
const injectionLevel = ref('STANDARD')
const injectionSaving = ref(false)
const injectionMsg = ref('')

const fieldWeights = ref({
  era: 5, geography: 5, history_events: 5,
  politics: 5, economy: 5, culture: 5,
  military: 5, core_rule: 5,
})

const FIELD_LABELS = {
  era: '时代背景',
  geography: '地理环境',
  history_events: '历史事件',
  politics: '政治体制',
  economy: '经济体系',
  culture: '文化',
  military: '军事',
  core_rule: '核心规则',
}

const weightSaving = ref(false)
const weightMsg = ref('')

const LEVEL_OPTIONS = [
  { value: 'MINIMAL', label: '精简', desc: '仅注入最相关的 2-3 个字段，~200 字，节省 token，适合对话/动作场景' },
  { value: 'STANDARD', label: '标准', desc: '注入 4-6 个相关字段，~500 字，平衡上下文相关性（推荐）' },
  { value: 'DETAILED', label: '详细', desc: '注入全部字段 + 自定义属性，~1000 字，世界观构建场景' },
]

const { loading, error, execute: fetchSetting } = useRequest(async (id) => {
  const res = await getWorldSetting(id)
  if (res.data) {
    form.value = {
      era: res.data.era || '',
      geography: res.data.geography || '',
      history_events: res.data.history_events || '',
      politics: res.data.politics || '',
      economy: res.data.economy || '',
      culture: res.data.culture || '',
      military: res.data.military || '',
      core_rule_type: res.data.core_rule_type || '',
      core_rule_summary: res.data.core_rule_summary || '',
    }
  }
  return res
})

const saving = ref(false)
const success = ref('')
const generating = ref(false)
const preview = ref({ show: false, type: 'world-setting', data: null })
const synopsis = ref('')
const bookTitle = ref('')

async function loadSynopsis() {
  const res = await getBook(bookId)
  if (res) {
    synopsis.value = res.data?.core_idea || ''
    bookTitle.value = res.data?.title || ''
    try {
      const attrs = JSON.parse(res.data?.extra_attributes || '{}')
      if (attrs.ai_injection_level) injectionLevel.value = attrs.ai_injection_level
      if (attrs.ai_field_weights) {
        fieldWeights.value = { ...fieldWeights.value, ...attrs.ai_field_weights }
      }
    } catch {}
  }
}

async function saveInjectionLevel() {
  injectionSaving.value = true
  injectionMsg.value = ''
  try {
    const res = await getBook(bookId)
    const attrs = JSON.parse(res.data?.extra_attributes || '{}')
    attrs.ai_injection_level = injectionLevel.value
    await updateBook(bookId, { extra_attributes: JSON.stringify(attrs) })
    injectionMsg.value = '已保存'
    setTimeout(() => { injectionMsg.value = '' }, 2000)
  } catch (e) {
    injectionMsg.value = e.response?.data?.message || '保存失败'
  } finally {
    injectionSaving.value = false
  }
}

async function saveFieldWeights() {
  weightSaving.value = true
  weightMsg.value = ''
  try {
    const res = await getBook(bookId)
    const attrs = JSON.parse(res.data?.extra_attributes || '{}')
    attrs.ai_field_weights = fieldWeights.value
    await updateBook(bookId, { extra_attributes: JSON.stringify(attrs) })
    weightMsg.value = '已保存'
    setTimeout(() => { weightMsg.value = '' }, 2000)
  } catch (e) {
    weightMsg.value = e.response?.data?.message || '保存失败'
  } finally {
    weightSaving.value = false
  }
}

async function handleRandomWorld() {
  if (!synopsis.value.trim()) {
    error.value = '请先在「大纲编排」页填写总纲（全书概要），再生成世界观'
    return
  }
  generating.value = true
  error.value = ''
  success.value = ''
  try {
    const data = await randomWorldSetting(bookId, synopsis.value.trim())
    preview.value = { show: true, type: 'world-setting', data }
  } catch (e) {
    error.value = e.message || '生成失败'
  } finally {
    generating.value = false
  }
}

function applyWorld() {
  const d = preview.value.data
  if (d) {
    if (d.era) form.value.era = d.era
    if (d.geography) form.value.geography = d.geography
    if (d.history_events) form.value.history_events = d.history_events
    if (d.politics) form.value.politics = d.politics
    if (d.economy) form.value.economy = d.economy
    if (d.culture) form.value.culture = d.culture
    if (d.military) form.value.military = d.military
    if (d.core_rule_type) form.value.core_rule_type = d.core_rule_type
    if (d.core_rule_summary) form.value.core_rule_summary = d.core_rule_summary
    success.value = '已应用，可修改后保存'
    setTimeout(() => { success.value = '' }, 3000)
  }
  preview.value = { show: false, type: 'world-setting', data: null }
}

function draftWorld() {
  addDraft('world-setting', preview.value.data, '世界观草稿')
  preview.value = { show: false, type: 'world-setting', data: null }
}

function handleDraftApply(data) {
  if (data.era) form.value.era = data.era
  if (data.geography) form.value.geography = data.geography
  if (data.history_events) form.value.history_events = data.history_events
  if (data.politics) form.value.politics = data.politics
  if (data.economy) form.value.economy = data.economy
  if (data.culture) form.value.culture = data.culture
  if (data.military) form.value.military = data.military
  if (data.core_rule_type) form.value.core_rule_type = data.core_rule_type
  if (data.core_rule_summary) form.value.core_rule_summary = data.core_rule_summary
  success.value = '已导入草稿，可修改后保存'
  setTimeout(() => { success.value = '' }, 3000)
}

function closeWorldPreview() {
  preview.value = { show: false, type: 'world-setting', data: null }
}

const form = ref({
  era: '',
  geography: '',
  history_events: '',
  politics: '',
  economy: '',
  culture: '',
  military: '',
  core_rule_type: '',
  core_rule_summary: '',
})

async function handleSave() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await saveWorldSetting(bookId, form.value)
    success.value = '保存成功'
    setTimeout(() => success.value = '', 2000)
  } catch (e) {
    error.value = e.response?.data?.message || '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => { fetchSetting(bookId); loadSynopsis() })

watch(() => route.params.id, (newId) => {
  if (newId) { fetchSetting(newId); loadSynopsis() }
})
</script>

<template>
  <div class="world-setting">
    <div class="page-header">
      <router-link v-if="route.query.from === 'hub'" to="/modules/world" class="back-link">&larr; 返回世界工坊</router-link>
      <router-link v-else :to="`/books/${bookId}`" class="back-link">&larr; 返回书籍详情</router-link>
    </div>

    <div class="section-header">
      <h1>世界观设定</h1>
      <div class="header-actions">
        <DraftsDrawer :book-id="bookId" type="world-setting" @apply="handleDraftApply" />
        <button class="btn-random" :disabled="generating" @click="handleRandomWorld">
          {{ generating ? '生成中...' : '随机世界观' }}
        </button>
        <button class="btn-primary" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>

    <div v-if="success" class="success-msg">{{ success }}</div>
    <div v-if="error" class="error-msg">{{ error }}</div>

    <LoadingSpinner v-if="loading" />

    <form v-else @submit.prevent="handleSave" class="setting-form">
      <div class="form-group">
        <label>时代背景</label>
        <textarea v-model="form.era" rows="4" placeholder="如：近未来 2087 年，第三次世界大战后的废墟世界"></textarea>
      </div>

      <div class="form-group">
        <label>地理环境</label>
        <textarea v-model="form.geography" rows="4" placeholder="世界地理、主要区域、气候等"></textarea>
      </div>

      <div class="form-group">
        <label>历史事件</label>
        <textarea v-model="form.history_events" rows="4" placeholder="重要历史事件节点，用 JSON 数组或自由文本"></textarea>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>政治体制</label>
          <textarea v-model="form.politics" rows="4" placeholder="政权结构、治理模式"></textarea>
        </div>
        <div class="form-group">
          <label>经济体系</label>
          <textarea v-model="form.economy" rows="4" placeholder="货币、贸易、资源"></textarea>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>文化</label>
          <textarea v-model="form.culture" rows="4" placeholder="信仰、习俗、价值观"></textarea>
        </div>
        <div class="form-group">
          <label>军事</label>
          <textarea v-model="form.military" rows="4" placeholder="军队、武器、冲突"></textarea>
        </div>
      </div>

      <div class="form-group">
        <label>核心规则类型</label>
        <input v-model="form.core_rule_type" placeholder="如：魔法体系、修仙等级、科技树" />
      </div>

      <div class="form-group">
        <label>核心规则简述</label>
        <textarea v-model="form.core_rule_summary" rows="4" placeholder="描述世界观中的核心规则设定"></textarea>
      </div>
    </form>

    <div class="injection-section">
      <h2>AI 续写世界观注入</h2>
      <p class="injection-desc">控制续写时将多少世界观设定注入到 AI 上下文中。更多设定 = 更好的一致性，但消耗更多 token。</p>

      <div class="injection-options">
        <label v-for="opt in LEVEL_OPTIONS" :key="opt.value" class="injection-option"
          :class="{ active: injectionLevel === opt.value }">
          <input type="radio" v-model="injectionLevel" :value="opt.value" />
          <div class="option-content">
            <span class="option-label">{{ opt.label }}</span>
            <span class="option-desc">{{ opt.desc }}</span>
          </div>
        </label>
      </div>

      <div class="injection-footer">
        <button class="btn-primary" :disabled="injectionSaving" @click="saveInjectionLevel">
          {{ injectionSaving ? '保存中...' : '保存设置' }}
        </button>
        <span v-if="injectionMsg" class="injection-msg">{{ injectionMsg }}</span>
      </div>

      <h3 style="margin-top: 24px;">字段权重</h3>
      <p class="injection-desc">调整各世界观字段的注入优先级。权重越高，该字段越容易被 AI 续写时引用。</p>
      <div class="weight-list">
        <div v-for="(value, field) in fieldWeights" :key="field" class="weight-row">
          <span class="weight-label">{{ FIELD_LABELS[field] || field }}</span>
          <input type="range" min="0" max="10" v-model.number="fieldWeights[field]" class="weight-slider" />
          <span class="weight-value">{{ fieldWeights[field] }}</span>
        </div>
      </div>

      <div class="injection-footer">
        <button class="btn-primary" :disabled="weightSaving" @click="saveFieldWeights">
          {{ weightSaving ? '保存中...' : '保存权重' }}
        </button>
        <span v-if="weightMsg" class="injection-msg">{{ weightMsg }}</span>
      </div>
    </div>

    <RandomPreviewModal
      :visible="preview.show"
      type="world-setting"
      :data="preview.data"
      :loading="false"
      @apply="applyWorld"
      @draft="draftWorld"
      @close="closeWorldPreview"
    />
  </div>
</template>

<style scoped>
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.section-header h1 { font-size: 22px; font-weight: 600; margin: 0; }
.header-actions { display: flex; gap: 10px; align-items: center; }
.btn-random {
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
  white-space: nowrap;
}
.btn-random:hover:not(:disabled) { background: #f5f3ff; }
.btn-random:disabled { opacity: 0.5; cursor: not-allowed; }

.setting-form { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 24px; }

.success-msg { background: #f0fdf4; color: var(--color-success); padding: 10px 14px; border-radius: 6px; font-size: 13px; margin-bottom: 14px; }
.error-msg { background: #fef2f2; color: var(--color-danger); padding: 10px 14px; border-radius: 6px; font-size: 13px; margin-bottom: 14px; }

/* ── AI injection settings ── */
.injection-section {
  margin-top: 32px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 24px;
}
.injection-section h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px 0;
}
.injection-desc {
  font-size: 13px;
  color: var(--color-muted);
  margin: 0 0 16px 0;
  line-height: 1.5;
}
.injection-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.injection-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.injection-option:hover { background: #f9f8ff; }
.injection-option.active {
  border-color: var(--color-brand);
  background: #f5f3ff;
}
.injection-option input[type="radio"] { margin-top: 2px; accent-color: var(--color-brand); flex-shrink: 0; }
.option-content { display: flex; flex-direction: column; gap: 3px; }
.option-label { font-size: 14px; font-weight: 600; }
.option-desc { font-size: 12px; color: var(--color-muted); line-height: 1.4; }
.injection-footer {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.injection-msg { font-size: 13px; color: var(--color-success); }

.weight-list {
  display: flex; flex-direction: column; gap: 8px;
}
.weight-row {
  display: flex; align-items: center; gap: 12px;
}
.weight-label {
  width: 80px; font-size: 13px; color: var(--text-secondary); flex-shrink: 0;
}
.weight-slider {
  flex: 1; height: 4px; accent-color: #7c3aed;
}
.weight-value {
  width: 24px; font-size: 14px; font-weight: 600;
  color: var(--text-primary); text-align: center; flex-shrink: 0;
}
</style>
