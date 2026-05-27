<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import {
  getCharacterRelationships, createCharacterRelationship,
  updateCharacterRelationship, deleteCharacterRelationship,
} from '@/api/book'

const props = defineProps({
  characters: { type: Array, required: true },
  bookId: { type: [String, Number], required: true },
})

const emit = defineEmits(['select', 'refresh'])

// ── Fetch dedicated relationships ──
const apiRelationships = ref([])
const loadingRels = ref(false)

async function loadRelationships() {
  loadingRels.value = true
  try {
    const res = await getCharacterRelationships(props.bookId)
    apiRelationships.value = res?.data || []
  } catch { apiRelationships.value = [] }
  finally { loadingRels.value = false }
}

watch(() => props.bookId, loadRelationships, { immediate: true })

// ── Merge relationships from both sources ──
function parseRelations(extraAttributes) {
  try {
    const ea = typeof extraAttributes === 'string' ? JSON.parse(extraAttributes) : (extraAttributes || {})
    return ea.relationships || []
  } catch { return [] }
}

const graph = computed(() => {
  const nodes = props.characters.map(c => ({
    id: c.id,
    name: c.name,
    gender: c.gender,
    identity: c.identity,
    depthLevel: c.depth_level,
  }))

  const edges = []
  const seen = new Set()

  // Dedicated API relationships (preferred)
  for (const r of apiRelationships.value) {
    const sid = r.source_character?.id || r.sourceCharacter?.id || r.source?.id || r.source
    const tid = r.target_character?.id || r.targetCharacter?.id || r.target?.id || r.target
    if (!sid || !tid) continue
    const key = [sid, tid].sort().join('-')
    if (seen.has(key)) continue
    seen.add(key)
    if (!props.characters.find(x => x.id === sid)) continue
    if (!props.characters.find(x => x.id === tid)) continue
    edges.push({
      id: r.id,
      source: sid,
      target: tid,
      type: r.relationship_type || r.relationshipType || r.type || '关联',
      description: r.description || '',
      intimacy: r.intimacy_level || r.intimacyLevel || r.intimacy || 1,
      fromApi: true,
    })
  }

  // Fallback: embedded relationships from extra_attributes
  for (const c of props.characters) {
    const rels = parseRelations(c.extra_attributes)
    for (const r of rels) {
      const key = [c.id, r.targetId].sort().join('-')
      if (seen.has(key)) continue
      seen.add(key)
      if (!props.characters.find(x => x.id === r.targetId)) continue
      edges.push({
        source: c.id,
        target: r.targetId,
        type: r.type || '关联',
        description: r.description || '',
        intimacy: 1,
        fromApi: false,
      })
    }
  }

  return { nodes, edges }
})

// ── Circle layout ──
const svgWidth = ref(800)
const svgHeight = ref(600)
const wrapper = ref(null)

const layout = computed(() => {
  const { nodes } = graph.value
  const n = nodes.length
  if (n === 0) return []
  const cx = svgWidth.value / 2
  const cy = svgHeight.value / 2
  const radius = Math.min(cx, cy) - 80
  return nodes.map((node, i) => {
    const angle = (2 * Math.PI * i) / n - Math.PI / 2
    return { ...node, x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) }
  })
})

const nodeMap = computed(() => {
  const m = {}
  for (const n of layout.value) m[n.id] = n
  return m
})

const edgeLines = computed(() => {
  return graph.value.edges.map(e => {
    const s = nodeMap.value[e.source]
    const t = nodeMap.value[e.target]
    if (!s || !t) return null
    const dx = t.x - s.x
    const dy = t.y - s.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    const curvature = Math.min(dist * 0.3, 40)
    const mx = (s.x + t.x) / 2
    const my = (s.y + t.y) / 2 - curvature
    return { ...e, d: `M${s.x},${s.y} Q${mx},${my} ${t.x},${t.y}` }
  }).filter(Boolean)
})

function genderColor(g) {
  if (g === '男') return '#3b82f6'
  if (g === '女') return '#ec4899'
  return '#6b7280'
}

// ── Relationship CRUD ──
const showForm = ref(false)
const formMode = ref('create') // 'create' | 'edit'
const editingRel = ref(null)
const relForm = ref({
  sourceCharacterId: null,
  targetCharacterId: null,
  relationshipType: '',
  description: '',
  intimacyLevel: 1,
})
const relSaving = ref(false)
const hoveredRel = ref(null)
const tooltipPos = ref({ x: 0, y: 0 })

