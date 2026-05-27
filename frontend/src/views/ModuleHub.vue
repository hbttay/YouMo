<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBookStore } from '@/stores/book'
import {
  listCharacters, getOutline, getWorldSetting, getForeshadowings,
  getBookStats, getStyleProfile, getCharacterRelationshipGraph
} from '@/api/book'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const route = useRoute()
const router = useRouter()
const store = useBookStore()

const LS_VIEW = 'hub-view-mode'
const LS_EXPANDED = 'hub-expanded'

const DEPTH = { L0: '背景板', L1: '配角', L2: '重要配角', L3: '主角' }
const DEPTH_CLASS = { L0: 'dt-l0', L1: 'dt-l1', L2: 'dt-l2', L3: 'dt-l3' }
const FS_TYPE = { ITEM: '物品', EVENT: '事件', CHARACTER: '角色', RELATIONSHIP: '关系', PLOT_TWIST: '转折' }
const FS_COLORS = { ITEM: '#f59e0b', EVENT: '#3b82f6', CHARACTER: '#7c3aed', RELATIONSHIP: '#ec4899', PLOT_TWIST: '#ef4444' }
const FS_STATUS = { ACTIVE: '活跃', RECYCLED: '已回收', DROPPED: '已放弃' }

function toList(d) { return d?.data || d || [] }
function isSet(v) { return v && v.trim().length > 0 }

const viewMode = ref(localStorage.getItem(LS_VIEW) || 'by-book')
const expandedBooks = ref(loadExpanded())
const statsSortBy = ref('default')

function loadExpanded() {
  try { return JSON.parse(localStorage.getItem(LS_EXPANDED) || '[]') } catch { return [] }
}
function saveExpanded() {
  localStorage.setItem(LS_EXPANDED, JSON.stringify(expandedBooks.value))
}
function toggleBook(bookId) {
  const i = expandedBooks.value.indexOf(bookId)
  if (i >= 0) expandedBooks.value.splice(i, 1)
  else expandedBooks.value.push(bookId)
  saveExpanded()
}
function switchView(mode) {
  viewMode.value = mode
  localStorage.setItem(LS_VIEW, mode)
}

