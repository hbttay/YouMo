<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getChapterContent, saveChapterContent, getOutline, getVersionHistory, createAnnotation, checkConsistency, updateOutlineNodeStatus, getBook } from '@/api/book'
import { streamContinue, streamRewrite, getStreamBuffer, getSuggestions, continuePlan, streamContinueExecute, analyzeChapter, optimizeInstructions, getWritingGuide } from '@/api/generation'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ReviewPanel from '@/components/ReviewPanel.vue'
import AnnotationSidebar from '@/components/AnnotationSidebar.vue'
import InlinePopup from '@/components/InlinePopup.vue'

const route = useRoute()
const router = useRouter()
const bookId = route.params.bookId
const structureId = ref(Number(route.params.structureId))
const title = ref(route.query.title || '未命名章节')
const nodeType = ref(route.query.node_type || 'SCENE')

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
  return countWords(content.value)
})

function toggleScene(sceneId) {
  if (expandedScenes.has(sceneId)) {
    expandedScenes.delete(sceneId)
  } else {
    expandedScenes.add(sceneId)
  }
}

// ── Save helpers ──

async function saveContent(nodeId, body) {
  const res = await saveChapterContent(nodeId, body)
  return res && res.data ? res.data.status : 'DRAFT'
}

async function saveChapter() {
  if (saving.value) return
  saving.value = true
  saved.value = false
  try {
    contentStatus.value = await saveContent(structureId.value, {
      content: chapterContent.value,
      word_count: countWords(chapterContent.value),
      source: 'USER_EDITED',
      storage_type: 'FULL',
      status: contentStatus.value === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    }) || contentStatus.value
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
    console.error('自动保存失败:', e)
  } finally {
    saving.value = false
  }
}

