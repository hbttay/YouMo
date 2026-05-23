<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getWorldSetting, saveWorldSetting, getBook } from '@/api/book'
import { randomWorldSetting } from '@/api/generation'
import { useRequest } from '@/composables/useRequest'
import { useDrafts } from '@/composables/useDrafts'
import RandomPreviewModal from '@/components/RandomPreviewModal.vue'

const route = useRoute()
const bookId = route.params.id
const { add: addDraft } = useDrafts(bookId)

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

async function loadSynopsis() {
  const res = await getBook(bookId)
  if (res) synopsis.value = res.data?.core_idea || ''
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
</script>

<template>
  <div class="world-setting">
    <div class="page-header">
      <router-link :to="`/books/${bookId}`" class="back-link">&larr; 返回详情</router-link>
    </div>

    <div class="section-header">
      <h1>世界观设定</h1>
      <div class="header-actions">
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

    <div v-if="loading" class="loading">加载中...</div>

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
</style>