const MODULES = {
  characters: {
    label: '角色工坊', icon: '🎭', color: '#7c3aed',
    link: (id) => `/books/${id}/characters?from=hub`,
    moduleType: 'characters',
    async fetch(bookId) {
      const [chars, rel] = await Promise.allSettled([
        listCharacters(bookId), getCharacterRelationshipGraph(bookId)
      ])
      const characters = chars.status === 'fulfilled' ? toList(chars.value) : []
      const relationships = rel.status === 'fulfilled' ? (rel.value?.data?.edges || rel.value?.edges || []) : []
      return { characters, relCount: relationships.length }
    },
    preview(data) {
      const chars = data?.characters || []
      if (!chars.length) return null
      return {
        depthCounts: { L3: chars.filter(c => c.depth_level === 'L3').length, L2: chars.filter(c => c.depth_level === 'L2').length, L1: chars.filter(c => c.depth_level === 'L1').length, L0: chars.filter(c => c.depth_level === 'L0' || !c.depth_level).length },
        sample: chars.slice(0, 6),
        total: chars.length,
        relCount: data?.relCount || 0,
      }
    },
    globalView(charsByBook) {
      const all = []
      for (const { book, data } of charsByBook) {
        const chars = data?.characters || []
        for (const c of chars) all.push({ ...c, _book: book })
      }
      return all
    },
  },
  'world-setting': {
    label: '世界观管理', icon: '🌍', color: '#059669',
    link: (id) => `/books/${id}/world-setting?from=hub`,
    moduleType: 'world-setting',
    async fetch(bookId) {
      const res = await getWorldSetting(bookId)
      return res?.data || res || {}
    },
    preview(data) {
      const fields = ['era', 'geography', 'history_events', 'politics', 'economy', 'culture', 'military']
      const filled = fields.filter(f => isSet(data?.[f]))
      return { era: (data?.era || '').slice(0, 80), filled: filled.length, total: fields.length }
    },
    globalView(items) {
      return items.map(({ book, data }) => ({ book, filled: ['era','geography','history_events','politics','economy','culture','military'].filter(f => isSet(data?.[f])).length }))
    },
  },
  outline: {
    label: '大纲编排', icon: '📋', color: '#d97706',
    link: (id) => `/books/${id}/outline?from=hub`,
    moduleType: 'outline',
    async fetch(bookId) {
      const res = await getOutline(bookId)
      return toList(res)
    },
    preview(data) {
      const nodes = data || []
      const volumes = nodes.filter(n => n.node_type === 'VOLUME').sort((a, b) => (a.sequence || 0) - (b.sequence || 0))
      const chapters = nodes.filter(n => n.node_type === 'CHAPTER')
      const scenes = nodes.filter(n => n.node_type === 'SCENE')
      return { volumes: volumes.slice(0, 4), total: nodes.length, chapterCount: chapters.length, sceneCount: scenes.length }
    },
    globalView(nodesByBook) {
      return nodesByBook.map(({ book, data }) => ({ book, nodes: (data || []).filter(n => n.node_type === 'VOLUME').sort((a, b) => (a.sequence || 0) - (b.sequence || 0)).slice(0, 5), total: (data || []).length }))
    },
  },
  foreshadowings: {
    label: '伏笔管理', icon: '📌', color: '#dc2626',
    link: (id) => `/books/${id}/foreshadowings?from=hub`,
    moduleType: 'foreshadowings',
    async fetch(bookId) {
      const res = await getForeshadowings(bookId)
      return toList(res)
    },
    preview(data) {
      const items = data || []
      const byType = {}; const byStatus = {}
      for (const f of items) { byType[f.foreshadowing_type] = (byType[f.foreshadowing_type] || 0) + 1; byStatus[f.status] = (byStatus[f.status] || 0) + 1 }
      return { byType, byStatus, total: items.length, sample: items.slice(0, 3) }
    },
    globalView(itemsByBook) {
      const all = []
      for (const { book, data } of itemsByBook) {
        for (const f of (data || [])) all.push({ ...f, _book: book })
      }
      return all
    },
  },
  stats: {
    label: '统计分析', icon: '📊', color: '#0891b2',
    link: (id) => `/books/${id}?from=hub`,
    moduleType: 'stats',
    async fetch(bookId) {
      const [st, chars] = await Promise.allSettled([getBookStats(bookId), listCharacters(bookId)])
      const stats = st.status === 'fulfilled' ? (st.value?.data || st.value || {}) : {}
      const ch = chars.status === 'fulfilled' ? toList(chars.value) : []
      return { ...stats, charCount: ch.length }
    },
    preview(data) {
      return {
        totalWords: data?.totalWords || data?.total_words || 0,
        chapterCount: data?.chapterCount || data?.chapter_count || data?.totalChapters || data?.total_chapters || 0,
        charCount: data?.charCount || 0,
        createdAt: null, // filled from book
        updatedAt: null,
      }
    },
    globalView(items) {
      return items.map(({ book, data }) => ({ book, words: data?.totalWords || data?.total_words || 0, chars: data?.charCount || 0 }))
    },
  },
  style: {
    label: '风格分析', icon: '🎨', color: '#be185d',
    link: (id) => `/books/${id}?from=hub`,
    moduleType: 'style',
    async fetch(bookId) {
      const res = await getStyleProfile(bookId)
      return res?.data || res || {}
    },
    preview(data) {
      if (!data || (!data.avgSentenceLength && !data.dialogueRatio && !data.avg_sentence_length && !data.dialogue_ratio)) return null
      return {
        avgSentenceLength: data.avgSentenceLength || data.avg_sentence_length,
        dialogueRatio: data.dialogueRatio || data.dialogue_ratio,
        vocabularyRichness: data.vocabularyRichness || data.vocabulary_richness,
      }
    },
    globalView(items) {
      return items.map(({ book, data }) => ({ book, sl: data?.avgSentenceLength || data?.avg_sentence_length, dr: data?.dialogueRatio || data?.dialogue_ratio }))
    },
  },
  consistency: {
    label: '一致性检查', icon: '🔍', color: '#4f46e5',
    link: (id) => `/books/${id}?from=hub`,
    moduleType: 'consistency',
    fetch: null,
    preview: () => null,
    globalView: () => [],
  },
}

