<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  characters: { type: Array, required: true },
})

const emit = defineEmits(['select'])

// ── Parse relationships from character.extra_attributes ──
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

  for (const c of props.characters) {
    const rels = parseRelations(c.extra_attributes)
    for (const r of rels) {
      // dedupe (A→B same as B→A)
      const key = [c.id, r.targetId].sort().join('-')
      if (seen.has(key)) continue
      seen.add(key)

      // verify target exists
      if (!props.characters.find(x => x.id === r.targetId)) continue

      edges.push({
        source: c.id,
        target: r.targetId,
        type: r.type || '关联',
        description: r.description || '',
      })
    }
  }

  return { nodes, edges }
})

function parseRelations(extraAttributes) {
  try {
    const ea = typeof extraAttributes === 'string'
      ? JSON.parse(extraAttributes)
      : (extraAttributes || {})
    return ea.relationships || []
  } catch { return [] }
}

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
    return {
      ...node,
      x: cx + radius * Math.cos(angle),
      y: cy + radius * Math.sin(angle),
    }
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
    return {
      ...e,
      d: `M${s.x},${s.y} Q${mx},${my} ${t.x},${t.y}`,
    }
  }).filter(Boolean)
})

// Gender color
function genderColor(g) {
  if (g === '男') return '#3b82f6'
  if (g === '女') return '#ec4899'
  return '#6b7280'
}

function handleNodeClick(node) {
  emit('select', node.id)
}

// Responsive
function onResize() {
  if (wrapper.value) {
    svgWidth.value = Math.max(600, wrapper.value.clientWidth)
    svgHeight.value = Math.max(400, window.innerHeight * 0.6)
  }
}

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div ref="wrapper" class="cgraph-wrapper">
    <div v-if="graph.nodes.length === 0" class="cgraph-empty">
      暂无角色，请先创建角色并设置关系
    </div>

    <svg
      v-else
      :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
      class="cgraph-svg"
    >
      <!-- Edges -->
      <g class="cgraph-edges">
        <path
          v-for="(e, i) in edgeLines"
          :key="'e'+i"
          :d="e.d"
          fill="none"
          stroke="#d1d5db"
          stroke-width="1.5"
          stroke-dasharray="6,3"
        />
      </g>

      <!-- Edge labels -->
      <g class="cgraph-edge-labels">
        <text
          v-for="(e, i) in edgeLines"
          :key="'el'+i"
          :x="(nodeMap[e.source].x + nodeMap[e.target].x) / 2"
          :y="(nodeMap[e.source].y + nodeMap[e.target].y) / 2 - 8"
          text-anchor="middle"
          class="edge-label"
        >{{ e.type }}</text>
      </g>

      <!-- Nodes -->
      <g class="cgraph-nodes">
        <g
          v-for="node in layout"
          :key="node.id"
          class="cgraph-node"
          :transform="`translate(${node.x},${node.y})`"
          @click="handleNodeClick(node)"
        >
          <!-- Circle -->
          <circle
            r="28"
            :fill="genderColor(node.gender)"
            fill-opacity="0.15"
            :stroke="genderColor(node.gender)"
            stroke-width="2"
          />
          <!-- Name -->
          <text
            y="4"
            text-anchor="middle"
            class="node-name"
            :fill="genderColor(node.gender)"
          >{{ node.name.length > 4 ? node.name.slice(0, 4) + '…' : node.name }}</text>
          <!-- Identity below -->
          <text
            v-if="node.identity"
            y="44"
            text-anchor="middle"
            class="node-identity"
          >{{ node.identity.length > 8 ? node.identity.slice(0, 8) + '…' : node.identity }}</text>
          <!-- Depth level badge -->
          <text
            x="22"
            y="-22"
            text-anchor="middle"
            class="node-depth"
          >{{ node.depthLevel }}</text>
        </g>
      </g>
    </svg>

    <div v-if="graph.nodes.length > 0" class="cgraph-legend">
      <span class="legend-item"><span class="legend-dot" style="background:#3b82f6"></span> 男</span>
      <span class="legend-item"><span class="legend-dot" style="background:#ec4899"></span> 女</span>
      <span class="legend-item"><span class="legend-dot" style="background:#6b7280"></span> 其他</span>
      <span class="legend-sep">|</span>
      <span class="legend-item">点击角色可编辑</span>
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

.cgraph-svg {
  width: 100%;
  height: auto;
  display: block;
}

.cgraph-node {
  cursor: pointer;
  transition: transform 0.15s;
}

.cgraph-node:hover {
  filter: brightness(0.9);
}

.node-name {
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
}

.node-identity {
  font-size: 10px;
  fill: var(--text-muted);
  font-family: inherit;
}

.node-depth {
  font-size: 9px;
  font-weight: 600;
  fill: var(--text-muted);
  font-family: inherit;
}

.edge-label {
  font-size: 10px;
  fill: var(--text-muted);
  font-family: inherit;
}

.cgraph-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.legend-sep {
  color: var(--border-color);
}

.cgraph-empty {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