function openCreateRel() {
  formMode.value = 'create'
  relForm.value = { sourceCharacterId: null, targetCharacterId: null, relationshipType: '', description: '', intimacyLevel: 1 }
  showForm.value = true
}

function openEditRel(edge) {
  if (!edge.fromApi) return // can only edit API relationships
  formMode.value = 'edit'
  editingRel.value = edge
  relForm.value = {
    sourceCharacterId: edge.source,
    targetCharacterId: edge.target,
    relationshipType: edge.type,
    description: edge.description,
    intimacyLevel: edge.intimacy || 1,
  }
  showForm.value = true
}

async function handleRelSubmit() {
  if (!relForm.value.sourceCharacterId || !relForm.value.targetCharacterId || !relForm.value.relationshipType) return
  relSaving.value = true
  try {
    const payload = {
      source_character: { id: relForm.value.sourceCharacterId },
      target_character: { id: relForm.value.targetCharacterId },
      relationship_type: relForm.value.relationshipType,
      description: relForm.value.description,
      intimacy_level: relForm.value.intimacyLevel,
    }
    if (formMode.value === 'edit' && editingRel.value?.id) {
      await updateCharacterRelationship(props.bookId, editingRel.value.id, payload)
    } else {
      await createCharacterRelationship(props.bookId, payload)
    }
    showForm.value = false
    await loadRelationships()
    emit('refresh')
  } catch (e) {
    // error handled by API interceptor
  } finally {
    relSaving.value = false
  }
}

async function handleDeleteRel(edge) {
  if (!edge.fromApi || !edge.id) return
  try {
    await deleteCharacterRelationship(props.bookId, edge.id)
    await loadRelationships()
    emit('refresh')
  } catch { /* ignore */ }
}

function handleEdgeHover(edge, e) {
  hoveredRel.value = edge
  tooltipPos.value = { x: e.clientX, y: e.clientY }
}

// ── Responsive ──
function onResize() {
  if (wrapper.value) {
    svgWidth.value = Math.max(600, wrapper.value.clientWidth)
    svgHeight.value = Math.max(400, window.innerHeight * 0.55)
  }
}

onMounted(() => { onResize(); window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize) })

const REL_TYPES = ['朋友', '恋人', '家人', '师徒', '仇敌', '盟友', '上下级', '青梅竹马', '暗恋', '对手', '其他']
</script>