const mod = computed(() => MODULES[route.params.type])
const summaries = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  summaries.value = []
  await store.fetchBooks()
  const cfg = mod.value
  if (!cfg || store.books.length === 0) { loading.value = false; return }

  const results = await Promise.allSettled(
    store.books.map(async (book) => {
      try {
        const data = cfg.fetch ? await cfg.fetch(book.id) : null
        return { book, data, preview: cfg.preview(data) }
      } catch {
        return { book, data: null, preview: null }
      }
    })
  )
  summaries.value = results.map(r => r.status === 'fulfilled' ? r.value : { book: null, data: null, preview: null })
  // auto-expand all
  if (expandedBooks.value.length === 0) {
    expandedBooks.value = store.books.map(b => b.id)
    saveExpanded()
  }
  loading.value = false
}

onMounted(load)
watch(() => route.params.type, () => { if (mod.value) load() })

function goToBook(bookId) {
  const target = mod.value?.link(bookId)
  if (target) router.push(target)
}

function fmtDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN')
}

function genderColor(g) {
  if (g === '男') return '#3b82f6'
  if (g === '女') return '#ec4899'
  return '#9ca3af'
}

const sortedSummaries = computed(() => {
  const list = [...summaries.value]
  if (route.params.type !== 'stats') return list
  switch (statsSortBy.value) {
    case 'words-desc': return list.sort((a, b) => (b.preview?.totalWords || 0) - (a.preview?.totalWords || 0))
    case 'words-asc': return list.sort((a, b) => (a.preview?.totalWords || 0) - (b.preview?.totalWords || 0))
    case 'chapters-desc': return list.sort((a, b) => (b.preview?.chapterCount || 0) - (a.preview?.chapterCount || 0))
    case 'chapters-asc': return list.sort((a, b) => (a.preview?.chapterCount || 0) - (b.preview?.chapterCount || 0))
    case 'chars-desc': return list.sort((a, b) => (b.preview?.charCount || 0) - (a.preview?.charCount || 0))
    case 'chars-asc': return list.sort((a, b) => (a.preview?.charCount || 0) - (b.preview?.charCount || 0))
    case 'created-desc': return list.sort((a, b) => new Date(b.book?.createdAt || 0) - new Date(a.book?.createdAt || 0))
    case 'created-asc': return list.sort((a, b) => new Date(a.book?.createdAt || 0) - new Date(b.book?.createdAt || 0))
    default: return list
  }
})

const globalCharData = computed(() => {
  const all = []
  for (const item of summaries.value) {
    const chars = item.data?.characters || []
    for (const c of chars) all.push({ ...c, _book: item.book })
  }
  const byLevel = { L3: [], L2: [], L1: [], L0: [] }
  for (const c of all) {
    const lvl = c.depth_level || 'L0'
    if (byLevel[lvl]) byLevel[lvl].push(c)
    else byLevel.L0.push(c)
  }
  return { all, byLevel }
})

const globalFSData = computed(() => {
  const byType = {}
  let total = 0
  const byStatus = {}
  for (const item of summaries.value) {
    const items = item.data || []
    for (const f of items) {
      const t = f.foreshadowing_type || 'OTHER'
      if (!byType[t]) byType[t] = []
      byType[t].push({ ...f, _book: item.book })
      total++
      byStatus[f.status] = (byStatus[f.status] || 0) + 1
    }
  }
  return { byType, total, byStatus }
})

const globalOutlineData = computed(() => {
  let totalNodes = 0, totalVolumes = 0, totalChapters = 0, totalScenes = 0
  const perBook = []
  for (const item of summaries.value) {
    const nodes = item.data || []
    const vols = nodes.filter(n => n.node_type === 'VOLUME').sort((a, b) => (a.sequence || 0) - (b.sequence || 0))
    const chaps = nodes.filter(n => n.node_type === 'CHAPTER')
    const scenes = nodes.filter(n => n.node_type === 'SCENE')
    totalNodes += nodes.length; totalVolumes += vols.length; totalChapters += chaps.length; totalScenes += scenes.length
    perBook.push({ book: item.book, volumes: vols, chapters: chaps.length, scenes: scenes.length, total: nodes.length })
  }
  return { totalNodes, totalVolumes, totalChapters, totalScenes, perBook }
})
</script>

