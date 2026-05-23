<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getChapterContent, saveChapterContent, getOutline, getVersionHistory } from '@/api/book'
import { streamContinue, streamRewrite } from '@/api/generation'

const route = useRoute()
const bookId = route.params.bookId
const structureId = Number(route.params.structureId)
const title = route.query.title || '未命名章节'
const nodeType = route.query.node_type || 'SCENE'

const loading = ref(false)
const saving = ref(false)
const saved = ref(false)
const lastSaved = ref('')

let saveTimer = null
let savedTimer = null

// ── Single scene (or chapter without children) ──
const content = ref('')
const contentStatus = ref('DRAFT')
const wordCount = ref(0)

// ── Nested scenes (when node_type=CHAPTER with children) ──
const scenes = ref([])           // { id, title, sequence, content, status }
const chapterContent = ref('')   // chapter-level body text
const expandedScenes = reactive(new Set())  // track which scenes are expanded

function countWords(text) {
  return (text || '').replace(/[\s\d\p{P}]/gu, '').length
}

const totalWords = computed(() => {
  if (scenes.value.length) {
    return countWords(chapterContent.value) + scenes.value.reduce((s, sc) => s + countWords(sc.content), 0)
  }
  return wordCount.value
})

function toggleScene(sceneId) {
  if (expandedScenes.has(sceneId)) {
    expandedScenes.delete(sceneId)
  } else {
    expandedScenes.add(sceneId)
  }
}

// ── Save helpers ──

async function saveContent(structureId, body) {
  const res = await saveChapterContent(structureId, body)
  return res && res.data ? res.data.status : 'DRAFT'
}

async function saveChapter() {
  if (saving.value) return
  saving.value = true
  saved.value = false
  try {
    contentStatus.value = await saveContent(structureId, {
      content: chapterContent.value,
      word_count: countWords(chapterContent.value),
      source: 'USER_EDITED',
      storage_type: 'FULL',
      status: contentStatus.value === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    })
    for (const sc of scenes.value) {
      sc.status = await saveContent(sc.id, {
        content: sc.content,
        word_count: countWords(sc.content),
        source: 'USER_EDITED',
        storage_type: 'FULL',
        status: sc.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
      })
    }
    saved.value = true
    lastSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    if (savedTimer) clearTimeout(savedTimer)
    savedTimer = setTimeout(() => { saved.value = false }, 2000)
  } catch (e) {
    // 保存失败不提示
  } finally {
    saving.value = false
  }
}

async function saveSingle() {
  if (saving.value) return
  saving.value = true
  saved.value = false
  try {
    contentStatus.value = await saveContent(structureId, {
      content: content.value,
      word_count: wordCount.value,
      source: 'USER_EDITED',
      storage_type: 'FULL',
      status: contentStatus.value === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    })
    saved.value = true
    lastSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    if (savedTimer) clearTimeout(savedTimer)
    savedTimer = setTimeout(() => { saved.value = false }, 2000)
  } catch (e) {
    // 保存失败不提示
  } finally {
    saving.value = false
  }
}

function doSave(status) {
  if (status === 'PUBLISHED') {
    contentStatus.value = 'PUBLISHED'
    if (scenes.value.length) {
      scenes.value.forEach(s => s.status = 'PUBLISHED')
    }
  }
  if (scenes.value.length) {
    return saveChapter()
  }
  return saveSingle()
}

function handlePublish() {
  doSave('PUBLISHED')
}

// ── AI 续写 ─────────────────────────
const generating = ref(false)
const genError = ref('')
const genTarget = ref(null) // 'chapter' | sceneId — which textarea is receiving output