async function saveSingle() {
  if (saving.value) return
  saving.value = true
  saved.value = false
  try {
    contentStatus.value = await saveContent(structureId.value, {
      content: content.value,
      word_count: countWords(content.value),
      source: 'USER_EDITED',
      storage_type: 'FULL',
      status: contentStatus.value === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    }) || contentStatus.value
    saved.value = true
    lastSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    if (savedTimer) clearTimeout(savedTimer)
    savedTimer = setTimeout(() => { saved.value = false }, 2000)
  } catch (e) {
    console.error('保存失败:', e)
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
    // Also mark the outline node as COMPLETED so the progress bar updates
    if (structureId.value) {
      updateOutlineNodeStatus(bookId, structureId.value, 'COMPLETED').catch(() => {
        genError.value = '大纲状态更新失败，进度条可能未变'
      })
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
const consistencyIssues = ref([]) // flat list for display
const consistencyRaw = ref(null) // raw report with type groups
const consistencyTab = ref('all')
const consistencyChecked = ref(false) // track whether check was performed
const fixingIdx = ref(-1) // which issue is being AI-fixed
const fixSuccessMessage = ref('') // toast after fix accepted
const fixedIssueFingerprints = new Set() // track fixed issues to prevent re-appearance

const consistencyTabs = computed(() => {
  const raw = consistencyRaw.value
  if (!raw) return []
  const tabs = [{ key: 'all', label: '全部', count: consistencyIssues.value.length }]
  const c = raw.character_issues || raw.characterIssues || []
  const tl = raw.timeline_issues || raw.timelineIssues || []
  const w = raw.world_issues || raw.worldIssues || []
  const f = raw.foreshadowing_issues || raw.foreshadowingIssues || []
  const t = raw.tone_issues || raw.toneIssues || []
  if (c.length) tabs.push({ key: 'character', label: '角色', count: c.length })
  if (tl.length) tabs.push({ key: 'timeline', label: '时间线', count: tl.length })
  if (w.length) tabs.push({ key: 'world', label: '世界观', count: w.length })
  if (f.length) tabs.push({ key: 'foreshadowing', label: '伏笔', count: f.length })
  if (t.length) tabs.push({ key: 'tone', label: '文风', count: t.length })
  return tabs
})

const filteredConsistencyIssues = computed(() => {
  let list = consistencyTab.value === 'all' ? consistencyIssues.value : consistencyIssues.value.filter(i => i.type === consistencyTab.value)
  if (fixedIssueFingerprints.size > 0) {
    list = list.filter(i => !fixedIssueFingerprints.has(issueFingerprint(i)))
  }
  return list
})
const temperature = ref(1.2) // AI temperature (0.3-2.0)
const instructions = ref('') // user's continuation instructions
const showInstructions = ref(false) // expandable prompt guidance
const showConsistency = ref(true) // consistency card collapsed
const showPlanPreview = ref(true) // plan preview card collapsed
const showAnalysisPanel = ref(true) // analysis panel collapsed
const allPanelsCollapsed = ref(false)
const showAiMenu = ref(false)
const showSettings = ref(false)
const headerRef = ref(null)
const showFloatingToolbar = ref(false)
const floatingPos = ref({ top: 60, right: 24 })
let _headerObserver = null

function toggleAllPanels() {
  allPanelsCollapsed.value = !allPanelsCollapsed.value
  showInstructions.value = !allPanelsCollapsed.value && !!instructions.value
  showConsistency.value = !allPanelsCollapsed.value && consistencyChecked.value
  showPlanPreview.value = !allPanelsCollapsed.value && !!planPreview.value
  showAnalysisPanel.value = !allPanelsCollapsed.value && showAnalysis.value
}
function closeMenus(e) {
  if (showAiMenu.value && !e.target.closest('.ai-dropdown')) showAiMenu.value = false
  if (showSettings.value && !e.target.closest('.settings-dropdown')) showSettings.value = false
}
const optimizingInstructions = ref(false) // AI optimizing instructions
let abortController = null
const recovering = ref(false) // stream buffer recovery state
const recoveredText = ref('')

// ── Plan-then-Execute mode ──
const planMode = ref(false)
const planGenerating = ref(false)
const planPreview = ref(null) // { plan, characters, events, emotion_arc }
const planEdited = ref('')

function getContextForTarget(target) {
  if (target === 'chapter') return chapterContent.value
  if (typeof target === 'number') {
    const sc = scenes.value.find(s => s.id === target)
    return sc ? sc.content : content.value
  }
  return content.value
}

function isGenericTitle(t) {
  if (!t || t.length < 3) return true
  const generic = [
    /^第[一二三四五六七八九十百千\d]+[章节部篇].*$/,
    /^chapter\s*\d+/i,
    /^\d+$/,
  ]
  return generic.some(p => p.test(t))
}

async function assembleEmptyContext() {
  const parts = []
  try {
    const bookRes = await getBook(bookId)
    if (bookRes?.data) {
      const book = bookRes.data
      if (book.one_sentence) parts.push('故事梗概：' + book.one_sentence)
      if (book.core_idea) parts.push('核心创意：' + book.core_idea)
      if (book.theme) parts.push('题材类型：' + book.theme)
    }
  } catch (e) { /* offline fallback */ }

  if (title.value && title.value !== '未命名章节' && !isGenericTitle(title.value)) {
    parts.push('本章标题：「' + title.value + '」')
  }

  // Include previous chapter tail for continuity
  if (prevNode.value && structureId.value) {
    try {
      const prevRes = await getChapterContent(prevNode.value.id)
      if (prevRes?.data?.content) {
        const prevText = prevRes.data.content
        const lastPart = prevText.length > 500
          ? '…' + prevText.slice(-500)
          : prevText
        parts.push('前一章末尾：' + lastPart)
      }
    } catch (e) { /* ignore */ }
  }

  if (instructions.value && instructions.value.trim()) {
    parts.push('创作要求：' + instructions.value.trim())
  }

  return parts.join('\n\n')
}

function appendChunkToTarget(target, chunk) {
  if (target === 'chapter') {
    chapterContent.value += chunk
  } else if (typeof target === 'number') {
    const sc = scenes.value.find(s => s.id === target)
    if (sc) sc.content += chunk
  } else {
    content.value += chunk
  }
}

function handleConsistencyResult(result) {
  if (!result) return
  // Try snake_case first (Jackson global config), then camelCase
  const keys = ['character_issues', 'timeline_issues', 'world_issues', 'foreshadowing_issues', 'tone_issues']
  const altKeys = ['characterIssues', 'timelineIssues', 'worldIssues', 'foreshadowingIssues', 'toneIssues']
  const hasSnake = result.character_issues !== undefined || result.timeline_issues !== undefined
  const useKeys = hasSnake ? keys : altKeys

  if (result[useKeys[0]] !== undefined) {
    consistencyRaw.value = result
    const all = []
    for (const key of useKeys) {
      if (result[key]) all.push(...result[key])
    }
    // Sort: high severity first
    const order = { high: 0, medium: 1, low: 2 }
    all.sort((a, b) => (order[a.severity] ?? 3) - (order[b.severity] ?? 3))
    consistencyIssues.value = all
  } else {
    consistencyIssues.value = result?.issues || []
  }
  consistencyTab.value = 'all'
  consistencyChecked.value = true
}

// Extract candidate keywords from issue (entity + description nouns)
function extractKeywords(issue) {
  const words = []
  const entity = (issue.entity || '').trim()
  if (entity) words.push(entity)
  // Also try without common prefixes/suffixes like "角色"、"事件"、"伏笔"
  if (entity.length > 2) {
    const stripped = entity.replace(/^[的]/, '').replace(/[的了]$/, '')
    if (stripped !== entity) words.push(stripped)
  }
  // Extract quoted or bracketed terms from description (「」『』""'')
  const desc = (issue.description || '').trim()
  const quoted = desc.match(/[「「『』]([^」」』』]+)[」」』』]/g)
    || desc.match(/[""]([^""]+)[""]/g)
    || desc.match(/'([^']+)'/g)
  if (quoted) quoted.forEach(q => words.push(q.replace(/[「「」」『』』』""'']/g, '')))
  // Split description by separators, strip quotes, keep 2+ char segments
  desc.split(/[，。、；：\s\[\]]+/).forEach(seg => {
    const clean = seg.replace(/[''""「「」」『』』』]/g, '').trim()
    if (clean.length >= 2 && !words.includes(clean)) words.push(clean)
  })
  return words
}

// Find the sentence containing a match position (delimited by 。！？\n)
function findSentenceBounds(text, pos) {
  const delims = new Set(['。', '！', '？', '\n'])
  let start = pos
  while (start > 0 && !delims.has(text[start - 1])) start--
  let end = pos
  while (end < text.length && !delims.has(text[end])) end++
  if (end < text.length && text[end] !== '\n') end++ // include 。！？
  return { start, end }
}

function jumpToIssue(issue) {
  const keywords = extractKeywords(issue)
  if (!keywords.length) {
    genError.value = '该问题未关联具体正文关键词，无法定位'
    return
  }

  const highlight = (ta, start, end) => {
    ta.focus()
    ta.setSelectionRange(start, end)
    ta.scrollTop = Math.max(0, (ta.scrollHeight / ta.value.length) * start - 80)
    // Flash glow effect
    ta.classList.add('flash-highlight')
    setTimeout(() => ta.classList.remove('flash-highlight'), 1800)
  }

  const tas = document.querySelectorAll('.write-textarea')
  for (const ta of tas) {
    for (const kw of keywords) {
      const idx = ta.value.indexOf(kw)
      if (idx !== -1) {
        const { start, end } = findSentenceBounds(ta.value, idx)
        highlight(ta, start, end)
        return
      }
    }
  }

  // Fallback: try partial match (first 2 chars of longer keywords)
  for (const ta of tas) {
    for (const kw of keywords) {
      if (kw.length >= 3) {
        const partial = kw.substring(0, 2)
        const idx = ta.value.indexOf(partial)
        if (idx !== -1) {
          const { start, end } = findSentenceBounds(ta.value, idx)
          highlight(ta, start, end)
          return
        }
      }
    }
  }

  genError.value = `无法定位"${keywords[0]}"，该实体未出现在当前章节正文中`
  setTimeout(() => { if (genError.value.includes(keywords[0])) genError.value = '' }, 3000)
}

function issueFingerprint(issue) {
  return `${issue.entity || ''}::${issue.description || ''}::${issue.type || ''}`.replace(/\s+/g, ' ').trim()
}

function dismissIssue(idx) {
  const issue = consistencyIssues.value[idx]
  if (issue) fixedIssueFingerprints.add(issueFingerprint(issue))
  consistencyIssues.value.splice(idx, 1)
  if (consistencyIssues.value.length === 0) consistencyChecked.value = false
}

async function fixConsistencyIssue(issue, idx) {
  const keywords = extractKeywords(issue)
  if (!keywords.length) { genError.value = '该问题未关联正文关键词，无法AI修正'; return }
  if (fixingIdx.value !== -1) return

  // find textarea and matching keyword
  let ta = null; let foundKw = ''
  const tas = document.querySelectorAll('.write-textarea')
  for (const t of tas) {
    for (const kw of keywords) {
      if (t.value.includes(kw)) { ta = t; foundKw = kw; break }
    }
    if (ta) break
  }
  // Fallback: partial match first 2 chars
  if (!ta) {
    for (const t of tas) {
      for (const kw of keywords) {
        if (kw.length >= 3) {
          const partial = kw.substring(0, 2)
          if (t.value.includes(partial)) { ta = t; foundKw = partial; break }
        }
      }
      if (ta) break
    }
  }
  if (!ta) { genError.value = `无法定位"${keywords[0]}"，该关键词不在当前章节中`; return }

  const text = ta.value
  const pos = text.indexOf(foundKw)
  if (pos === -1) return

  // extract surrounding context window (~500 chars around keyword)
  let start = text.lastIndexOf('\n\n', pos)
  if (start === -1) {
    // no double newline — use single newline or sentence boundary
    start = text.lastIndexOf('\n', pos)
    if (start === -1 || pos - start > 500) start = Math.max(0, pos - 500)
    else start = start + 1
  } else {
    start = start + 2
  }
  let end = text.indexOf('\n\n', pos)
  if (end === -1) {
    end = text.indexOf('\n', pos + 1)
    if (end === -1 || end - pos > 500) end = Math.min(text.length, pos + 500)
  }
  if (end - start > 3000) { end = Math.min(text.length, start + 3000) }
  const paragraph = text.substring(start, end)

  // set textarea selection so acceptInlineResult knows where to apply
  ta.focus()
  ta.setSelectionRange(start, end)

  fixingIdx.value = idx
  try {
    inlineSelectedText.value = paragraph
    inlineSource.value = ta
    inlineMode.value = 'fix-consistency'
    inlineLoading.value = true
    inlineResult.value = ''
    inlineAbortController = new AbortController()

    const { streamRewrite } = await import('@/api/generation')
    await streamRewrite({
      context: paragraph,
      mode: 'fix-consistency',
      instructions: `实体：${issue.entity}\n问题：${issue.description}\n类型：${issue.type}\n严重程度：${issue.severity}`,
      temperature: 0.3,
      maxTokens: paragraph.length + 500,
      signal: inlineAbortController.signal,
    }, {
      onChunk(chunk) { inlineResult.value += chunk },
      onDone() {
        inlineLoading.value = false
        // position popup near the paragraph, clamped within viewport
        const rect = ta.getBoundingClientRect()
        const lineHeight = parseInt(getComputedStyle(ta).lineHeight) || 20
        const linesBefore = text.substring(0, start).split('\n').length - 1
        const visualTop = rect.top + linesBefore * lineHeight - ta.scrollTop
        inlinePos.value = { top: Math.max(10, Math.min(window.innerHeight - 200, visualTop)), left: rect.left + 20 }
        inlinePopup.value = true
      },
      onError(msg) {
        if (msg !== 'AbortError') genError.value = msg
        inlineLoading.value = false
      },
    })
  } catch (e) {
    // fixingIdx will be reset by acceptInlineResult or closeInlinePopup
  }
}

async function handleAiContinue(target) {
  genTarget.value = target
  genError.value = ''
  resetInlineState()

  let ctx = getContextForTarget(target)
  if (!ctx.trim()) {
    // Empty textarea — assemble writing brief from book metadata + chapter title
    ctx = await assembleEmptyContext()
    if (!ctx.trim()) {
      genError.value = '请在续写指令中描述你想写的内容，或先在正文中写一些开头'
      return
    }
  }

  // Plan mode: generate plan first, then let user approve
  if (planMode.value) {
    planGenerating.value = true
    try {
      const result = await continuePlan({ bookId, context: ctx, structureId: structureId.value })
      planPreview.value = result
      planEdited.value = result.plan || ''
    } catch (e) {
      genError.value = e.message || '生成计划失败'
      planPreview.value = null
    } finally {
      planGenerating.value = false
    }
    return
  }

  // Normal mode: stream directly
  doStreamContinue(target, ctx)
}

function doStreamContinue(target, ctx, plan) {
  generating.value = true
  abortController = new AbortController()
  const commonParams = {
    bookId,
    context: ctx,
    chapterTitle: title.value !== '未命名章节' ? title.value : undefined,
    temperature: temperature.value,
    instructions: instructions.value || undefined,
    signal: abortController.signal,
    structureId: structureId.value,
  }

  const callbacks = {
    onChunk(chunk) { appendChunkToTarget(target, chunk) },
    onDone() {
      generating.value = false
      genTarget.value = null
      planPreview.value = null
      doSave('DRAFT')
    },
    onConsistency: handleConsistencyResult,
    onError(msg) {
      if (msg === 'AbortError') {
        consistencyIssues.value = []
      } else {
        genError.value = msg
      }
      generating.value = false
      genTarget.value = null
      planPreview.value = null
    },
  }

  if (plan) {
    streamContinueExecute({ ...commonParams, plan }, callbacks)
  } else {
    streamContinue(commonParams, callbacks)
  }
}

function handlePlanApprove() {
  const target = genTarget.value
  const ctx = getContextForTarget(target)
  const plan = planEdited.value || planPreview.value?.plan || ''
  if (!plan.trim()) {
    genError.value = '计划不能为空'
    return
  }
  planPreview.value = null
  doStreamContinue(target, ctx, plan)
}

function handlePlanEdit() {
  // User is editing planEdited via textarea — just keep the card open
}

function handlePlanRegenerate() {
  planPreview.value = null
  planEdited.value = ''
  handleAiContinue(genTarget.value)
}

function handlePlanCancel() {
  planPreview.value = null
  planEdited.value = ''
  genTarget.value = null
}

// ── Instruction optimization ──
async function handleOptimizeInstructions() {
  if (!instructions.value.trim()) return
  optimizingInstructions.value = true
  try {
    const ctx = getContextForTarget(genTarget.value)
    const result = await optimizeInstructions({
      bookId,
      draft: instructions.value,
      context: ctx,
    })
    if (result?.optimized) {
      instructions.value = result.optimized
    }
  } catch (e) {
    // optimization failed silently, keep original
  } finally {
    optimizingInstructions.value = false
  }
}

function handleAiStop() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
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

  resetInlineState()
  genError.value = ''
  rewriteTarget.value = target
  originalText.value = ctx
  rewriting.value = true
  abortController = new AbortController()

  // clear current content and stream in rewritten version
  setRewriteContent(target, '')

  streamRewrite(
    { context: ctx, mode: rewriteMode.value, signal: abortController.signal },
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

// ── Author review panel ─────────────────────────
const reviewing = ref(false)
const reviewPanelOpen = ref(false)
const suggestions = ref([])

async function handleReview() {
  if (reviewing.value) return
  reviewing.value = true
  genError.value = ''
  resetInlineState()
  try {
    const ctx = scenes.value.length ? chapterContent.value : content.value
    if (!ctx.trim()) { genError.value = '请先写一些正文'; reviewing.value = false; return }
    const result = await getSuggestions({ bookId, context: ctx, structureId: structureId.value })
    suggestions.value = (result.suggestions || []).map(s => ({ ...s, accepted: false, rejected: false }))
    reviewPanelOpen.value = true
  } catch (e) {
    genError.value = e.message || '审改失败'
  } finally {
    reviewing.value = false
  }
}

function handleReviewAccept() {
  const target = scenes.value.length ? 'chapter' : null
  let text = getRewriteContext(target)
  // Apply accepted suggestions in reverse order (to preserve indices)
  for (let i = suggestions.value.length - 1; i >= 0; i--) {
    const s = suggestions.value[i]
    if (s.accepted) {
      text = text.replaceAll(s.original, s.suggested)
    }
  }
  setRewriteContent(target, text)
  reviewPanelOpen.value = false
  suggestions.value = []
  doSave('DRAFT')
}

function handleReviewRejectAll() {
  reviewPanelOpen.value = false
  suggestions.value = []
}

// ── Inline AI popup ─────────────────────────
const inlineAiEnabled = ref(localStorage.getItem('inline-ai-enabled') !== 'false')
const inlinePopup = ref(false)
const inlinePos = ref({ top: 0, left: 0 })
const inlineSelectedText = ref('')
const inlineMode = ref('')
const inlineResult = ref('')
const inlineLoading = ref(false)
const inlineSource = ref(null)
let inlineAbortController = null

// Floating toolbar drag
let _floatDragging = false
let _floatDragOff = { x: 0, y: 0 }
function onFloatDragStart(e) {
  if (e.target.closest('button') || e.target.closest('select') || e.target.closest('.dropdown-menu')) return
  _floatDragging = true
  _floatDragOff = { x: e.clientX + floatingPos.value.right, y: e.clientY - floatingPos.value.top }
  document.addEventListener('mousemove', onFloatDragMove)
  document.addEventListener('mouseup', onFloatDragEnd)
}
function onFloatDragMove(e) {
  if (!_floatDragging) return
  floatingPos.value = {
    top: Math.max(0, e.clientY - _floatDragOff.y),
    right: Math.max(0, _floatDragOff.x - e.clientX),
  }
}
function onFloatDragEnd() {
  _floatDragging = false
  document.removeEventListener('mousemove', onFloatDragMove)
  document.removeEventListener('mouseup', onFloatDragEnd)
}

const inlineModes = [
  { value: 'fix', label: '纠错', icon: '✏️' },
  { value: 'polish', label: '润色', icon: '✨' },
  { value: 'expand', label: '扩写', icon: '📖' },
  { value: 'summarize', label: '缩写', icon: '📝' },
  { value: 'annotate', label: '批注', icon: '💬' },
  { value: 'fix-consistency', label: '一致性修正', icon: '🔧', _internal: true },
]

function toggleInlineAi() {
  inlineAiEnabled.value = !inlineAiEnabled.value
  localStorage.setItem('inline-ai-enabled', inlineAiEnabled.value)
  if (!inlineAiEnabled.value) closeInlinePopup()
}

function onTextareaMouseup(e) {
  // Self-heal: if loading flag is stuck but popup is gone, reset
  if (inlineLoading.value && !inlinePopup.value) { resetInlineState() }
  if (!inlineAiEnabled.value || inlineLoading.value || generating.value || rewriting.value) return
  const ta = e.target
  if (ta.tagName !== 'TEXTAREA' || !ta.classList.contains('write-textarea')) return
  const sel = ta.value.substring(ta.selectionStart, ta.selectionEnd).trim()
  if (sel.length < 10) { inlinePopup.value = false; return }
  inlineSelectedText.value = sel
  inlineSource.value = ta
  const rect = ta.getBoundingClientRect()
  inlinePos.value = {
    top: Math.max(e.clientY - rect.top - 44, 0),
    left: Math.min(e.clientX - rect.left, rect.width - 220),
  }
  inlinePopup.value = true
  inlineResult.value = ''
  inlineMode.value = ''
}

function resetInlineState() {
  try { if (inlineAbortController) { inlineAbortController.abort(); inlineAbortController = null } } catch {}
  inlineLoading.value = false
  inlinePopup.value = false
  inlineResult.value = ''
  inlineMode.value = ''
  fixingIdx.value = -1
  annotateComment.value = ''
}

function closeInlinePopup() {
  resetInlineState()
}

async function handleInlineAction(mode) {
  if (mode === 'annotate') { handleInlineAnnotate(); return }
  inlineMode.value = mode
  inlineLoading.value = true
  inlineResult.value = ''
  inlineAbortController = new AbortController()
  streamRewrite(
    { context: inlineSelectedText.value, mode, temperature: 0.8, maxTokens: 1200, signal: inlineAbortController.signal },
    {
      onChunk(chunk) { inlineResult.value += chunk },
      onDone() { inlineLoading.value = false },
      onError(msg) {
        if (msg !== 'AbortError') genError.value = msg
        inlineLoading.value = false
        closeInlinePopup()
      },
    },
  )
}

function acceptInlineResult() {
  const ta = inlineSource.value
  if (!ta || !inlineResult.value) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  ta.value = ta.value.substring(0, start) + inlineResult.value + ta.value.substring(end)
  ta.dispatchEvent(new Event('input', { bubbles: true }))
  ta.selectionStart = ta.selectionEnd = start + inlineResult.value.length
  // if this came from consistency fix, remove the fixed issue
  if (fixingIdx.value >= 0) {
    const fixed = consistencyIssues.value[fixingIdx.value]
    if (fixed) {
      fixedIssueFingerprints.add(issueFingerprint(fixed))
      fixSuccessMessage.value = `已修正「${fixed.entity || fixed.description?.slice(0, 20) || '问题'}」`
      setTimeout(() => { fixSuccessMessage.value = '' }, 2500)
    }
    consistencyIssues.value.splice(fixingIdx.value, 1)
    fixingIdx.value = -1
    if (consistencyIssues.value.length === 0) consistencyChecked.value = false
  }
  closeInlinePopup()
  doSave('DRAFT')
}

// ── Focus mode ─────────────────────────
const focusMode = ref(false)

function toggleFocus() {
  focusMode.value = !focusMode.value
  if (focusMode.value) showFloatingToolbar.value = false
}

watch(focusMode, (v) => {
  if (v) showFloatingToolbar.value = false
})

function exitFocus(e) {
  if (e.key === 'Escape') {
    if (inlinePopup.value) { closeInlinePopup(); return }
    if (focusMode.value) { focusMode.value = false; return }
  }
}

// ── Chapter analysis ─────────────────────────
const checkingConsistency = ref(false)
const consistencyCheckProgress = ref(0)
const consistencyCheckSteps = ['角色一致性', '时间线', '世界观', '伏笔', '文风']
let _checkProgressTimer = null
const analyzing = ref(false)
const analysisResult = ref(null)
const showAnalysis = ref(false)
const guideLoading = ref(false)
const guideResult = ref(null)
const showGuide = ref(true)

// ── Annotations ──
const annotationSidebarOpen = ref(false)
const annotationCount = ref(0)
const annotateComment = ref('')
const annotateCategory = ref('suggestion')
const annotateSeverity = ref('INFO')
const annotating = ref(false)

function parseJsonField(val) {
  if (!val) return null
  if (typeof val === 'object') return val
  try { return JSON.parse(val) } catch { return null }
}

async function handleAnalyze() {
  if (analyzing.value) return
  const ctx = scenes.value.length ? chapterContent.value : content.value
  if (!ctx.trim()) { genError.value = '请先写一些正文再分析'; return }
  analyzing.value = true
  genError.value = ''
  try {
    const result = await analyzeChapter(structureId.value)
    // Parse JSONB string fields into objects
    for (const key of ['core_events', 'appearing_characters', 'character_state_changes', 'new_foreshadowings', 'recycled_foreshadowings', 'emotion_curve_point', 'key_scenes', 'world_elements']) {
      if (result[key]) result[key] = parseJsonField(result[key])
    }
    analysisResult.value = result
    showAnalysis.value = true
  } catch (e) {
    genError.value = e.message || '分析失败'
  } finally {
    analyzing.value = false
  }
}

// ── Writing guide ────────────────────────
async function handleWritingGuide() {
  if (guideLoading.value) return
  const ctx = scenes.value.length ? chapterContent.value : content.value
  if (!ctx.trim()) { genError.value = '请先写一些正文'; return }
  guideLoading.value = true
  genError.value = ''
  try {
    const result = await getWritingGuide({ bookId, context: ctx, structureId: structureId.value })
    guideResult.value = result
    showGuide.value = true
  } catch (e) {
    genError.value = e.message || '写作指导失败'
  } finally {
    guideLoading.value = false
  }
}

// ── Manual consistency check ─────────────
async function handleCheckConsistency() {
  if (checkingConsistency.value) return
  if (!structureId.value) { genError.value = '未找到章节ID'; return }
  checkingConsistency.value = true
  consistencyCheckProgress.value = 0
  genError.value = ''
  // simulated progress while waiting for parallel checks
  _checkProgressTimer = setInterval(() => {
    if (consistencyCheckProgress.value < 4) consistencyCheckProgress.value++
  }, 2500)
  try {
    const res = await checkConsistency(structureId.value)
    clearInterval(_checkProgressTimer)
    consistencyCheckProgress.value = 5
    if (res?.data) {
      handleConsistencyResult(res.data)
    } else {
      genError.value = res?.message || '一致性检查返回空结果'
    }
  } catch (e) {
    clearInterval(_checkProgressTimer)
    const msg = e?.response?.data?.message || e.message || '一致性检查失败'
    genError.value = msg
  } finally {
    checkingConsistency.value = false
  }
}

// ── Annotations handlers ─────────────────
function handleInlineAnnotate() {
  inlineMode.value = 'annotate'
  inlineResult.value = ''
  annotateComment.value = ''
  annotateCategory.value = 'suggestion'
  annotateSeverity.value = 'INFO'
}

async function submitAnnotation() {
  if (!annotateComment.value.trim() || !inlineSelectedText.value) return
  annotating.value = true
  try {
    const ta = inlineSource.value
    const before = ta.value.substring(0, ta.selectionStart)
    const after = ta.value.substring(ta.selectionEnd)
    await createAnnotation(structureId.value, {
      annotation_type: 'MANUAL',
      status: 'OPEN',
      char_offset_start: ta.selectionStart,
      char_offset_end: ta.selectionEnd,
      anchor_text: inlineSelectedText.value,
      context_before: before.length > 100 ? before.slice(-100) : before,
      context_after: after.length > 100 ? after.slice(0, 100) : after,
      comment: annotateComment.value,
      category: annotateCategory.value,
      severity: annotateSeverity.value,
    })
    closeInlinePopup()
    annotationCount.value++
  } catch (e) {
    genError.value = e.message || '添加批注失败'
  } finally {
    annotating.value = false
  }
}

function handleJumpToAnnotation(annotation) {
  annotationSidebarOpen.value = false
  const ta = document.querySelector('.write-textarea:focus') || document.querySelector('.write-textarea')
  if (!ta) return
  ta.focus()
  ta.setSelectionRange(annotation.char_offset_start, annotation.char_offset_end)
  // Scroll to the selected text
  const textBefore = ta.value.substring(0, annotation.char_offset_start)
  const lineHeight = parseInt(getComputedStyle(ta).lineHeight) || 32
  const linesBefore = textBefore.split('\n').length - 1
  ta.scrollTop = Math.max(0, linesBefore * lineHeight - ta.clientHeight / 2)
}

// ── Version history ─────────────────────────
const showVersions = ref(false)
const versions = ref([])
const viewingVersion = ref(null) // version number being previewed
const versionPreview = ref('')
const compareA = ref(null) // first version for diff
const compareB = ref(null) // second version for diff
const diffLines = ref([]) // [{ type: 'same'|'added'|'removed', text: '...' }]

async function openVersions() {
  showVersions.value = true
  viewingVersion.value = null
  versionPreview.value = ''
  try {
    const res = await getVersionHistory(structureId.value)
    if (res?.data) versions.value = res.data
  } catch (e) { versions.value = [] }
}

function closeVersions() {
  showVersions.value = false
  viewingVersion.value = null
  versionPreview.value = ''
  compareA.value = null
  compareB.value = null
  diffLines.value = []
}

function selectCompare(ver) {
  if (!compareA.value) {
    compareA.value = ver
  } else if (!compareB.value && compareA.value.id !== ver.id) {
    compareB.value = ver
  } else {
    compareA.value = ver
    compareB.value = null
  }
}

function computeDiff() {
  if (!compareA.value || !compareB.value) return
  const a = (compareA.value.content || '').split('\n')
  const b = (compareB.value.content || '').split('\n')
  const m = a.length, n = b.length

  // LCS table — use Uint16Array rows for memory efficiency
  const dp = new Array(m + 1)
  for (let i = 0; i <= m; i++) {
    dp[i] = new Uint16Array(n + 1)
  }
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }

  // Backtrack
  const result = []
  let i = m, j = n
  const rev = []
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i - 1] === b[j - 1]) {
      rev.push({ type: 'same', text: a[i - 1] })
      i--; j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      rev.push({ type: 'added', text: b[j - 1] })
      j--
    } else {
      rev.push({ type: 'removed', text: a[i - 1] })
      i--
    }
  }
  for (let k = rev.length - 1; k >= 0; k--) result.push(rev[k])
  diffLines.value = result
}