<template>
  <div ref="wrapper" class="cgraph-wrapper">
    <!-- Toolbar -->
    <div class="cgraph-toolbar">
      <span v-if="graph.edges.length" class="cgraph-count">{{ graph.nodes.length }} 角色 · {{ graph.edges.length }} 关系</span>
      <button class="cg-btn-add" @click="openCreateRel">+ 添加关系</button>
    </div>

    <div v-if="graph.nodes.length === 0" class="cgraph-empty">
      暂无角色，请先创建角色
    </div>

    <svg v-else :viewBox="`0 0 ${svgWidth} ${svgHeight}`" class="cgraph-svg">
      <!-- Edges -->
      <g class="cgraph-edges">
        <path
          v-for="(e, i) in edgeLines"
          :key="'e'+i"
          :d="e.d"
          fill="none"
          :stroke="e.fromApi ? '#c4b5fd' : '#d1d5db'"
          :stroke-width="e.fromApi ? 2 : 1.5"
          :stroke-dasharray="e.fromApi ? 'none' : '6,3'"
          class="cg-edge"
          :class="{ 'from-api': e.fromApi }"
          @mouseenter="handleEdgeHover(e, $event)"
          @mouseleave="hoveredRel = null"
          @click="e.fromApi ? openEditRel(e) : null"
          style="cursor: pointer"
        />
      </g>

      <!-- Edge labels -->
      <g class="cgraph-edge-labels">
        <text
          v-for="(e, i) in edgeLines"
          :key="'el'+i"
          :x="(nodeMap[e.source].x + nodeMap[e.target].x) / 2"
          :y="(nodeMap[e.source].y + nodeMap[e.target].y) / 2 - 10"
          text-anchor="middle"
          class="edge-label"
          :fill="e.fromApi ? '#7c3aed' : '#9ca3af'"
        >{{ e.type }}</text>
      </g>

      <!-- Nodes -->
      <g class="cgraph-nodes">
        <g
          v-for="node in layout"
          :key="node.id"
          class="cgraph-node"
          :transform="`translate(${node.x},${node.y})`"
          @click="emit('select', node.id)"
        >
          <circle
            r="30"
            :fill="genderColor(node.gender)"
            fill-opacity="0.12"
            :stroke="genderColor(node.gender)"
            stroke-width="2.5"
          />
          <text y="4" text-anchor="middle" class="node-name" :fill="genderColor(node.gender)">
            {{ node.name.length > 4 ? node.name.slice(0, 4) + '…' : node.name }}
          </text>
          <text v-if="node.identity" y="48" text-anchor="middle" class="node-identity">
            {{ node.identity.length > 8 ? node.identity.slice(0, 8) + '…' : node.identity }}
          </text>
        </g>
      </g>
    </svg>

    <!-- Edge tooltip -->
    <Teleport to="body">
      <div
        v-if="hoveredRel"
        class="cg-tooltip"
        :style="{ left: tooltipPos.x + 12 + 'px', top: tooltipPos.y - 10 + 'px' }"
      >
        <div class="cg-tooltip-type">{{ hoveredRel.type }}</div>
        <div v-if="hoveredRel.description" class="cg-tooltip-desc">{{ hoveredRel.description }}</div>
        <div v-if="!hoveredRel.fromApi" class="cg-tooltip-hint">（来自角色属性，迁移后可管理）</div>
        <div v-if="hoveredRel.fromApi" class="cg-tooltip-actions">
          <button class="cg-tooltip-btn" @click="openEditRel(hoveredRel); hoveredRel = null">编辑</button>
          <button class="cg-tooltip-btn cg-tooltip-del" @click="handleDeleteRel(hoveredRel); hoveredRel = null">删除</button>
        </div>
      </div>
    </Teleport>

    <!-- Legend -->
    <div v-if="graph.nodes.length > 0" class="cgraph-legend">
      <span class="legend-item"><span class="legend-dot" style="background:#3b82f6"></span> 男</span>
      <span class="legend-item"><span class="legend-dot" style="background:#ec4899"></span> 女</span>
      <span class="legend-item"><span class="legend-dot" style="background:#6b7280"></span> 其他</span>
      <span class="legend-sep">|</span>
      <span class="legend-item"><span class="legend-line api"></span> 正式关系</span>
      <span class="legend-item"><span class="legend-line legacy"></span> 旧关系</span>
      <span class="legend-sep">|</span>
      <span class="legend-item">点击角色编辑 · 点击关系线管理</span>
    </div>

    <!-- Add/Edit relationship modal -->
    <div v-if="showForm" class="cg-modal-overlay" @click.self="showForm = false">
      <div class="cg-modal">
        <h3>{{ formMode === 'edit' ? '编辑关系' : '添加角色关系' }}</h3>
        <div class="cg-form-row">
          <label>角色A</label>
          <select v-model="relForm.sourceCharacterId" class="cg-select">
            <option :value="null">选择角色...</option>
            <option v-for="c in props.characters" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="cg-form-row">
          <label>关系</label>
          <select v-model="relForm.relationshipType" class="cg-select">
            <option value="">选择关系类型...</option>
            <option v-for="t in REL_TYPES" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="cg-form-row">
          <label>角色B</label>
          <select v-model="relForm.targetCharacterId" class="cg-select">
            <option :value="null">选择角色...</option>
            <option v-for="c in props.characters" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="cg-form-row">
          <label>亲密度 (1-5)</label>
          <input type="range" min="1" max="5" v-model.number="relForm.intimacyLevel" class="cg-range" />
          <span class="cg-intimacy-val">{{ relForm.intimacyLevel }}</span>
        </div>
        <div class="cg-form-row">
          <label>描述</label>
          <textarea v-model="relForm.description" rows="2" class="cg-textarea" placeholder="可选：描述两人之间的关系细节..."></textarea>
        </div>
        <div class="cg-form-actions">
          <button class="cg-btn-cancel" @click="showForm = false">取消</button>
          <button class="cg-btn-save" :disabled="!relForm.sourceCharacterId || !relForm.targetCharacterId || !relForm.relationshipType || relSaving" @click="handleRelSubmit">
            {{ relSaving ? '保存中...' : formMode === 'edit' ? '更新' : '添加' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cgraph-wrapper {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  position: relative;
}

.cgraph-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.cgraph-count { font-size: 13px; color: var(--text-secondary); }
.cg-btn-add {
  padding: 5px 14px; font-size: 13px; font-weight: 500;
  background: #7c3aed; color: #fff;
  border: none; border-radius: 6px; cursor: pointer; font-family: inherit;
  transition: background 0.15s;
}
.cg-btn-add:hover { background: #6d28d9; }

.cgraph-svg { width: 100%; height: auto; display: block; }

.cgraph-node { cursor: pointer; transition: filter 0.15s; }
.cgraph-node:hover { filter: brightness(0.85); }

.cg-edge { transition: stroke-width 0.15s; }
.cg-edge:hover { stroke-width: 3.5 !important; }

.node-name { font-size: 13px; font-weight: 700; font-family: inherit; }
.node-identity { font-size: 10px; fill: var(--text-muted); font-family: inherit; }

.edge-label { font-size: 10px; font-weight: 500; font-family: inherit; }

/* Tooltip */
.cg-tooltip {
  position: fixed; z-index: 9999;
  background: #1f2937; color: #f9fafb;
  padding: 10px 14px; border-radius: 8px;
  font-size: 13px; max-width: 260px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
  pointer-events: auto;
}
.cg-tooltip-type { font-weight: 600; margin-bottom: 4px; }
.cg-tooltip-desc { font-size: 12px; color: #d1d5db; line-height: 1.5; }
.cg-tooltip-hint { font-size: 11px; color: #9ca3af; margin-top: 6px; font-style: italic; }
.cg-tooltip-actions { display: flex; gap: 6px; margin-top: 8px; }
.cg-tooltip-btn {
  padding: 3px 10px; font-size: 11px; font-weight: 500;
  background: #374151; color: #e5e7eb; border: none; border-radius: 4px;
  cursor: pointer; font-family: inherit;
}
.cg-tooltip-btn:hover { background: #4b5563; }
.cg-tooltip-del { color: #fca5a5; }
.cg-tooltip-del:hover { background: #7f1d1d; color: #fecaca; }

/* Legend */
.cgraph-legend {
  display: flex; align-items: center; justify-content: center;
  gap: 12px; margin-top: 14px; font-size: 12px; color: var(--text-muted);
  flex-wrap: wrap;
}
.legend-dot {
  display: inline-block; width: 8px; height: 8px;
  border-radius: 50%; margin-right: 4px;
}
.legend-line {
  display: inline-block; width: 20px; height: 0; margin-right: 4px;
  vertical-align: middle;
}
.legend-line.api { border-top: 2px solid #c4b5fd; }
.legend-line.legacy { border-top: 1.5px dashed #d1d5db; }
.legend-sep { color: var(--border-color); }

.cgraph-empty { text-align: center; padding: 60px 20px; color: var(--text-muted); font-size: 14px; }

/* Modal */
.cg-modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  z-index: 300; display: flex; align-items: center; justify-content: center;
}
.cg-modal {
  background: var(--bg-primary, #fff);
  border-radius: 12px; padding: 24px;
  width: 420px; max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.cg-modal h3 { margin: 0 0 20px; font-size: 17px; font-weight: 600; color: var(--text-primary); }
.cg-form-row {
  display: flex; align-items: center; gap: 10px; margin-bottom: 14px;
}
.cg-form-row label {
  width: 60px; flex-shrink: 0; font-size: 13px; font-weight: 500; color: var(--text-secondary);
}
.cg-select {
  flex: 1; padding: 7px 10px; font-size: 13px;
  border: 1px solid var(--border-input); border-radius: 6px;
  background: var(--bg-surface); color: var(--text-primary);
  font-family: inherit; cursor: pointer;
}
.cg-range { flex: 1; accent-color: #7c3aed; }
.cg-intimacy-val { font-size: 13px; font-weight: 600; color: #7c3aed; min-width: 20px; }
.cg-textarea {
  flex: 1; padding: 8px 10px; font-size: 13px; line-height: 1.5;
  border: 1px solid var(--border-input); border-radius: 6px;
  font-family: inherit; resize: vertical;
  background: var(--bg-surface); color: var(--text-primary);
}
.cg-form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.cg-btn-cancel {
  padding: 7px 18px; font-size: 13px; font-weight: 500;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid var(--border-color); border-radius: 6px;
  cursor: pointer; font-family: inherit;
}
.cg-btn-save {
  padding: 7px 18px; font-size: 13px; font-weight: 600;
  background: #7c3aed; color: #fff; border: none; border-radius: 6px;
  cursor: pointer; font-family: inherit; transition: background 0.15s;
}
.cg-btn-save:hover:not(:disabled) { background: #6d28d9; }
.cg-btn-save:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