function handleAiContinue(target) {
  genTarget.value = target
  genError.value = ''

  // determine context text based on target
  let ctx = ''
  if (target === 'chapter') {
    ctx = chapterContent.value
  } else if (typeof target === 'number') {
    const sc = scenes.value.find(s => s.id === target)
    ctx = sc ? sc.content : content.value
  } else {
    ctx = content.value
  }

  if (!ctx.trim()) {
    genError.value = '请先写一些正文作为前文'
    return
  }

  generating.value = true
  streamContinue(
    { bookId: bookId, context: ctx },
    {
      onChunk(chunk) {
        if (target === 'chapter') {
          chapterContent.value += chunk
        } else if (typeof target === 'number') {
          const sc = scenes.value.find(s => s.id === target)
          if (sc) sc.content += chunk
        } else {
          content.value += chunk
        }
      },
      onDone() {
        generating.value = false
        genTarget.value = null
        doSave('DRAFT')
      },
      onError(msg) {
        genError.value = msg
        generating.value = false
        genTarget.value = null
      },
    },
  )
}

function handleAiStop() {
  // reload page to abort — simple but effective
  window.location.reload()
}

// ── AI 改写 ─────────────────────────
const rewriteMode = ref('polish')
const rewriting = ref(false)
const rewriteTarget = ref(null) // same as genTarget: null | 'chapter' | sceneId
const originalText = ref('') // saved for undo

const modeOptions = [
  { value: 'polish', label: '润色' },
  { value: 'expand', label: '扩写' },
  { value: 'summarize', label: '缩写' },
]

function getRewriteContext(target) {
  if (target === 'chapter') return chapterContent.value
  if (typeof target === 'number') {
    const sc = scenes.value.find(s => s.id === target)
    return sc ? sc.content : content.value
  }
  return content.value
}

function setRewriteContent(target, text) {
  if (target === 'chapter') chapterContent.value = text
  else if (typeof target === 'number') {
    const sc = scenes.value.find(s => s.id === target)
    if (sc) sc.content = text
  } else content.value = text
}

function resetRewriteState() {
  rewriting.value = false
  rewriteTarget.value = null
  originalText.value = ''
}

function handleRewrite(target) {
  const ctx = getRewriteContext(target)
  if (!ctx.trim()) {
    genError.value = '请先写一些正文'
    return
  }

  genError.value = ''
  rewriteTarget.value = target
  originalText.value = ctx
  rewriting.value = true

  // clear current content and stream in rewritten version
  setRewriteContent(target, '')

  streamRewrite(
    { context: ctx, mode: rewriteMode.value },
    {
      onChunk(chunk) {
        if (target === 'chapter') chapterContent.value += chunk
        else if (typeof target === 'number') {
          const sc = scenes.value.find(s => s.id === target)
          if (sc) sc.content += chunk
        } else content.value += chunk
      },
      onDone() {
        rewriting.value = false
        // keep target so buttons stay visible
      },
      onError(msg) {
        genError.value = msg
        // restore original on error
        setRewriteContent(target, originalText.value)
        resetRewriteState()
      },
    },
  )
}

function handleRewriteApply() {
  resetRewriteState()
  doSave('DRAFT')
}

function handleRewriteUndo() {
  const target = rewriteTarget.value
  setRewriteContent(target, originalText.value)
  resetRewriteState()
}

// ── Focus mode ─────────────────────────
const focusMode = ref(false)

function toggleFocus() {
  focusMode.value = !focusMode.value
}

function exitFocus(e) {
  if (e.key === 'Escape' && focusMode.value) {
    focusMode.value = false
  }
}

// ── Version history ─────────────────────────
const showVersions = ref(false)
const versions = ref([])
const viewingVersion = ref(null) // version number being previewed
const versionPreview = ref('')

async function openVersions() {
  showVersions.value = true
  viewingVersion.value = null
  versionPreview.value = ''
  try {
    const res = await getVersionHistory(structureId)
    if (res?.data) versions.value = res.data
  } catch (e) { versions.value = [] }
}

function closeVersions() {
  showVersions.value = false
  viewingVersion.value = null
  versionPreview.value = ''
}

async function viewVersion(ver) {
  viewingVersion.value = ver.version_number
  versionPreview.value = ver.content || '(此版本无内容)'
}