async function viewVersion(ver) {
  viewingVersion.value = ver.version_number
  versionPreview.value = ver.content || '(此版本无内容)'
}

async function restoreVersion(ver) {
  const target = scenes.value.length ? 'chapter' : null
  const text = ver.content || ''
  // Save current content as undo reference BEFORE overwriting
  originalText.value = getRewriteContext(target)
  setRewriteContent(target, text)
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

// ── Chapter navigation ──
const navNodes = ref([]) // [{ id, title, node_type }] — flat ordered list of writable nodes

function buildNavList(outlineData) {
  const map = {}
  const roots = []
  for (const n of outlineData) {
    map[n.id] = { ...n, children: [] }
  }
  for (const n of outlineData) {
    if (n.parent_id && map[n.parent_id]) {
      map[n.parent_id].children.push(map[n.id])
    } else {
      roots.push(map[n.id])
    }
  }
  // Sort by sequence
  const sort = arr => { arr.sort((a, b) => a.sequence - b.sequence); arr.forEach(n => n.children && sort(n.children)) }
  sort(roots)

  // Flatten to writable nodes only (CHAPTER, SCENE), depth-first
  const result = []
  const walk = node => {
    if (node.node_type === 'CHAPTER' || node.node_type === 'SCENE') {
      result.push({ id: node.id, title: node.title, node_type: node.node_type })
    }
    if (node.children) node.children.forEach(walk)
  }
  roots.forEach(walk)
  return result
}

const navIndex = computed(() => navNodes.value.findIndex(n => n.id === structureId.value))
const prevNode = computed(() => navIndex.value > 0 ? navNodes.value[navIndex.value - 1] : null)
const nextNode = computed(() => navIndex.value < navNodes.value.length - 1 ? navNodes.value[navIndex.value + 1] : null)

function navigateTo(node) {
  let url = `/books/${bookId}/write/${node.id}?title=${encodeURIComponent(node.title)}&node_type=${node.node_type}`
  router.push(url)
}

// ── Load ──

async function loadContent() {
  loading.value = true
  try {
    // Load outline for nav + scene discovery
    let outlineData = []
    try {
      const outlineRes = await getOutline(bookId)
      if (outlineRes && outlineRes.data) {
        outlineData = outlineRes.data
        navNodes.value = buildNavList(outlineData)
      }
    } catch (e) { genError.value = '大纲加载失败，导航和章节列表可能不完整' }

    if (nodeType.value === 'CHAPTER') {
      scenes.value = outlineData
        .filter(n => n.parent_id === structureId.value && n.node_type === 'SCENE')
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
      try {
        const res = await getChapterContent(structureId.value)
        if (res && res.data) {
          chapterContent.value = res.data.content || ''
          contentStatus.value = res.data.status || 'DRAFT'
        }
      } catch (e) { /* 新章节 */ }
      // 没有子场景时，把章正文映射到 content，让单 textarea 显示
      if (scenes.value.length === 0) {
        content.value = chapterContent.value
        wordCount.value = countWords(content.value)
      }
    } else {
      try {
        const res = await getChapterContent(structureId.value)
        if (res && res.data) {
          content.value = res.data.content || ''
          contentStatus.value = res.data.status || 'DRAFT'
          wordCount.value = countWords(content.value)
        }
      } catch (e) { /* 新内容 */ }
    }
    // Check for uncompleted stream buffer
    try {
      const { buffer } = await getStreamBuffer(structureId.value)
      if (buffer) {
        recovering.value = true
        recoveredText.value = buffer
      }
    } catch (e) { /* stream buffer not available, ignore */ }
  } finally {
    loading.value = false
  }
}

function applyRecovery() {
  const target = scenes.value.length ? 'chapter' : null
  setRewriteContent(target, recoveredText.value)
  recovering.value = false
  recoveredText.value = ''
  doSave('DRAFT')
}

function discardRecovery() {
  recovering.value = false
  recoveredText.value = ''
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

function onKeyNav(e) {
  if (e.ctrlKey && e.key === 'ArrowLeft') {
    e.preventDefault()
    if (prevNode.value) navigateTo(prevNode.value)
  } else if (e.ctrlKey && e.key === 'ArrowRight') {
    e.preventDefault()
    if (nextNode.value) navigateTo(nextNode.value)
  }
}

function handleTabIndent(e) {
  const s = e.target
  const p = s.selectionStart
  s.value = s.value.substring(0, p) + '    ' + s.value.substring(s.selectionEnd)
  s.selectionStart = s.selectionEnd = p + 4
  s.dispatchEvent(new Event('input', { bubbles: true }))
}

onMounted(() => {
  loadContent()
  startAutoSave()
  document.addEventListener('keydown', exitFocus)

watch(() => route.params.structureId, (newId) => {
  if (newId) {
    structureId.value = Number(newId)
    title.value = route.query.title || '未命名章节'
    nodeType.value = route.query.node_type || 'SCENE'
    loadContent()
  }
})
  document.addEventListener('keydown', onKeyNav)
  document.addEventListener('click', closeMenus)
  // Floating toolbar: show when header scrolls out of view
  _headerObserver = new IntersectionObserver(([e]) => {
    showFloatingToolbar.value = !e.isIntersecting && !focusMode.value
  }, { threshold: 0 })
  if (headerRef.value) _headerObserver.observe(headerRef.value)
})

onUnmounted(() => {
  if (saveTimer) clearInterval(saveTimer)
  if (savedTimer) clearTimeout(savedTimer)
  if (abortController) abortController.abort()
  if (inlineAbortController) inlineAbortController.abort()
  if (_headerObserver) _headerObserver.disconnect()
  document.removeEventListener('keydown', exitFocus)
  document.removeEventListener('keydown', onKeyNav)
  document.removeEventListener('click', closeMenus)
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
  <div class="chapter-write" :class="{ 'focus-mode': focusMode }">
    <!-- Header -->
    <div ref="headerRef" class="write-header" v-show="!focusMode">
      <div class="header-left">
        <router-link :to="`/books/${bookId}/outline`" class="back-link">&larr; 返回大纲</router-link>
        <div class="nav-btns">
          <button class="nav-btn" :disabled="!prevNode" :title="prevNode ? '上一章 Ctrl+←' : ''" @click="navigateTo(prevNode)">◂</button>
          <span class="nav-info" v-if="navNodes.length">{{ navIndex + 1 }}/{{ navNodes.length }}</span>
          <button class="nav-btn" :disabled="!nextNode" :title="nextNode ? '下一章 Ctrl+→' : ''" @click="navigateTo(nextNode)">▸</button>
        </div>
        <h1 class="chapter-title">{{ title }}</h1>
        <span v-if="nodeType === 'CHAPTER'" class="type-label">章</span>
        <span v-else class="type-label scene">节</span>
      </div>
      <div class="header-right">
        <button class="btn-focus btn-inline-toggle" :class="{ active: inlineAiEnabled }" :title="inlineAiEnabled ? '内联AI已开启 — 选中正文即可弹出' : '内联AI已关闭 — 点击开启' " @click="toggleInlineAi">
          <span class="inline-toggle-icon">{{ inlineAiEnabled ? '⚡' : '🔌' }}</span>
        </button>
        <button class="btn-focus btn-collapse-all" :title="allPanelsCollapsed ? '展开全部面板' : '折叠全部面板'" @click="toggleAllPanels">
          {{ allPanelsCollapsed ? '展开' : '折叠' }}
        </button>
        <button class="btn-focus btn-focus-mode" :title="focusMode ? '退出专注 (Esc)' : '专注模式'" @click="toggleFocus">
          {{ focusMode ? '退出' : '专注' }}
        </button>
        <span class="word-count">{{ totalWords }} 字</span>
        <span class="status-badge" :class="contentStatus === 'PUBLISHED' ? 'published' : 'draft'">
          {{ contentStatus === 'PUBLISHED' ? '已定稿' : '草稿' }}
        </span>
        <span v-if="saved" class="saved-indicator">已保存 {{ lastSaved }}</span>
        <template v-if="!generating && !rewriting">
          <!-- Settings dropdown -->
          <div class="settings-dropdown">
            <button class="btn-focus" title="写作设置" @click="showSettings = !showSettings">⚙</button>
            <div class="dropdown-menu" v-if="showSettings" @click.stop>
              <label class="dropdown-item plan-toggle" title="Plan模式：先生成写作计划，审批后再生成正文">
                <input type="checkbox" v-model="planMode" />
                <span>Plan 模式</span>
              </label>
              <div class="dropdown-item temp-slider-menu">
                <span>创意度</span>
                <input type="range" min="0.3" max="2.0" step="0.1" v-model.number="temperature" />
                <span class="temp-val">🔥 {{ temperature }}</span>
              </div>
              <button class="dropdown-item" @click="openVersions(); showSettings = false">📋 版本历史</button>
            </div>
          </div>
          <!-- Primary: 续写/创作 -->
          <button class="btn-ai" title="在正文末尾追加 AI 生成的下一段内容" @click="handleAiContinue(null)">{{ getContextForTarget(null).trim() ? '续写' : '创作' }}</button>
          <!-- AI dropdown: 改写 + 审改 + 分析 + 一致性 -->
          <div class="ai-dropdown">
            <button class="btn-ai-more" @click="showAiMenu = !showAiMenu" title="更多 AI 工具">AI 工具 ▾</button>
            <div class="dropdown-menu" v-if="showAiMenu" @click.stop>
              <div class="dropdown-item dropdown-rewrite-row">
                <select v-model="rewriteMode" class="rewrite-select">
                  <option v-for="m in modeOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
                </select>
                <button class="btn-rewrite-dropdown" @click="handleRewrite(null); showAiMenu = false">改写</button>
              </div>
              <button class="dropdown-item" :disabled="reviewing" @click="handleReview(); showAiMenu = false">{{ reviewing ? '审改中...' : '🔍 审改' }}</button>
              <button class="dropdown-item" :disabled="analyzing" @click="handleAnalyze(); showAiMenu = false">{{ analyzing ? '分析中...' : '📊 分析' }}</button>
              <button class="dropdown-item" :disabled="guideLoading" @click="handleWritingGuide(); showAiMenu = false">{{ guideLoading ? '生成中...' : '📝 写作指导' }}</button>
            </div>
          </div>
          <button class="btn-consistency" :disabled="checkingConsistency" @click="handleCheckConsistency()" title="检查本章与全文的冲突和矛盾">
            {{ checkingConsistency ? `检查中 ${consistencyCheckProgress}/5 ${consistencyCheckSteps[Math.min(consistencyCheckProgress, 4)] || ''}` : '🔗 一致性' }}
          </button>
          <button class="btn-annotations" title="查看和管理批注" @click="annotationSidebarOpen = true">
            💬<span v-if="annotationCount" class="annotation-badge">{{ annotationCount }}</span>
          </button>
        </template>
        <button v-else class="btn-ai-stop" @click="handleAiStop">停止生成</button>
        <button class="btn-save" :disabled="saving" @click="doSave('DRAFT')">
          {{ saving ? '保存中...' : '保存' }}
        </button>
        <button class="btn-publish" :disabled="saving" @click="handlePublish">
          定稿
        </button>
      </div>
    </div>

    <!-- Floating AI toolbar — appears when header scrolls out of view -->
    <div v-if="showFloatingToolbar" class="floating-toolbar" :style="{ top: floatingPos.top + 'px', right: floatingPos.right + 'px' }" @mousedown="onFloatDragStart">
      <template v-if="!generating && !rewriting">
        <div class="float-drag-handle" title="拖动移动"></div>
        <button class="btn-ai btn-ai-float" title="续写" @click="handleAiContinue(null)">{{ getContextForTarget(null).trim() ? '续写' : '创作' }}</button>
        <div class="ai-dropdown">
          <button class="btn-ai-more btn-ai-more-float" @click="showAiMenu = !showAiMenu" title="更多 AI 工具">AI 工具 ▾</button>
          <div class="dropdown-menu" v-if="showAiMenu" @click.stop>
            <div class="dropdown-item dropdown-rewrite-row">
              <select v-model="rewriteMode" class="rewrite-select">
                <option v-for="m in modeOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
              </select>
              <button class="btn-rewrite-dropdown" @click="handleRewrite(null); showAiMenu = false">改写</button>
            </div>
            <button class="dropdown-item" :disabled="reviewing" @click="handleReview(); showAiMenu = false">{{ reviewing ? '审改中...' : '🔍 审改' }}</button>
            <button class="dropdown-item" :disabled="analyzing" @click="handleAnalyze(); showAiMenu = false">{{ analyzing ? '分析中...' : '📊 分析' }}</button>
              <button class="dropdown-item" :disabled="guideLoading" @click="handleWritingGuide(); showAiMenu = false">{{ guideLoading ? '生成中...' : '📝 写作指导' }}</button>
          </div>
        </div>
        <button class="btn-consistency btn-consistency-float" :disabled="checkingConsistency" @click="handleCheckConsistency()" :title="checkingConsistency ? '检查中...' : '检查一致性'">
          {{ checkingConsistency ? `检查中 ${consistencyCheckProgress}/5` : '🔗' }}
        </button>
        <button class="btn-save btn-save-float" :disabled="saving" @click="doSave('DRAFT')">{{ saving ? '...' : '保存' }}</button>
        <span class="word-count word-count-float">{{ totalWords }} 字</span>
      </template>
      <template v-else>
        <span class="float-gen-label">{{ generating ? 'AI 续写中...' : 'AI 改写中...' }}</span>
        <button class="btn-ai-stop btn-ai-stop-float" @click="handleAiStop">停止生成</button>
      </template>
    </div>

    <!-- Instructions panel -->
    <div v-if="!generating && !rewriting" class="instructions-toggle">
      <button class="btn-instructions-toggle" @click="showInstructions = !showInstructions">
        {{ showInstructions ? '收起' : '📝 续写指令' }}
      </button>
      <span v-if="instructions && !showInstructions" class="instructions-preview">{{ instructions }}</span>
    </div>
    <div v-if="showInstructions && !generating && !rewriting" class="instructions-panel">
      <textarea
        v-model="instructions"
        class="instructions-input"
        rows="3"
        placeholder="描述你想要的续写方向，例如：&#10;• 这里应该是什么氛围？（紧张/温馨/悲壮）&#10;• 主角现在是什么状态？（愤怒/迷茫/坚定）&#10;• 接下来应该发生什么？（冲突/转折/揭示）&#10;• 有什么需要特别注意的？（避免OOC/不要提前揭露伏笔）"
      ></textarea>
      <div class="instructions-actions">
        <button
          class="btn-optimize"
          :disabled="!instructions.trim() || optimizingInstructions"
          @click="handleOptimizeInstructions"
        >{{ optimizingInstructions ? '优化中...' : '✨ AI 优化指令' }}</button>
        <button v-if="instructions" class="btn-instructions-clear" @click="instructions = ''">清除</button>
      </div>
    </div>

    <!-- Stream recovery -->
    <div v-if="recovering" class="recovery-banner">
      <span>检测到上次生成中断，有 {{ recoveredText.length }} 字未保存的内容可以恢复</span>
      <button class="btn-recover" @click="applyRecovery">恢复</button>
      <button class="btn-recover-discard" @click="discardRecovery">丢弃</button>
    </div>

    <!-- AI error / status -->
    <div v-if="genError" class="gen-error">{{ genError }}</div>
    <div v-if="fixSuccessMessage" class="fix-success-toast">{{ fixSuccessMessage }}</div>
    <div v-if="planGenerating" class="gen-status">AI 正在生成写作计划<span class="gen-dots">...</span></div>
    <div v-if="generating" class="gen-status">AI 正在续写<span class="gen-dots">...</span></div>
    <div v-if="rewriting" class="gen-status">AI 正在{{ modeOptions.find(m => m.value === rewriteMode)?.label }}<span class="gen-dots">...</span></div>

    <!-- Plan preview card -->
    <div v-if="planPreview && !generating" class="plan-preview-card">
      <div class="plan-preview-header clickable" @click="showPlanPreview = !showPlanPreview">
        <span class="panel-chevron">{{ showPlanPreview ? '▾' : '▸' }}</span>
        <span class="plan-preview-title">写作计划</span>
        <span v-if="planPreview.emotion_arc" class="plan-emotion-tag">{{ planPreview.emotion_arc }}</span>
      </div>

      <template v-if="showPlanPreview">
      <div v-if="planPreview.characters?.length" class="plan-preview-characters">
        <span class="plan-label">涉及角色：</span>
        <span v-for="(c, i) in planPreview.characters" :key="i" class="plan-char-tag">
          {{ c.name }}<template v-if="c.action"> — {{ c.action }}</template>
        </span>
      </div>

      <div v-if="planPreview.events?.length" class="plan-preview-events">
        <span class="plan-label">关键事件：</span>
        <ol class="plan-events-list">
          <li v-for="(e, i) in planPreview.events" :key="i">{{ e }}</li>
        </ol>
      </div>

      <div class="plan-preview-body">
        <span class="plan-label">计划详情：</span>
        <textarea
          class="plan-edit-textarea"
          v-model="planEdited"
          rows="4"
          @input="handlePlanEdit"
        ></textarea>
      </div>

      <div class="plan-preview-actions">
        <button class="btn-plan-approve" @click="handlePlanApprove">批准并生成</button>
        <button class="btn-plan-regenerate" @click="handlePlanRegenerate" :disabled="planGenerating">
          {{ planGenerating ? '生成中...' : '重新生成' }}
        </button>
        <button class="btn-plan-cancel" @click="handlePlanCancel">取消</button>
      </div>
      </template>
    </div>

    <!-- Chapter analysis result -->
    <div v-if="showAnalysis && analysisResult" class="analysis-panel">
      <div class="analysis-header clickable" @click="showAnalysisPanel = !showAnalysisPanel">
        <span class="panel-chevron">{{ showAnalysisPanel ? '▾' : '▸' }}</span>
        <span class="analysis-title">章节分析</span>
        <button class="analysis-close" @click.stop="showAnalysis = false">&times;</button>
      </div>

      <template v-if="showAnalysisPanel">
      <div v-if="analysisResult.narrative_summary" class="analysis-narrative">
        {{ analysisResult.narrative_summary }}
      </div>
      <div v-else-if="!analysisResult.core_events?.length && !analysisResult.appearing_characters?.length && !analysisResult.key_scenes?.length" class="analysis-empty-state">
        AI 暂未提取到结构化信息，请确保章节有足够内容后再试
      </div>

      <div v-if="analysisResult.core_events?.length" class="analysis-section">
        <h4>核心事件</h4>
        <ul><li v-for="(e, i) in analysisResult.core_events" :key="i">{{ e }}</li></ul>
      </div>

      <div v-if="analysisResult.appearing_characters?.length" class="analysis-section">
        <h4>出场角色</h4>
        <div class="analysis-chars">
          <span v-for="(c, i) in analysisResult.appearing_characters" :key="i" class="analysis-char-tag">
            {{ c.name }}<template v-if="c.role"> · {{ c.role }}</template>
          </span>
        </div>
      </div>

      <div v-if="analysisResult.character_state_changes?.length" class="analysis-section">
        <h4>角色状态变化</h4>
        <div v-for="(c, i) in analysisResult.character_state_changes" :key="i" class="analysis-state-change">
          <strong>{{ c.character }}</strong>: {{ c.from }} → {{ c.to }}
        </div>
      </div>

      <div v-if="analysisResult.key_scenes?.length" class="analysis-section">
        <h4>关键场景</h4>
        <div v-for="(s, i) in analysisResult.key_scenes" :key="i" class="analysis-scene">
          <span class="analysis-scene-title">{{ s.title }}</span>
          <span v-if="s.emotion" class="analysis-scene-emotion">{{ s.emotion }}</span>
          <p v-if="s.summary">{{ s.summary }}</p>
        </div>
      </div>

      <div v-if="analysisResult.emotion_curve_point" class="analysis-section">
        <h4>情绪曲线</h4>
        <div class="emotion-curve">
          <div v-if="analysisResult.emotion_curve_point.start" class="emotion-point">
            <span class="emotion-label">开篇</span>
            <span>{{ analysisResult.emotion_curve_point.start }}</span>
          </div>
          <div v-if="analysisResult.emotion_curve_point.middle" class="emotion-point">
            <span class="emotion-label">中段</span>
            <span>{{ analysisResult.emotion_curve_point.middle }}</span>
          </div>
          <div v-if="analysisResult.emotion_curve_point.end" class="emotion-point">
            <span class="emotion-label">结尾</span>
            <span>{{ analysisResult.emotion_curve_point.end }}</span>
          </div>
          <div v-if="analysisResult.emotion_curve_point.peak" class="emotion-point">
            <span class="emotion-label">高点</span>
            <span>{{ analysisResult.emotion_curve_point.peak }}</span>
          </div>
        </div>
      </div>

      <div v-if="analysisResult.new_foreshadowings?.length" class="analysis-section">
        <h4>新伏笔</h4>
        <ul><li v-for="(f, i) in analysisResult.new_foreshadowings" :key="i">{{ f }}</li></ul>
      </div>

      <div v-if="analysisResult.world_elements?.length" class="analysis-section">
        <h4>世界观要素</h4>
        <div v-for="(w, i) in analysisResult.world_elements" :key="i" class="analysis-world-item">
          <strong>{{ w.element }}</strong>: {{ w.detail }}
        </div>
      </div>
      </template>
    </div>

    <!-- Writing guide -->
    <div v-if="guideResult" class="guide-card" :class="{ collapsed: !showGuide }">
      <div class="guide-title" @click="showGuide = !showGuide">
        <span>写作指导 {{ showGuide ? '▾' : '▸' }}</span>
        <button class="guide-close" @click.stop="guideResult = null">&times;</button>
      </div>
      <div v-if="showGuide" class="guide-body">
        <div v-for="(g, i) in guideResult.guides" :key="i" class="guide-item">
          <div class="guide-item-header">
            <span class="guide-dimension">{{ g.dimension }}</span>
            <span class="guide-severity" :class="'sev-' + (g.severity || 'medium')">{{ { high: '重要', medium: '建议', low: '锦上添花' }[g.severity] || '建议' }}</span>
          </div>
          <div class="guide-issue">{{ g.issue }}</div>
          <div class="guide-suggestion">{{ g.suggestion }}</div>
        </div>
      </div>
    </div>

    <!-- Consistency warnings -->
    <div v-if="consistencyChecked" class="consistency-card">
      <div class="consistency-title clickable" @click="showConsistency = !showConsistency">
        <span class="panel-chevron">{{ showConsistency ? '▾' : '▸' }}</span>
        一致性检查
        <span v-if="!consistencyIssues.length" style="color:#16a34a;font-weight:400;margin-left:8px;">✅ 未发现问题</span>
        <button v-if="consistencyIssues.length > 1" class="btn-dismiss-all" @click.stop="consistencyIssues = []; consistencyChecked = false">全部忽略</button>
      </div>
      <template v-if="showConsistency">
      <div class="consistency-tabs">
        <button
          v-for="tab in consistencyTabs"
          :key="tab.key"
          class="consistency-tab"
          :class="{ active: consistencyTab === tab.key }"
          @click="consistencyTab = tab.key"
        >
          {{ tab.label }}
          <span v-if="tab.count" class="tab-count">{{ tab.count }}</span>
        </button>
      </div>
      <div v-if="filteredConsistencyIssues.length === 0" class="consistency-empty">未发现问题</div>
      <div v-for="(issue, i) in filteredConsistencyIssues" :key="i" class="consistency-item clickable"
        :class="issue.severity === 'high' ? 'severity-high' : 'severity-medium'"
        @click="jumpToIssue(issue)">
        <span class="consistency-entity">{{ issue.entity }}</span>
        <span class="consistency-desc">{{ issue.description }}</span>
        <button class="btn-fix-issue" :disabled="fixingIdx === i" @click.stop="fixConsistencyIssue(issue, i)">
          {{ fixingIdx === i ? '修正中...' : 'AI修正' }}
        </button>
        <button class="btn-dismiss-issue" @click.stop="dismissIssue(i)">忽略</button>
      </div>
      </template>
    </div>

    <div v-if="rewriteTarget !== null && !rewriting && !generating" class="rewrite-actions">
      <span class="rewrite-hint">改写完成，要保留吗？</span>
      <button class="btn-apply" @click="handleRewriteApply">应用改写</button>
      <button class="btn-undo" @click="handleRewriteUndo">撤销</button>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" />

    <!-- Single textarea (SCENE or CHAPTER without children) -->
    <div v-else-if="scenes.length === 0" class="editor-area" @mouseup="onTextareaMouseup">
      <InlinePopup
        :visible="inlinePopup"
        :mode="inlineMode"
        :loading="inlineLoading"
        :selected-text="inlineSelectedText"
        :result="inlineResult"
        :position="inlinePos"
        :modes="inlineModes"
        :annotate-comment="annotateComment"
        :annotate-category="annotateCategory"
        :annotate-severity="annotateSeverity"
        :annotating="annotating"
        @close="closeInlinePopup"
        @select-mode="handleInlineAction"
        @accept="acceptInlineResult"
        @submit-annotation="submitAnnotation"
        @update:annotate-comment="annotateComment = $event"
        @update:annotate-category="annotateCategory = $event"
        @update:annotate-severity="annotateSeverity = $event"
      />
      <textarea
        v-model="content"
        :class="['write-textarea', { 'fix-highlight': inlinePopup && fixingIdx >= 0 }]"
        placeholder="开始书写正文..."
        @keydown.tab.prevent="handleTabIndent"
      ></textarea>
    </div>

    <!-- CHAPTER mode with children scenes -->
    <div v-else class="editor-area chapter-mode" @mouseup="onTextareaMouseup">
      <InlinePopup
        :visible="inlinePopup"
        :mode="inlineMode"
        :loading="inlineLoading"
        :selected-text="inlineSelectedText"
        :result="inlineResult"
        :position="inlinePos"
        :modes="inlineModes"
        :annotate-comment="annotateComment"
        :annotate-category="annotateCategory"
        :annotate-severity="annotateSeverity"
        :annotating="annotating"
        @close="closeInlinePopup"
        @select-mode="handleInlineAction"
        @accept="acceptInlineResult"
        @submit-annotation="submitAnnotation"
        @update:annotate-comment="annotateComment = $event"
        @update:annotate-category="annotateCategory = $event"
        @update:annotate-severity="annotateSeverity = $event"
      />
      <!-- 章正文：主写作区 -->
      <div class="chapter-body-section">
        <div class="section-label">
          章正文
          <template v-if="!generating && !rewriting">
            <select v-model="rewriteMode" class="rewrite-select-sm" @click.stop>
              <option v-for="m in modeOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
            <button class="btn-ai-sm" @click.stop="handleRewrite('chapter')">改写</button>
            <button class="btn-ai-sm" @click.stop="handleAiContinue('chapter')">{{ chapterContent.trim() ? '续写' : '创作' }}</button>
          </template>
          <span v-else-if="genTarget === 'chapter' || rewriteTarget === 'chapter'" class="gen-inline">生成中...</span>
        </div>
        <textarea
          v-model="chapterContent"
          :class="['write-textarea', 'chapter-body', { 'fix-highlight': inlinePopup && fixingIdx >= 0 }]"
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
                <button class="btn-ai-sm" @click.stop="handleAiContinue(scene.id)">{{ (scene.content || '').trim() ? '续写' : '创作' }}</button>
              </template>
              <span v-else-if="genTarget === scene.id || rewriteTarget === scene.id" class="gen-inline">生成中...</span>
              <span class="scene-words">{{ countWords(scene.content) }} 字</span>
              <span class="scene-status-dot" :class="scene.status === 'PUBLISHED' ? 'pub' : 'draft'"></span>
            </span>
          </div>
          <textarea
            v-if="expandedScenes.has(scene.id)"
            v-model="scene.content"
            :class="['write-textarea', 'scene-body', { 'fix-highlight': inlinePopup && fixingIdx >= 0 }]"
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

    <!-- Review Panel -->
    <ReviewPanel
      :suggestions="suggestions"
      :visible="reviewPanelOpen"
      @accept="handleReviewAccept"
      @reject-all="handleReviewRejectAll"
      @close="reviewPanelOpen = false; suggestions = []"
    />

    <!-- Focus mode exit button -->
    <button v-if="focusMode" class="focus-exit-btn" @click="focusMode = false" title="退出专注模式 (Esc)">✕ 退出专注</button>

    <!-- Annotation Sidebar -->
    <AnnotationSidebar
      :structure-id="structureId"
      :visible="annotationSidebarOpen"
      @close="annotationSidebarOpen = false"
      @jump-to="handleJumpToAnnotation"
      @count-change="count => annotationCount = count"
    />

    <!-- Version History Panel -->
    <div v-if="showVersions" class="version-overlay" @click.self="closeVersions">
      <div class="version-panel" :class="{ wide: diffLines.length }">
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

        <div v-else-if="diffLines.length" class="diff-view">
          <div class="diff-header">
            <button class="btn-back" @click="diffLines = []; compareA = null; compareB = null">&larr; 返回列表</button>
            <span>v{{ compareA?.version_number }} vs v{{ compareB?.version_number }}</span>
          </div>
          <div class="diff-container">
            <div v-for="(line, i) in diffLines" :key="i" class="diff-row" :class="line.type">
              <span v-if="line.type === 'same'" class="diff-line diff-same">{{ line.text }}</span>
              <span v-else-if="line.type === 'removed'" class="diff-line diff-removed">- {{ line.text }}</span>
              <span v-else-if="line.type === 'added'" class="diff-line diff-added">+ {{ line.text }}</span>
            </div>
          </div>
        </div>

        <div v-else class="version-list">
          <div v-if="compareA || compareB" class="compare-hint">
            <span v-if="compareA && !compareB">已选 v{{ compareA.version_number }}，再选一个版本进行对比</span>
            <span v-else-if="compareA && compareB">已选 v{{ compareA.version_number }} 和 v{{ compareB.version_number }}</span>
            <button class="btn-sm btn-compare" :disabled="!compareA || !compareB" @click="computeDiff">对比</button>
            <button class="btn-sm btn-clear" @click="compareA = null; compareB = null">取消</button>
          </div>
          <div v-if="versions.length === 0" class="version-empty">暂无历史版本</div>
          <div
            v-for="ver in versions"
            :key="ver.version_number"
            class="version-item"
            :class="{ selected: compareA?.id === ver.id || compareB?.id === ver.id }"
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
              <button class="btn-sm btn-select" @click="selectCompare(ver)">对比</button>
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
.nav-btns { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; }
.nav-btn {
  padding: 2px 8px; font-size: 14px; font-family: inherit;
  background: var(--bg-surface); color: var(--color-brand);
  border: 1px solid var(--border-color); border-radius: 4px;
  cursor: pointer; transition: all 0.15s;
}
.nav-btn:hover:not(:disabled) { background: #f5f3ff; border-color: var(--color-brand); }
.nav-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.nav-info { font-size: 11px; color: var(--text-muted); min-width: 36px; text-align: center; }

.chapter-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 600px;
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
  gap: 10px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
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

/* Floating AI toolbar */
.floating-toolbar {
  position: fixed;
  z-index: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
  user-select: none;
}
.float-drag-handle {
  width: 16px; height: 16px;
  cursor: grab;
  background: radial-gradient(circle, var(--text-secondary) 1px, transparent 1px) 0 0 / 3px 3px;
  opacity: 0.5;
  flex-shrink: 0;
  margin-right: 2px;
}
.float-drag-handle:hover { opacity: 0.8; }
.btn-ai-float { padding: 6px 14px; font-size: 13px; }
.btn-ai-more-float { padding: 6px 10px; font-size: 13px; }
.btn-consistency-float { padding: 6px 10px; font-size: 13px; }
.btn-save-float { padding: 6px 14px; font-size: 13px; }
.word-count-float { font-size: 12px; margin-left: 4px; }
.float-gen-label { font-size: 13px; color: var(--color-brand); font-weight: 500; margin-right: 8px; }
.btn-ai-stop-float { padding: 6px 14px; font-size: 13px; }

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

/* ── AI dropdown menu ── */
.ai-dropdown, .settings-dropdown {
  position: relative;
}
.btn-ai-more {
  padding: 8px 14px;
  background: var(--bg-surface); color: #7c3aed;
  border: 1px solid #c4b5fd; border-radius: 6px;
  font-size: 13px; font-weight: 500; cursor: pointer;
  font-family: inherit; transition: all 0.15s;
  white-space: nowrap;
}
.btn-ai-more:hover { background: #f5f3ff; }
.dropdown-menu {
  position: absolute; top: 100%; right: 0; z-index: 200;
  margin-top: 6px; min-width: 180px;
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  padding: 6px; display: flex; flex-direction: column; gap: 2px;
}
.dropdown-item {
  display: flex; align-items: center; gap: 8px;
  width: 100%; padding: 8px 12px;
  background: none; border: none; border-radius: 6px;
  font-size: 13px; color: var(--text-primary);
  cursor: pointer; font-family: inherit; text-align: left;
  transition: background 0.12s;
  box-sizing: border-box;
}
.dropdown-item:hover:not(:disabled) { background: var(--bg-surface-hover); }
.dropdown-item:disabled { opacity: 0.4; cursor: not-allowed; }
.dropdown-rewrite-row {
  gap: 6px; padding: 6px 8px;
}
.dropdown-rewrite-row .rewrite-select {
  flex: 1; padding: 5px 6px; font-size: 12px;
}
.btn-rewrite-dropdown {
  padding: 5px 12px; font-size: 12px; font-weight: 500;
  background: #7c3aed; color: #fff; border: none;
  border-radius: 4px; cursor: pointer; font-family: inherit;
  white-space: nowrap;
}
.btn-rewrite-dropdown:hover { background: #6d28d9; }
.plan-toggle { gap: 6px; }
.temp-slider-menu {
  display: flex; align-items: center; gap: 8px; cursor: default;
  font-size: 12px; color: var(--text-secondary);
}
.temp-slider-menu input[type="range"] {
  width: 80px; height: 4px; accent-color: #5b3cc4; cursor: pointer;
}
.temp-val { font-size: 11px; color: var(--text-muted); min-width: 36px; }

.btn-review {
  padding: 8px 22px;
  background: var(--bg-surface); color: var(--color-brand);
  border: 1px solid var(--color-brand); border-radius: 6px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  transition: all 0.15s; font-family: inherit;
}
.btn-review:hover:not(:disabled) { background: #ede9fe; }
.btn-review:disabled { opacity: 0.5; cursor: not-allowed; }
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
.recovery-banner {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 16px; margin-bottom: 12px;
  background: #fffbeb; border: 1px solid #fde68a;
  border-radius: 8px; font-size: 13px; color: #92400e;
}
.btn-recover {
  padding: 5px 16px; border: none; border-radius: 6px;
  background: #5b3cc4; color: #fff; font-size: 13px; font-weight: 500;
  cursor: pointer; font-family: inherit;
}
.btn-recover:hover { background: #4a2fa8; }
.btn-recover-discard {
  padding: 5px 16px; border: 1px solid #d1d5db; border-radius: 6px;
  background: var(--bg-surface); color: var(--text-secondary); font-size: 13px;
  cursor: pointer; font-family: inherit;
}

.gen-error {
  padding: 8px 16px; margin-bottom: 12px;
  background: var(--bg-error-soft); color: var(--color-danger);
  border: 1px solid var(--border-error-soft); border-radius: 6px;
  font-size: 13px;
}
.fix-success-toast {
  padding: 8px 16px; margin-bottom: 12px;
  background: #ecfdf5; color: #065f46;
  border: 1px solid #a7f3d0; border-radius: 6px;
  font-size: 13px; font-weight: 500;
  animation: fadeInOut 2.5s ease;
}
@keyframes fadeInOut {
  0% { opacity: 0; transform: translateY(-4px); }
  15% { opacity: 1; transform: translateY(0); }
  75% { opacity: 1; }
  100% { opacity: 0; }
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

/* ── Plan mode ── */
.plan-toggle {
  display: flex; align-items: center; gap: 4px; cursor: pointer;
  font-size: 12px; color: var(--text-secondary); user-select: none;
}
.plan-toggle input[type="checkbox"] {
  accent-color: #5b3cc4; cursor: pointer;
}
.plan-toggle input[type="checkbox"]:checked + .plan-toggle-label {
  color: #5b3cc4; font-weight: 600;
}
.plan-preview-card {
  margin-bottom: 12px; padding: 16px 20px;
  background: #f5f3ff; border: 1px solid #c4b5fd;
  border-radius: 10px;
}
.plan-preview-header {
  display: flex; align-items: center; gap: 10px; margin-bottom: 12px;
}
.plan-preview-title {
  font-size: 15px; font-weight: 700; color: #5b3cc4;
}
.plan-emotion-tag {
  padding: 2px 10px; font-size: 11px; font-weight: 500;
  background: #ede9fe; color: #6d28d9; border-radius: 10px;
}
.plan-label {
  font-size: 12px; font-weight: 600; color: #6d28d9; margin-bottom: 4px; display: block;
}
.plan-preview-characters { margin-bottom: 10px; }
.plan-char-tag {
  display: inline-block; padding: 2px 8px; margin: 2px 4px 2px 0;
  font-size: 12px; background: #e0e7ff; color: #4338ca;
  border-radius: 4px;
}
.plan-preview-events { margin-bottom: 10px; }
.plan-events-list {
  margin: 0; padding-left: 20px; font-size: 13px; color: var(--text-primary);
}
.plan-events-list li { margin-bottom: 3px; }
.plan-preview-body { margin-bottom: 14px; }
.plan-edit-textarea {
  width: 100%; padding: 10px 12px; font-size: 13px; line-height: 1.6;
  border: 1px solid #c4b5fd; border-radius: 6px;
  background: #fff; color: var(--text-primary);
  font-family: inherit; resize: vertical; box-sizing: border-box;
}
.plan-edit-textarea:focus { outline: none; border-color: #5b3cc4; box-shadow: 0 0 0 3px rgba(91,60,196,0.1); }
.plan-preview-actions { display: flex; gap: 8px; }
.btn-plan-approve {
  padding: 8px 20px; font-size: 14px; font-weight: 600;
  background: #5b3cc4; color: #fff; border: none; border-radius: 6px;
  cursor: pointer; font-family: inherit;
}
.btn-plan-approve:hover { background: #4a2fa8; }
.btn-plan-regenerate {
  padding: 8px 16px; font-size: 13px; font-weight: 500;
  background: #fff; color: #5b3cc4; border: 1px solid #c4b5fd;
  border-radius: 6px; cursor: pointer; font-family: inherit;
}
.btn-plan-regenerate:hover { background: #f5f3ff; }
.btn-plan-regenerate:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-plan-cancel {
  padding: 8px 16px; font-size: 13px;
  background: transparent; color: var(--text-secondary); border: none;
  cursor: pointer; font-family: inherit;
}
.btn-plan-cancel:hover { color: var(--text-primary); }

/* ── Chapter Analysis ── */
.btn-analyze {
  padding: 6px 14px; font-size: 13px; font-weight: 500;
  background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0;
  border-radius: 6px; cursor: pointer; font-family: inherit;
}
.btn-analyze:hover { background: #dcfce7; }
.btn-analyze:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-consistency {
  padding: 6px 14px; font-size: 13px; font-weight: 500;
  background: #fffbeb; color: #b45309; border: 1px solid #fde68a;
  border-radius: 6px; cursor: pointer; font-family: inherit;
}
.btn-consistency:hover { background: #fef3c7; }
.btn-consistency:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-annotations {
  position: relative;
  padding: 6px 10px; font-size: 14px;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid var(--border-input); border-radius: 6px;
  cursor: pointer; font-family: inherit; transition: all 0.15s;
}
.btn-annotations:hover { background: var(--bg-surface-hover); color: #7c3aed; }
.annotation-badge {
  position: absolute; top: -6px; right: -6px;
  min-width: 18px; height: 18px;
  display: inline-flex; align-items: center; justify-content: center;
  background: #ef4444; color: #fff; font-size: 10px; font-weight: 700;
  border-radius: 9px; padding: 0 4px;
}

.analysis-panel {
  margin-bottom: 12px; padding: 16px 20px;
  background: #f0fdf4; border: 1px solid #bbf7d0;
  border-radius: 10px; max-height: 480px; overflow-y: auto;
}
.analysis-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px; padding-bottom: 10px;
  border-bottom: 1px solid #bbf7d0;
}
.analysis-title { font-size: 15px; font-weight: 700; color: #16a34a; }
.analysis-narrative {
  padding: 14px 18px; margin-bottom: 16px;
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%);
  border-left: 4px solid #16a34a; border-radius: 0 8px 8px 0;
  font-size: 15px; line-height: 1.8; color: #065f46;
  font-family: 'Noto Serif SC', Georgia, serif;
  animation: analysisFadeIn 0.5s ease;
}
@keyframes analysisFadeIn {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}
.analysis-empty-state {
  text-align: center; padding: 32px 0;
  font-size: 14px; color: var(--text-muted);
}
.analysis-close {
  padding: 2px 8px; font-size: 18px; font-weight: 600;
  background: none; border: none; color: var(--text-secondary);
  cursor: pointer; line-height: 1;
}
.analysis-close:hover { color: var(--text-primary); }

.analysis-section { margin-bottom: 14px; }
.analysis-section h4 {
  font-size: 13px; font-weight: 600; color: #065f46;
  margin: 0 0 6px; text-transform: uppercase; letter-spacing: 0.5px;
}
.analysis-section ul { margin: 0; padding-left: 18px; }
.analysis-section li { font-size: 13px; color: var(--text-primary); margin-bottom: 3px; line-height: 1.5; }

.analysis-chars { display: flex; flex-wrap: wrap; gap: 6px; }
.analysis-char-tag {
  padding: 2px 10px; font-size: 12px; font-weight: 500;
  background: #dcfce7; color: #166534; border-radius: 4px;
}

.analysis-state-change {
  font-size: 13px; color: var(--text-primary); margin-bottom: 4px;
  padding: 4px 10px; background: #f8fafc; border-radius: 4px;
}

.analysis-scene {
  padding: 6px 10px; margin-bottom: 4px;
  background: #f8fafc; border-radius: 4px;
}
.analysis-scene-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.analysis-scene-emotion {
  display: inline-block; margin-left: 6px; padding: 1px 8px;
  font-size: 11px; background: #ede9fe; color: #6d28d9; border-radius: 8px;
}
.analysis-scene p { font-size: 12px; color: var(--text-secondary); margin: 4px 0 0; }

.emotion-curve { display: flex; flex-wrap: wrap; gap: 8px; }
.emotion-point {
  padding: 6px 12px; font-size: 13px;
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px;
}
.emotion-label { font-weight: 600; color: var(--text-secondary); margin-right: 6px; }

.analysis-world-item {
  font-size: 13px; color: var(--text-primary); margin-bottom: 4px;
  padding: 4px 10px; background: #f8fafc; border-radius: 4px;
}

/* ── Writing guide ── */
.guide-card {
  margin-bottom: 12px; padding: 12px 16px;
  background: #fefce8; border: 1px solid #fde68a;
  border-radius: 10px; max-height: 480px; overflow-y: auto;
}
.guide-card.collapsed { max-height: none; overflow: visible; }
.guide-title {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 14px; font-weight: 700; color: #a16207; cursor: pointer;
  user-select: none;
}
.guide-close {
  padding: 2px 6px; font-size: 16px; font-weight: 600;
  background: none; border: none; color: var(--text-secondary);
  cursor: pointer; line-height: 1;
}
.guide-close:hover { color: var(--text-primary); }
.guide-body { margin-top: 12px; display: flex; flex-direction: column; gap: 12px; }
.guide-item {
  padding: 10px 14px; background: #fffbeb; border-radius: 8px;
  border: 1px solid #fde68a;
}
.guide-item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.guide-dimension {
  font-size: 13px; font-weight: 700; color: #92400e;
  background: #fef3c7; padding: 2px 10px; border-radius: 12px;
}
.guide-severity { font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 10px; }
.guide-severity.sev-high { background: #fee2e2; color: #991b1b; }
.guide-severity.sev-medium { background: #fef3c7; color: #92400e; }
.guide-severity.sev-low { background: #f0fdf4; color: #166534; }
.guide-issue { font-size: 13px; color: var(--text-primary); line-height: 1.6; margin-bottom: 4px; }
.guide-suggestion {
  font-size: 13px; color: var(--color-success); line-height: 1.6;
  padding-left: 10px; border-left: 2px solid var(--color-brand);
}

/* ── Consistency ── */
.consistency-card {
  margin-bottom: 12px; padding: 12px 16px;
  background: #fffbeb; border: 1px solid #fde68a;
  border-radius: 8px;
  max-height: 420px;
  overflow-y: auto;
}
.consistency-title { font-size: 13px; font-weight: 600; color: #92400e; margin-bottom: 8px; display: flex; align-items: center; gap: 4px; }
.panel-chevron { font-size: 10px; transition: transform 0.15s; }
.clickable { cursor: pointer; user-select: none; }
.consistency-title.clickable:hover { color: #78350f; }
.btn-collapse-all { font-size: 12px; padding: 2px 6px; }
.consistency-tabs { display: flex; gap: 6px; margin-bottom: 10px; flex-wrap: wrap; }
.consistency-tab {
  padding: 3px 10px; font-size: 11px; font-weight: 500;
  border: 1px solid #d1d5db; border-radius: 12px;
  background: var(--bg-surface); color: var(--text-secondary);
  cursor: pointer; font-family: inherit; transition: all 0.15s;
}
.consistency-tab:hover { border-color: #92400e; color: #92400e; }
.consistency-tab.active { background: #fffbeb; border-color: #92400e; color: #92400e; font-weight: 600; }
.tab-count {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 16px; height: 16px; margin-left: 4px;
  padding: 0 4px; font-size: 10px; font-weight: 600;
  background: #fef3c7; border-radius: 8px; color: #92400e;
}
.consistency-empty { font-size: 12px; color: #16a34a; padding: 4px 0; }
.consistency-item { display: flex; gap: 8px; padding: 6px 0; border-bottom: 1px solid #fef3c7; font-size: 13px; }
.consistency-item:last-child { border-bottom: none; }
.consistency-item.clickable { cursor: pointer; border-radius: 4px; padding: 6px 6px; margin: 0 -6px; transition: background 0.15s; }
.consistency-item.clickable:hover { background: #fef3c7; }
.consistency-entity {
  font-weight: 600; color: #b45309; flex-shrink: 0;
  padding: 1px 8px; background: #fef3c7; border-radius: 4px;
}
.severity-high .consistency-entity { background: #fee2e2; color: #dc2626; }
.severity-medium .consistency-entity { background: #fef3c7; color: #b45309; }
.consistency-desc { color: #78350f; line-height: 1.5; flex: 1; }
.btn-fix-issue {
  padding: 2px 8px; font-size: 11px; font-weight: 500;
  background: transparent; color: #7c3aed; border: 1px solid #ddd6fe;
  border-radius: 4px; cursor: pointer; font-family: inherit; white-space: nowrap; flex-shrink: 0;
  transition: all 0.15s;
}
.btn-fix-issue:hover { background: #f5f3ff; border-color: #7c3aed; }
.btn-fix-issue:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-dismiss-issue {
  padding: 2px 6px; font-size: 11px; font-weight: 400;
  background: transparent; color: var(--text-muted); border: 1px solid transparent;
  border-radius: 4px; cursor: pointer; font-family: inherit; white-space: nowrap; flex-shrink: 0;
}
.btn-dismiss-issue:hover { color: #dc2626; border-color: #fecaca; background: #fef2f2; }

.btn-dismiss-all {
  padding: 2px 10px; font-size: 11px; font-weight: 500;
  background: transparent; color: var(--text-muted); border: 1px solid var(--border-color);
  border-radius: 4px; cursor: pointer; font-family: inherit; margin-left: auto;
}
.btn-dismiss-all:hover { color: #dc2626; border-color: #fecaca; background: #fef2f2; }

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
.temp-slider {
  display: flex; align-items: center; gap: 4px;
}
.temp-slider input[type="range"] {
  width: 60px; height: 4px;
  accent-color: #5b3cc4;
  cursor: pointer;
}
.temp-label { font-size: 12px; color: var(--text-muted); white-space: nowrap; min-width: 36px; }

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
.write-textarea.flash-highlight {
  animation: flashPulse 1.8s ease-out;
}
.write-textarea.fix-highlight::selection {
  background: rgba(99, 102, 241, 0.3);
  color: inherit;
}
@keyframes flashPulse {
  0%   { box-shadow: 0 0 0 0 transparent; }
  15%  { box-shadow: 0 0 0 12px rgba(91,60,196,0.35); }
  30%  { box-shadow: 0 0 0 8px  rgba(91,60,196,0.25); }
  60%  { box-shadow: 0 0 0 4px  rgba(91,60,196,0.12); }
  100% { box-shadow: 0 0 0 0   transparent; }
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
.version-item.selected { border-color: #5b3cc4; background: #f5f3ff; }

/* ── Compare ── */
.compare-hint {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 10px 16px; margin-bottom: 12px;
  background: #f5f3ff; border: 1px solid #d4c5f0; border-radius: 8px;
  font-size: 12px; color: #5b3cc4;
}
.btn-compare { padding: 5px 14px; border: none; border-radius: 6px; font-size: 12px; font-weight: 500; cursor: pointer; font-family: inherit; background: #5b3cc4; color: #fff; }
.btn-compare:hover:not(:disabled) { background: #4a2fa8; }
.btn-compare:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-clear { padding: 5px 14px; border: 1px solid var(--border-color); border-radius: 6px; font-size: 12px; cursor: pointer; font-family: inherit; background: var(--bg-surface); }
.btn-select { padding: 5px 10px; border: 1px solid var(--border-color); border-radius: 5px; font-size: 11px; cursor: pointer; font-family: inherit; background: var(--bg-surface); }
.btn-select:hover { border-color: #5b3cc4; color: #5b3cc4; }

.version-panel.wide { width: 760px; }

/* ── Diff view ── */
.diff-view { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.diff-header {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-bottom: 1px solid var(--border-color);
  font-size: 14px; font-weight: 500; flex-shrink: 0;
}
.diff-container { flex: 1; overflow-y: auto; font-family: 'Consolas', 'Courier New', monospace; }
.diff-row { display: flex; font-size: 13px; line-height: 1.6; min-height: 22px; }
.diff-line { flex: 1; padding: 1px 12px; white-space: pre-wrap; word-break: break-all; }
.diff-same { color: var(--text-primary); }
.diff-removed { background: #fee2e2; color: #b91c1c; }
.diff-added { background: #dcfce7; color: #166534; }
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
.btn-focus.active { background: #ede9fe; color: #7c3aed; border-color: #c4b5fd; }
.btn-collapse-all, .btn-focus-mode {
  width: auto; padding: 4px 8px; font-size: 12px; font-weight: 500;
}
.btn-inline-toggle {
  width: 34px; padding: 4px; gap: 0; font-size: 14px;
}
.inline-toggle-icon { font-size: 14px; }

.focus-exit-btn {
  position: fixed; top: 16px; right: 24px; z-index: 100;
  padding: 6px 16px; font-size: 13px; font-weight: 500;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid var(--border-color); border-radius: 6px;
  cursor: pointer; font-family: inherit;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: all 0.15s;
}
.focus-exit-btn:hover { background: #fef2f2; color: #dc2626; border-color: #fecaca; }

/* ── Inline AI Popup ── */
.editor-area { position: relative; }
.inline-popup {
  position: absolute;
  z-index: 50;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.12);
  min-width: 200px;
  max-width: 380px;
  max-height: 60vh;
  overflow-y: auto;
}
.inline-popup.dragging { cursor: grabbing; user-select: none; }
.inline-drag-handle {
  height: 6px; cursor: grab; background: var(--color-brand);
  border-radius: 10px 10px 0 0; opacity: 0.4;
}
.inline-drag-handle:hover { opacity: 0.8; }
.inline-popup.dragging .inline-drag-handle { opacity: 1; background: var(--color-brand); }
.inline-actions {
  display: flex; gap: 4px; padding: 8px;
  align-items: center;
}
.inline-btn {
  padding: 5px 10px; font-size: 12px; font-weight: 500;
  border: 1px solid #e5e7eb; border-radius: 6px;
  background: #f9fafb; color: var(--text-primary);
  cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.inline-btn:hover { background: #ede9fe; border-color: #c4b5fd; color: #7c3aed; }
.inline-btn.active { background: #ede9fe; border-color: #7c3aed; color: #7c3aed; }
.inline-close {
  margin-left: auto; width: 24px; height: 24px;
  display: inline-flex; align-items: center; justify-content: center;
  background: none; border: none; font-size: 16px; color: var(--text-muted);
  cursor: pointer; border-radius: 4px;
}
.inline-close:hover { background: #f3f4f6; color: var(--text-primary); }
.inline-result {
  padding: 10px; min-width: 280px;
}
.inline-loading {
  font-size: 13px; color: #7c3aed; padding: 8px 0;
}
.inline-mode-label {
  font-size: 10px; color: var(--text-muted); margin-top: 6px;
  text-align: right;
}
.inline-diff {
  max-height: 200px; overflow-y: auto; font-size: 13px; line-height: 1.6;
}
.inline-diff-view {
  padding: 8px; background: var(--bg-surface); border-radius: 6px;
  white-space: pre-wrap; word-break: break-all;
}
.diff-same { color: var(--text-primary); }
.diff-remove { background: #fecaca; color: #991b1b; text-decoration: line-through; }
.diff-add { background: #bbf7d0; color: #166534; }
.inline-result-actions {
  display: flex; gap: 8px; margin-top: 8px;
}
.inline-result-actions .btn-apply {
  padding: 5px 16px; font-size: 12px; background: #059669; color: #fff;
  border: none; border-radius: 5px; cursor: pointer; font-family: inherit;
  font-weight: 500;
}
.inline-result-actions .btn-apply:hover { background: #047857; }
.inline-result-actions .btn-undo {
  padding: 5px 16px; font-size: 12px; background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid #d1d5db; border-radius: 5px; cursor: pointer; font-family: inherit;
}
.inline-result-actions .btn-undo:hover { background: var(--bg-surface-hover); }

/* ── Inline annotation form ── */
.inline-annotate {
  padding: 10px; min-width: 300px;
}
.inline-annotate-header {
  display: flex; flex-direction: column; gap: 4px; margin-bottom: 10px;
}
.inline-annotate-header span:first-child {
  font-size: 13px; font-weight: 600; color: #7c3aed;
}
.inline-selected-preview {
  font-size: 12px; color: var(--text-secondary); line-height: 1.4;
  padding: 6px 8px; background: #f5f3ff; border-radius: 4px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.inline-annotate-input {
  width: 100%; padding: 8px 10px; font-size: 13px; line-height: 1.5;
  border: 1px solid var(--border-input); border-radius: 6px;
  font-family: inherit; resize: vertical; box-sizing: border-box;
  background: var(--bg-body); color: var(--text-primary);
}
.inline-annotate-input:focus { outline: none; border-color: #7c3aed; }
.inline-annotate-meta {
  display: flex; gap: 8px; margin-top: 8px;
}
.inline-select {
  padding: 4px 8px; font-size: 12px;
  border: 1px solid var(--border-input); border-radius: 4px;
  background: var(--bg-surface); color: var(--text-primary);
  font-family: inherit; cursor: pointer;
}

.btn-versions {
  padding: 8px 22px;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid var(--border-input); border-radius: 6px;
  font-size: 14px; font-weight: 500;
  cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.btn-versions:hover { background: var(--bg-surface-hover); color: var(--text-primary); }

/* ── Instructions panel ── */
.instructions-toggle {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 8px;
}
.btn-instructions-toggle {
  background: none; border: 1px solid var(--border-input);
  color: var(--text-secondary); font-size: 13px;
  padding: 4px 12px; border-radius: 6px;
  cursor: pointer; font-family: inherit;
  white-space: nowrap;
}
.btn-instructions-toggle:hover { border-color: var(--brand); color: var(--brand); }
.instructions-preview {
  font-size: 13px; color: var(--text-tertiary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 400px;
}
.instructions-panel {
  margin-bottom: 12px;
  padding: 12px;
  background: var(--bg-surface); border: 1px solid var(--border-input);
  border-radius: 8px;
}
.instructions-input {
  width: 100%; padding: 10px 12px;
  border: 1px solid var(--border-input); border-radius: 6px;
  font-size: 14px; font-family: inherit; line-height: 1.6;
  resize: vertical; background: var(--bg-body); color: var(--text-primary);
}
.instructions-input::placeholder { color: var(--text-tertiary); }
.instructions-input:focus { outline: none; border-color: var(--brand); }
.instructions-actions {
  display: flex; gap: 8px; margin-top: 8px;
}
.btn-optimize {
  padding: 5px 14px; border: none; border-radius: 6px;
  background: var(--brand); color: #fff;
  font-size: 13px; cursor: pointer; font-family: inherit;
  transition: opacity 0.15s;
}
.btn-optimize:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-optimize:hover:not(:disabled) { background: var(--brand-hover); }
.btn-instructions-clear {
  background: none; border: none; color: var(--text-tertiary);
  font-size: 13px; cursor: pointer; font-family: inherit;
}
.btn-instructions-clear:hover { color: var(--text-secondary); }

@media (max-width: 640px) {
  .chapter-write { padding: 16px; }
  .write-header { flex-direction: column; gap: 12px; }
  .write-textarea { padding: 20px; font-size: 15px; }
  .scene-bar { padding: 10px 14px; }
  .scene-body { padding: 14px 20px 22px; }
}
</style>