<template>
  <div class="hub">
    <router-link to="/" class="back-link">&larr; 返回首页</router-link>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="!mod" class="empty">未知模块</div>

    <template v-else>
      <div class="hub-header">
        <span class="hub-icon">{{ mod.icon }}</span>
        <div>
          <h1>{{ mod.label }}</h1>
          <p>{{ mod.desc || '' }}</p>
        </div>
      </div>

      <div v-if="store.books.length === 0" class="empty-state">
        <p>还没有书籍，先去创建一本吧</p>
        <router-link to="/books/create" class="btn-primary">创建书籍</router-link>
      </div>

      <template v-else>
        <!-- View toggle -->
        <div class="view-toggle">
          <button :class="{ active: viewMode === 'by-book' }" @click="switchView('by-book')">按书籍</button>
          <button :class="{ active: viewMode === 'global' }" @click="switchView('global')">全局</button>
        </div>

        <!-- Sort toolbar for stats -->
        <div v-if="viewMode === 'by-book' && route.params.type === 'stats'" class="stats-sort-bar">
          <select v-model="statsSortBy" class="stats-sort-select">
            <option value="default">默认排序</option>
            <option value="words-desc">总字数 ↓</option>
            <option value="words-asc">总字数 ↑</option>
            <option value="chapters-desc">章节数 ↓</option>
            <option value="chapters-asc">章节数 ↑</option>
            <option value="chars-desc">角色数 ↓</option>
            <option value="chars-asc">角色数 ↑</option>
            <option value="created-desc">创建时间 新→旧</option>
            <option value="created-asc">创建时间 旧→新</option>
          </select>
        </div>

        <!-- ═══ By Book View ═══ -->
        <div v-if="viewMode === 'by-book'" class="book-list">
          <div v-for="item in sortedSummaries" :key="item.book.id" class="book-card"
            :style="{ '--c': mod.color }">
            <!-- Header: always visible, click to enter -->
            <div class="card-head" @click="toggleBook(item.book.id)">
              <div class="card-head-left">
                <h3>{{ item.book.title }}</h3>
                <span v-if="item.preview" class="card-summary">{{ item.preview.total ? `共 ${item.preview.total} 项` : '' }}</span>
              </div>
              <div class="card-head-right">
                <button class="btn-expand" @click.stop="toggleBook(item.book.id)" title="展开/折叠">
                  {{ expandedBooks.includes(item.book.id) ? '▾' : '▸' }}
                </button>
                <span class="card-arrow" @click.stop="goToBook(item.book.id)" title="进入书籍">→</span>
              </div>
            </div>

            <!-- Preview: expandable -->
            <div v-if="expandedBooks.includes(item.book.id) && item.preview" class="card-preview">

              <!-- Characters -->
              <template v-if="route.params.type === 'characters'">
                <div v-if="item.preview.depthCounts" class="preview-depth">
                  <span v-for="(n, lvl) in item.preview.depthCounts" :key="lvl" v-show="n > 0"
                    :class="['depth-tag', DEPTH_CLASS[lvl] || 'dt-l0']">{{ DEPTH[lvl] }} {{ n }}</span>
                  <span v-if="item.preview.relCount" class="depth-tag rel-tag">关系 {{ item.preview.relCount }}</span>
                </div>
                <div v-if="item.preview.sample?.length" class="preview-chars">
                  <span v-for="c in item.preview.sample" :key="c.id" class="char-chip">
                    {{ c.name }}<small>{{ DEPTH[c.depth_level] || '' }}</small>
                  </span>
                  <span v-if="item.preview.total > item.preview.sample.length" class="char-more">
                    +{{ item.preview.total - item.preview.sample.length }} 个
                  </span>
                </div>
              </template>

              <!-- World -->
              <template v-else-if="route.params.type === 'world-setting'">
                <p v-if="item.preview.era" class="preview-era">{{ item.preview.era }}</p>
                <div class="preview-meta">
                  已填 {{ item.preview.filled }}/{{ item.preview.total }} 个字段
                </div>
              </template>

              <!-- Outline -->
              <template v-else-if="route.params.type === 'outline'">
                <div v-if="item.preview.volumes?.length" class="preview-outline">
                  <div v-for="v in item.preview.volumes" :key="v.id" class="outline-node">
                    <span class="on-volume">{{ v.title || '未命名卷' }}</span>
                  </div>
                </div>
                <div class="preview-meta">
                  共 {{ item.preview.total }} 个节点（{{ item.preview.volumes?.length || 0 }}卷 {{ item.preview.chapterCount }}章 {{ item.preview.sceneCount }}节）
                </div>
              </template>

              <!-- Foreshadowings -->
              <template v-else-if="route.params.type === 'foreshadowings'">
                <div class="preview-fs-types">
                  <span v-for="(n, t) in item.preview.byType" :key="t" class="fs-type-tag">
                    {{ FS_TYPE[t] || t }} {{ n }}
                  </span>
                </div>
                <div class="preview-fs-status">
                  <span v-for="(n, s) in item.preview.byStatus" :key="s" class="fs-status-tag"
                    :class="{ 'fs-recycled': s === 'RECYCLED', 'fs-dropped': s === 'DROPPED' }">
                    {{ FS_STATUS[s] || s }} {{ n }}
                  </span>
                </div>
                <ul v-if="item.preview.sample?.length" class="preview-fs-list">
                  <li v-for="f in item.preview.sample" :key="f.id">{{ f.description?.slice(0, 40) }}{{ f.description?.length > 40 ? '...' : '' }}</li>
                </ul>
              </template>

              <!-- Stats -->
              <template v-else-if="route.params.type === 'stats'">
                <div class="preview-stats">
                  <div class="stat-item"><span class="stat-val">{{ (item.preview.totalWords || 0).toLocaleString() }}</span><span class="stat-lbl">总字数</span></div>
                  <div class="stat-item"><span class="stat-val">{{ item.preview.chapterCount || 0 }}</span><span class="stat-lbl">章节</span></div>
                  <div class="stat-item"><span class="stat-val">{{ item.preview.charCount || 0 }}</span><span class="stat-lbl">角色</span></div>
                  <div class="stat-item"><span class="stat-val">{{ fmtDate(item.book.createdAt) }}</span><span class="stat-lbl">创建</span></div>
                </div>
              </template>

              <!-- Style -->
              <template v-else-if="route.params.type === 'style'">
                <div v-if="item.preview" class="preview-stats">
                  <div class="stat-item"><span class="stat-val">{{ item.preview.avgSentenceLength || '—' }}</span><span class="stat-lbl">均句长</span></div>
                  <div class="stat-item"><span class="stat-val">{{ item.preview.dialogueRatio ? (item.preview.dialogueRatio * 100).toFixed(0) + '%' : '—' }}</span><span class="stat-lbl">对话比</span></div>
                  <div class="stat-item"><span class="stat-val">{{ item.preview.vocabularyRichness || '—' }}</span><span class="stat-lbl">词汇丰富度</span></div>
                </div>
                <div v-else class="preview-meta">未分析</div>
              </template>

              <!-- Consistency — no preview -->
              <template v-else-if="route.params.type === 'consistency'">
                <div class="preview-meta">进入书籍后可运行一致性检查</div>
              </template>
            </div>
          </div>
        </div>

        <!-- ═══ Global View ═══ -->
        <div v-else class="global-view">
          <template v-if="route.params.type === 'characters'">
            <!-- Summary stats -->
            <div class="global-char-summary">
              <div class="gcs-item"><span class="gcs-num">{{ globalCharData.all.length }}</span><span class="gcs-lbl">总角色</span></div>
              <div class="gcs-item"><span class="gcs-num">{{ globalCharData.byLevel.L3.length }}</span><span class="gcs-lbl">主角</span></div>
              <div class="gcs-item"><span class="gcs-num">{{ globalCharData.byLevel.L2.length }}</span><span class="gcs-lbl">重要配角</span></div>
              <div class="gcs-item"><span class="gcs-num">{{ globalCharData.byLevel.L1.length + globalCharData.byLevel.L0.length }}</span><span class="gcs-lbl">配角/背景板</span></div>
            </div>
            <div v-if="globalCharData.all.length === 0" class="preview-meta" style="text-align:center;padding:40px">暂无角色</div>
            <div v-for="level in ['L3','L2','L1','L0']" v-else :key="level" class="global-depth-section">
              <div v-if="globalCharData.byLevel[level].length" class="global-depth-group">
                <h3 class="depth-heading">
                  <span :class="['depth-dot', DEPTH_CLASS[level]]"></span>
                  {{ DEPTH[level] }}
                  <span class="depth-count">{{ globalCharData.byLevel[level].length }}</span>
                </h3>
                <div class="global-char-grid">
                  <div v-for="c in globalCharData.byLevel[level]" :key="c.id" class="global-char-card"
                    @click="router.push(`/books/${c._book.id}/characters?from=hub`)">
                    <div class="char-avatar" :style="{ borderColor: genderColor(c.gender), color: genderColor(c.gender) }">
                      <span class="avatar-initial">{{ c.name?.charAt(0) || '?' }}</span>
                    </div>
                    <div class="char-info">
                      <span class="char-name">{{ c.name }}</span>
                      <span class="char-book-tag">{{ c._book.title }}</span>
                    </div>
                    <div class="char-meta-right">
                      <span v-if="c.gender" class="char-gender">{{ c.gender }}</span>
                      <span v-if="c.race" class="char-race">{{ c.race }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <template v-else-if="route.params.type === 'outline'">
            <div class="global-char-summary">
              <div class="gcs-item"><span class="gcs-num">{{ globalOutlineData.totalNodes }}</span><span class="gcs-lbl">总节点</span></div>
              <div class="gcs-item"><span class="gcs-num">{{ globalOutlineData.totalVolumes }}</span><span class="gcs-lbl">卷</span></div>
              <div class="gcs-item"><span class="gcs-num">{{ globalOutlineData.totalChapters }}</span><span class="gcs-lbl">章</span></div>
              <div class="gcs-item"><span class="gcs-num">{{ globalOutlineData.totalScenes }}</span><span class="gcs-lbl">节</span></div>
            </div>
            <div v-if="globalOutlineData.totalNodes === 0" class="preview-meta" style="text-align:center;padding:40px">暂无大纲</div>
            <div v-for="item in globalOutlineData.perBook" v-else :key="item.book.id" class="global-group"
              @click="router.push(`/books/${item.book.id}/outline?from=hub`)" style="cursor:pointer">
              <h3 class="global-book" :style="{ color: mod.color }">{{ item.book.title }}</h3>
              <div class="preview-outline">
                <div v-for="v in item.volumes.slice(0, 5)" :key="v.id" class="outline-node">
                  <span class="on-volume">{{ v.title || '未命名' }}</span>
                </div>
                <div v-if="item.volumes.length > 5" class="preview-meta">+{{ item.volumes.length - 5 }} 卷</div>
              </div>
              <div class="preview-meta">{{ item.total }} 节点（{{ item.chapters }}章 {{ item.scenes }}节）</div>
            </div>
          </template>
          <template v-else-if="route.params.type === 'foreshadowings'">
            <div class="global-char-summary">
              <div v-for="(label, type) in FS_TYPE" :key="type" class="gcs-item">
                <span class="gcs-num">{{ (globalFSData.byType[type] || []).length }}</span>
                <span class="gcs-lbl">{{ label }}</span>
              </div>
            </div>
            <div v-if="globalFSData.total === 0" class="preview-meta" style="text-align:center;padding:40px">暂无伏笔</div>
            <div v-for="(items, type) in globalFSData.byType" v-else :key="type" class="global-depth-section">
              <div v-if="items.length" class="global-depth-group">
                <h3 class="depth-heading">
                  <span class="fs-type-badge" :style="{ background: FS_COLORS[type] || '#9ca3af' }">{{ FS_TYPE[type] || type }}</span>
                  <span class="depth-count">{{ items.length }}</span>
                </h3>
                <div class="global-fs-list">
                  <div v-for="f in items" :key="f.id" class="global-fs-card"
                    @click="router.push(`/books/${f._book.id}/foreshadowings?from=hub`)">
                    <p class="global-fs-desc">{{ f.description?.slice(0, 60) }}{{ f.description?.length > 60 ? '...' : '' }}</p>
                    <div class="global-fs-meta">
                      <span class="char-book-tag">{{ f._book.title }}</span>
                      <span :class="['fs-status-sm', (f.status || 'ACTIVE').toLowerCase()]">
                        {{ FS_STATUS[f.status] || f.status }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div v-for="item in summaries" :key="item.book.id" class="global-group"
              @click="goToBook(item.book.id)" style="cursor:pointer">
              <h3 class="global-book" :style="{ color: mod.color }">{{ item.book.title }}</h3>
              <div class="preview-meta">{{ item.preview?.total ? `共 ${item.preview.total} 项` : '进入 →' }}</div>
            </div>
          </template>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.hub { max-width: 840px; margin: 0 auto; }
.back-link { color: #5b3cc4; text-decoration: none; font-size: 13px; }
.back-link:hover { color: #4a2fa8; }

.hub-header { display: flex; align-items: center; gap: 16px; margin: 20px 0 16px; }
.hub-icon { font-size: 36px; line-height: 1; }
.hub-header h1 { font-size: 24px; font-weight: 700; color: var(--text-primary); margin: 0 0 4px; }
.hub-header p { font-size: 14px; color: var(--text-muted); margin: 0; }

.empty-state { text-align: center; padding: 80px 20px; color: var(--text-muted); }
.empty-state p { margin-bottom: 16px; }

/* View toggle */
.view-toggle { display: flex; gap: 0; margin-bottom: 18px; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; width: fit-content; }
.view-toggle button {
  padding: 7px 18px; font-size: 13px; font-weight: 500; background: var(--bg-surface);
  color: var(--text-secondary); border: none; cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.view-toggle button:first-child { border-right: 1px solid var(--border-color); }
.view-toggle button.active { background: #f5f3ff; color: var(--color-brand); font-weight: 600; }
.view-toggle button:hover:not(.active) { background: var(--bg-surface-hover); }

/* Stats sort bar */
.stats-sort-bar { margin-bottom: 14px; }
.stats-sort-select {
  padding: 6px 12px; font-size: 13px; font-family: inherit;
  border: 1px solid var(--border-color); border-radius: 6px;
  background: var(--bg-surface); color: var(--text-secondary); cursor: pointer;
}
.stats-sort-select:focus { outline: none; border-color: var(--color-brand); }

/* Book list */
.book-list { display: flex; flex-direction: column; gap: 10px; }

/* Card */
.book-card {
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 10px; overflow: hidden; transition: border-color 0.15s;
}
.book-card:hover { border-color: var(--c, var(--color-brand)); }

.card-head { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; cursor: pointer; transition: background 0.1s; }
.card-head:hover { background: var(--bg-surface-hover); }
.card-head-left { flex: 1; min-width: 0; }
.card-head-left h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0; }
.card-summary { font-size: 12px; color: var(--text-muted); }
.card-head-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.btn-expand { width: 24px; height: 24px; border: none; background: transparent; color: var(--text-muted); font-size: 14px; cursor: pointer; border-radius: 4px; display: flex; align-items: center; justify-content: center; }
.btn-expand:hover { background: var(--bg-surface-hover); color: var(--text-primary); }
.card-arrow { font-size: 16px; color: var(--text-muted); transition: transform 0.15s, color 0.15s; }
.card-head:hover .card-arrow { transform: translateX(2px); color: var(--c); }

/* Preview area */
.card-preview { padding: 0 20px 16px; border-top: 1px solid var(--border-color); padding-top: 12px; }

/* Depth tags */
.preview-depth { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
.depth-tag { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.dt-l3 { background: #f0e6ff; color: #7c3aed; }
.dt-l2 { background: #fef3e0; color: #b45309; }
.dt-l1 { background: #e8f0fe; color: #1a56db; }
.dt-l0 { background: var(--bg-surface-hover); color: var(--text-secondary); }
.rel-tag { background: #fce7f3; color: #be185d; }

/* Character chips */
.preview-chars { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.char-chip {
  font-size: 13px; padding: 3px 10px; background: var(--bg-surface-hover);
  border: 1px solid var(--border-color); border-radius: 14px; color: var(--text-primary);
  display: inline-flex; align-items: center; gap: 4px;
}
.char-chip small { font-size: 10px; color: var(--text-muted); }
.char-more { font-size: 12px; color: var(--text-muted); }

/* World preview */
.preview-era { font-size: 13px; color: var(--text-secondary); line-height: 1.5; margin: 0 0 6px; }

/* Outline preview */
.preview-outline { display: flex; flex-direction: column; gap: 3px; margin-bottom: 6px; }
.outline-node { font-size: 13px; padding-left: 12px; border-left: 2px solid var(--border-color); }
.on-volume { color: var(--text-primary); font-weight: 500; }

/* FS preview */
.preview-fs-types, .preview-fs-status { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 6px; }
.fs-type-tag { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; background: #fee2e2; color: #dc2626; }
.fs-status-tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: #d1fae5; color: #065f46; }
.fs-status-tag.fs-recycled { background: #dbeafe; color: #1d4ed8; }
.fs-status-tag.fs-dropped { background: var(--bg-surface-hover); color: var(--text-muted); }
.preview-fs-list { margin: 4px 0 0; padding-left: 18px; font-size: 12px; color: var(--text-secondary); }
.preview-fs-list li { margin-bottom: 2px; }

/* Stats preview */
.preview-stats { display: flex; gap: 18px; flex-wrap: wrap; }
.stat-item { display: flex; flex-direction: column; align-items: center; gap: 1px; }
.stat-val { font-size: 17px; font-weight: 700; color: var(--text-primary); }
.stat-lbl { font-size: 11px; color: var(--text-muted); }

/* Generic */
.preview-meta { font-size: 13px; color: var(--text-muted); }

/* Global view */
.global-view { display: flex; flex-direction: column; gap: 16px; }
.global-group {
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 16px 20px;
}
.global-book { font-size: 15px; font-weight: 600; margin: 0 0 8px; }
.global-chars { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

/* Global character view */
.global-char-summary {
  display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap;
}
.gcs-item {
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 14px 22px; text-align: center; min-width: 90px; flex: 1;
}
.gcs-num { display: block; font-size: 24px; font-weight: 700; color: var(--text-primary); line-height: 1.2; }
.gcs-lbl { font-size: 12px; color: var(--text-muted); }

.global-depth-section { margin-bottom: 18px; }

.depth-heading {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; font-weight: 600; color: var(--text-primary); margin: 0 0 10px;
}
.depth-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.depth-dot.dt-l3 { background: #7c3aed; }
.depth-dot.dt-l2 { background: #b45309; }
.depth-dot.dt-l1 { background: #1a56db; }
.depth-dot.dt-l0 { background: #9ca3af; }
.depth-count { font-size: 12px; font-weight: 400; color: var(--text-muted); }

.global-char-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 8px;
}
.global-char-card {
  display: flex; align-items: center; gap: 10px;
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 8px; padding: 10px 14px; cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.global-char-card:hover {
  border-color: var(--color-brand); box-shadow: 0 1px 6px rgba(0,0,0,0.06);
}

.char-avatar {
  width: 36px; height: 36px; border-radius: 50%;
  border: 2px solid #9ca3af;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; font-weight: 700; flex-shrink: 0;
  background: var(--bg-surface-hover);
}
.avatar-initial { line-height: 1; }

.char-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.char-info .char-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.char-book-tag {
  font-size: 11px; color: var(--text-muted);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.char-meta-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; flex-shrink: 0; }
.char-gender { font-size: 11px; color: var(--text-secondary); }
.char-race { font-size: 10px; color: var(--text-muted); background: var(--bg-surface-hover); padding: 1px 6px; border-radius: 4px; }

/* Foreshadowing global view */
.fs-type-badge { font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 4px; color: #fff; }
.global-fs-list { display: flex; flex-direction: column; gap: 6px; }
.global-fs-card {
  background: var(--bg-surface-hover); border: 1px solid var(--border-color);
  border-radius: 8px; padding: 10px 14px; cursor: pointer;
  transition: border-color 0.15s;
}
.global-fs-card:hover { border-color: var(--color-brand); }
.global-fs-desc { font-size: 13px; color: var(--text-primary); margin: 0 0 6px; line-height: 1.5; }
.global-fs-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.fs-status-sm { font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.fs-status-sm.active { background: #d1fae5; color: #065f46; }
.fs-status-sm.recycled { background: #dbeafe; color: #1d4ed8; }
.fs-status-sm.dropped { background: #f3f4f6; color: #9ca3af; }

@media (max-width: 640px) {
  .hub-header { gap: 10px; }
  .hub-icon { font-size: 28px; }
  .hub-header h1 { font-size: 20px; }
}
</style>