async function restoreVersion(ver) {
  const target = scenes.value.length ? 'chapter' : null
  const text = ver.content || ''
  setRewriteContent(target, text)
  originalText.value = getRewriteContext(target)
  // keep original as undo reference
  if (target === 'chapter') chapterContent.value = text
  else content.value = text
  closeVersions()
  doSave('DRAFT')
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

// ── Load ──

async function loadContent() {
  loading.value = true
  try {
    if (nodeType === 'CHAPTER') {
      const outlineRes = await getOutline(bookId)
      if (outlineRes && outlineRes.data) {
        scenes.value = (outlineRes.data || [])
          .filter(n => n.parent_id === structureId && n.node_type === 'SCENE')
          .sort((a, b) => a.sequence - b.sequence)
          .map(n => ({
            id: n.id,
            title: n.title,
            sequence: n.sequence,
            content: '',
            status: 'DRAFT',
          }))
        for (const sc of scenes.value) {
          try {
            const res = await getChapterContent(sc.id)
            if (res && res.data) {
              sc.content = res.data.content || ''
              sc.status = res.data.status || 'DRAFT'
              if (sc.content) expandedScenes.add(sc.id)
            }
          } catch (e) { /* 新场景 */ }
        }
      }
      try {
        const res = await getChapterContent(structureId)
        if (res && res.data) {
          chapterContent.value = res.data.content || ''
          contentStatus.value = res.data.status || 'DRAFT'
        }
      } catch (e) { /* 新章节 */ }
    } else {
      try {
        const res = await getChapterContent(structureId)
        if (res && res.data) {
          content.value = res.data.content || ''
          contentStatus.value = res.data.status || 'DRAFT'
          wordCount.value = countWords(content.value)
        }
      } catch (e) { /* 新内容 */ }
    }
  } finally {
    loading.value = false
  }
}

// ── Auto-save ──

function startAutoSave() {
  saveTimer = setInterval(() => {
    const hasContent = scenes.value.length
      ? (chapterContent.value || scenes.value.some(s => s.content))
      : content.value
    if (hasContent) doSave('DRAFT')
  }, 30000)
}

onMounted(() => {
  loadContent()
  startAutoSave()
  document.addEventListener('keydown', exitFocus)
})

onUnmounted(() => {
  if (saveTimer) clearInterval(saveTimer)
  if (savedTimer) clearTimeout(savedTimer)
  document.removeEventListener('keydown', exitFocus)
})

import { onBeforeRouteLeave } from 'vue-router'
onBeforeRouteLeave((_to, _from, next) => {
  if (saveTimer) clearInterval(saveTimer)
  const hasContent = scenes.value.length
    ? (chapterContent.value || scenes.value.some(s => s.content))
    : content.value
  if (hasContent) {
    doSave('DRAFT').then(() => next())
  } else {
    next()
  }
})
</script>

<template>
  <div class="chapter-write">
    <!-- Header -->
    <div class="write-header" v-show="!focusMode">
      <div class="header-left">
        <router-link :to="`/books/${bookId}/outline`" class="back-link">&larr; 返回大纲</router-link>
        <h1 class="chapter-title">{{ title }}</h1>
        <span v-if="nodeType === 'CHAPTER'" class="type-label">章</span>
        <span v-else class="type-label scene">节</span>
      </div>
      <div class="header-right">
        <button class="btn-focus" :title="focusMode ? '退出专注 (Esc)' : '专注模式'" @click="toggleFocus">
          {{ focusMode ? '⊠' : '⊡' }}
        </button>
        <span class="word-count">{{ totalWords }} 字</span>
        <span class="status-badge" :class="contentStatus === 'PUBLISHED' ? 'published' : 'draft'">
          {{ contentStatus === 'PUBLISHED' ? '已定稿' : '草稿' }}
        </span>
        <span v-if="saved" class="saved-indicator">已保存 {{ lastSaved }}</span>
        <template v-if="!generating && !rewriting">
          <select v-model="rewriteMode" class="rewrite-select">
            <option v-for="m in modeOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
          <button class="btn-rewrite" @click="handleRewrite(null)">改写</button>
          <button class="btn-ai" @click="handleAiContinue(null)">续写</button>
        </template>
        <button v-else class="btn-ai-stop" @click="handleAiStop">停止生成</button>
        <button class="btn-versions" @click="openVersions">版本历史</button>
        <button class="btn-save" :disabled="saving" @click="doSave('DRAFT')">
          {{ saving ? '保存中...' : '保存' }}
        </button>
        <button class="btn-publish" :disabled="saving" @click="handlePublish">
          定稿
        </button>
      </div>
    </div>

    <!-- AI error / status -->
    <div v-if="genError" class="gen-error">{{ genError }}</div>
    <div v-if="generating" class="gen-status">AI 正在续写<span class="gen-dots">...</span></div>
    <div v-if="rewriting" class="gen-status">AI 正在{{ modeOptions.find(m => m.value === rewriteMode)?.label }}<span class="gen-dots">...</span></div>
    <div v-if="rewriteTarget !== null && !rewriting && !generating" class="rewrite-actions">
      <span class="rewrite-hint">改写完成，要保留吗？</span>
      <button class="btn-apply" @click="handleRewriteApply">应用改写</button>
      <button class="btn-undo" @click="handleRewriteUndo">撤销</button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading">加载中...</div>

    <!-- Single textarea (SCENE or CHAPTER without children) -->
    <div v-else-if="scenes.length === 0" class="editor-area">
      <textarea
        v-model="content"
        class="write-textarea"
        placeholder="开始书写正文..."
        @keydown.tab.prevent="
          const s = $event.target;
          const p = s.selectionStart;
          s.value = s.value.substring(0, p) + '    ' + s.value.substring(s.selectionEnd);
          s.selectionStart = s.selectionEnd = p + 4;
        "
      ></textarea>
    </div>

    <!-- CHAPTER mode with children scenes -->
    <div v-else class="editor-area chapter-mode">
      <!-- 章正文：主写作区 -->
      <div class="chapter-body-section">
        <div class="section-label">
          章正文
          <template v-if="!generating && !rewriting">
            <select v-model="rewriteMode" class="rewrite-select-sm" @click.stop>
              <option v-for="m in modeOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
            <button class="btn-ai-sm" @click.stop="handleRewrite('chapter')">改写</button>
            <button class="btn-ai-sm" @click.stop="handleAiContinue('chapter')">续写</button>
          </template>
          <span v-else-if="genTarget === 'chapter' || rewriteTarget === 'chapter'" class="gen-inline">生成中...</span>
        </div>
        <textarea
          v-model="chapterContent"
          class="write-textarea chapter-body"
          placeholder="在此书写本章正文…"
          @keydown.tab.prevent="
            const s = $event.target;
            const p = s.selectionStart;
            s.value = s.value.substring(0, p) + '    ' + s.value.substring(s.selectionEnd);
            s.selectionStart = s.selectionEnd = p + 4;
          "
        ></textarea>
      </div>

      <!-- 节列表：折叠式 -->
      <div class="scenes-list">
        <div class="section-label">本章各节</div>
        <div
          v-for="scene in scenes"
          :key="scene.id"
          class="scene-card"
          :class="{ expanded: expandedScenes.has(scene.id) }"
        >
          <div class="scene-bar" @click="toggleScene(scene.id)">
            <span class="scene-arrow">{{ expandedScenes.has(scene.id) ? '▾' : '▸' }}</span>
            <span class="scene-seq">节 {{ scene.sequence }}</span>
            <span class="scene-title">{{ scene.title }}</span>
            <span class="scene-meta">
              <template v-if="!generating && !rewriting">
                <button class="btn-ai-sm" @click.stop="handleRewrite(scene.id)">{{ modeOptions.find(m => m.value === rewriteMode)?.label }}</button>
                <button class="btn-ai-sm" @click.stop="handleAiContinue(scene.id)">续写</button>
              </template>
              <span v-else-if="genTarget === scene.id || rewriteTarget === scene.id" class="gen-inline">生成中...</span>
              <span class="scene-words">{{ countWords(scene.content) }} 字</span>
              <span class="scene-status-dot" :class="scene.status === 'PUBLISHED' ? 'pub' : 'draft'"></span>
            </span>
          </div>
          <textarea
            v-if="expandedScenes.has(scene.id)"
            v-model="scene.content"
            class="write-textarea scene-body"
            :placeholder="'书写「' + scene.title + '」…'"
            @keydown.tab.prevent="
              const s = $event.target;
              const p = s.selectionStart;
              s.value = s.value.substring(0, p) + '    ' + s.value.substring(s.selectionEnd);
              s.selectionStart = s.selectionEnd = p + 4;
            "
          ></textarea>
        </div>
      </div>
    </div>

    <!-- Version History Panel -->
    <div v-if="showVersions" class="version-overlay" @click.self="closeVersions">
      <div class="version-panel">
        <div class="version-panel-header">
          <h3>版本历史</h3>
          <button class="version-close" @click="closeVersions">&times;</button>
        </div>

        <div v-if="viewingVersion > 0" class="version-preview">
          <div class="preview-header">
            <button class="btn-back" @click="viewingVersion = null">&larr; 返回列表</button>
            <span>版本 #{{ viewingVersion }}</span>
          </div>
          <textarea readonly class="write-textarea preview-area" :value="versionPreview"></textarea>
        </div>

        <div v-else class="version-list">
          <div v-if="versions.length === 0" class="version-empty">暂无历史版本</div>
          <div
            v-for="ver in versions"
            :key="ver.version_number"
            class="version-item"
          >
            <div class="version-info">
              <span class="version-num">v{{ ver.version_number }}</span>
              <span class="version-meta">
                {{ ver.word_count || 0 }} 字
                <span :class="ver.status === 'PUBLISHED' ? 'pub' : 'draft'">
                  {{ ver.status === 'PUBLISHED' ? '已定稿' : '草稿' }}
                </span>
              </span>
              <span class="version-time">{{ formatTime(ver.created_at) }}</span>
            </div>
            <div class="version-actions">
              <button class="btn-sm btn-view" @click="viewVersion(ver)">查看</button>
              <button class="btn-sm btn-restore" @click="restoreVersion(ver)">恢复</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chapter-write {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 32px 80px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.chapter-write.focus-mode {
  max-width: none;
  padding: 0;
}
.chapter-write.focus-mode .editor-area > .write-textarea {
  min-height: 100vh;
  border-radius: 0;
  border: none;
  box-shadow: none;
}
.chapter-write.focus-mode .chapter-mode {
  min-height: 100vh;
}
.chapter-write.focus-mode .chapter-body {
  min-height: 50vh;
}

/* ── Header ── */
.write-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.chapter-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}
.type-label {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
  background: var(--bg-info);
  color: var(--color-brand);
  align-self: flex-start;
}
.type-label.scene { background: var(--bg-surface-hover); color: var(--text-muted); }
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.word-count {
  font-size: 13px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.status-badge {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 10px;
}
.status-badge.draft { background: var(--bg-surface-hover); color: var(--text-secondary); }
.status-badge.published { background: var(--bg-success-soft); color: var(--color-success); }
.saved-indicator { font-size: 12px; color: #059669; }
.btn-save {
  padding: 8px 22px;
  background: var(--color-brand);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
  font-family: inherit;
}
.btn-save:hover:not(:disabled) { background: var(--color-brand-hover); }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-publish {
  padding: 8px 22px;
  background: var(--color-success);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  font-family: inherit;
}
.btn-publish:hover:not(:disabled) { background: #047857; }
.btn-publish:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-ai {
  padding: 8px 22px;
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  font-family: inherit;
}
.btn-ai:hover { opacity: 0.85; }
.btn-ai-stop {
  padding: 8px 22px;
  background: #ef4444;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
}
.btn-ai-sm {
  padding: 2px 8px; font-size: 11px;
  background: #7c3aed; color: #fff;
  border: none; border-radius: 4px;
  cursor: pointer; font-family: inherit;
  transition: opacity 0.15s;
}
.btn-ai-sm:hover { opacity: 0.8; }
.gen-error {
  padding: 8px 16px; margin-bottom: 12px;
  background: var(--bg-error-soft); color: var(--color-danger);
  border: 1px solid var(--border-error-soft); border-radius: 6px;
  font-size: 13px;
}
.gen-status {
  padding: 8px 16px; margin-bottom: 12px;
  background: var(--bg-info); color: var(--color-brand);
  border: 1px solid var(--border-info); border-radius: 6px;
  font-size: 13px; display: flex; align-items: center; gap: 4px;
}
.gen-inline { font-size: 11px; color: #7c3aed; font-weight: 500; }
.gen-dots::after {
  content: ''; animation: ellipsis 1.2s infinite;
}
@keyframes ellipsis {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
}

/* ── Rewrite ── */
.rewrite-select {
  padding: 7px 10px; font-size: 13px;
  border: 1px solid var(--border-input); border-radius: 6px;
  background: var(--bg-surface); color: var(--text-primary);
  font-family: inherit; cursor: pointer;
}
.rewrite-select-sm {
  padding: 2px 6px; font-size: 11px;
  border: 1px solid var(--border-input); border-radius: 4px;
  background: var(--bg-surface); color: var(--text-primary);
  font-family: inherit; cursor: pointer;
}
.btn-rewrite {
  padding: 8px 22px;
  background: var(--bg-surface); color: #7c3aed;
  border: 1px solid #7c3aed; border-radius: 6px;
  font-size: 14px; font-weight: 500;
  cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.btn-rewrite:hover { background: var(--bg-info); }
.rewrite-actions {
  display: flex; align-items: center; gap: 12px; padding: 8px 16px;
  margin-bottom: 12px; background: var(--bg-success-soft); border: 1px solid var(--border-success-soft);
  border-radius: 6px; font-size: 13px;
}
.rewrite-hint { color: #059669; font-weight: 500; }
.btn-apply {
  padding: 6px 18px; background: #059669; color: #fff;
  border: none; border-radius: 6px; font-size: 13px;
  font-weight: 500; cursor: pointer; font-family: inherit;
}
.btn-apply:hover { background: #047857; }
.btn-undo {
  padding: 6px 18px; background: var(--bg-surface); color: var(--text-muted);
  border: 1px solid var(--border-color); border-radius: 6px; font-size: 13px;
  cursor: pointer; font-family: inherit;
}
.btn-undo:hover { background: var(--bg-surface-hover); }

/* ── Editor area ── */
.editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.editor-area > .write-textarea {
  flex: 1;
  min-height: calc(100vh - 160px);
}

/* ── Shared textarea ── */
.write-textarea {
  width: 100%;
  padding: 28px 32px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  font-size: 16px;
  line-height: 2;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'SimSun', 'STSong', Georgia, serif;
  resize: vertical;
  background: var(--bg-input);
  color: var(--text-primary);
  box-sizing: border-box;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.3s, color 0.3s;
}
.write-textarea:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px var(--shadow-focus);
}
.write-textarea::placeholder { color: var(--text-muted); }

/* ── Chapter mode ── */
.chapter-mode {
  gap: 28px;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
  display: flex; align-items: center; gap: 8px;
}

/* Chapter body */
.chapter-body {
  min-height: 400px;
}

/* Scene cards */
.scenes-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scene-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-surface);
  overflow: hidden;
  transition: border-color 0.15s, background 0.3s;
}
.scene-card.expanded {
  border-color: #d0c8e8;
}

.scene-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.1s;
}
.scene-bar:hover {
  background: var(--bg-surface-hover);
}

.scene-arrow {
  font-size: 12px;
  color: var(--text-muted);
  width: 14px;
  flex-shrink: 0;
}
.scene-seq {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-surface-hover);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.scene-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
}
.scene-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.scene-words {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.scene-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.scene-status-dot.draft { background: #d1d5db; }
.scene-status-dot.pub { background: #059669; }

.scene-body {
  border: none;
  border-top: 1px solid var(--border-color);
  border-radius: 0;
  padding: 20px 32px 28px;
  min-height: 200px;
  background: var(--bg-body);
}
.scene-body:focus {
  box-shadow: none;
  border-top-color: var(--border-color);
  background: var(--bg-input);
}

/* ── Loading ── */
.loading {
  text-align: center;
  padding: 80px 0;
  color: var(--text-secondary);
  font-size: 14px;
}

/* ── Version panel ── */
.version-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.25);
  display: flex; justify-content: flex-end;
}
.version-panel {
  width: 380px; max-width: 90vw; height: 100%;
  background: var(--bg-surface); box-shadow: -4px 0 20px rgba(0,0,0,0.1);
  display: flex; flex-direction: column;
}
.version-panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-color);
}
.version-panel-header h3 { font-size: 17px; font-weight: 600; margin: 0; color: var(--text-primary); }
.version-close {
  width: 32px; height: 32px; font-size: 22px;
  background: none; border: none; cursor: pointer;
  color: var(--text-muted); border-radius: 6px; display: flex;
  align-items: center; justify-content: center;
}
.version-close:hover { background: var(--bg-surface-hover); color: var(--text-primary); }

.version-list {
  flex: 1; overflow-y: auto; padding: 0 24px 24px;
  display: flex; flex-direction: column; gap: 8px;
}
.version-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border: 1px solid var(--border-color); border-radius: 8px;
  transition: border-color 0.15s;
  background: var(--bg-surface);
}
.version-item:hover { border-color: #d0c8e8; }
.version-info {
  display: flex; flex-direction: column; gap: 3px;
}
.version-num {
  font-size: 14px; font-weight: 600; color: var(--text-primary);
}
.version-meta {
  font-size: 12px; color: var(--text-muted); display: flex; gap: 8px;
}
.version-meta .pub { color: #059669; font-weight: 500; }
.version-meta .draft { color: var(--text-muted); }
.version-time { font-size: 11px; color: var(--text-muted); }
.version-actions { display: flex; gap: 6px; flex-shrink: 0; }
.version-actions .btn-sm {
  padding: 4px 12px; font-size: 12px; border-radius: 5px;
  border: 1px solid var(--border-color); cursor: pointer; font-family: inherit;
  font-weight: 500; transition: all 0.15s;
}
.btn-view { background: var(--bg-surface); color: var(--color-brand); }
.btn-view:hover { background: var(--bg-info); }
.btn-restore { background: var(--color-brand); color: #fff; border-color: var(--color-brand); }
.btn-restore:hover { background: var(--color-brand-hover); }
.version-empty { text-align: center; padding: 40px 0; color: var(--text-muted); font-size: 14px; }

.version-preview {
  flex: 1; display: flex; flex-direction: column; padding: 0 24px 24px;
}
.preview-header {
  display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
}
.btn-back {
  font-size: 13px; color: var(--color-brand); background: none;
  border: none; cursor: pointer; font-family: inherit;
}
.btn-back:hover { color: var(--color-brand-hover); }
.preview-area {
  flex: 1; min-height: 300px;
}

.btn-focus {
  font-size: 16px;
  width: 34px; height: 34px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
  line-height: 1;
}
.btn-focus:hover { background: var(--bg-surface-hover); color: var(--color-brand); }

.btn-versions {
  padding: 8px 22px;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid var(--border-input); border-radius: 6px;
  font-size: 14px; font-weight: 500;
  cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.btn-versions:hover { background: var(--bg-surface-hover); color: var(--text-primary); }

@media (max-width: 640px) {
  .chapter-write { padding: 16px; }
  .write-header { flex-direction: column; gap: 12px; }
  .write-textarea { padding: 20px; font-size: 15px; }
  .scene-bar { padding: 10px 14px; }
  .scene-body { padding: 14px 20px 22px; }
}
</style>
