<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { NODE_TYPE, NODE_COLOR } from '@/utils/labels'

const props = defineProps({
  tree: { type: Array, required: true },
  bookId: { type: [String, Number], required: true },
  collapsedNodes: { type: Set, default: () => new Set() },
})

const emit = defineEmits(['navigate', 'toggle-collapse'])

// ── Layout engine ──

// Grouped columns: each column is array of groups, each group = { nodes: [...] }
// Group 0 = first volume's children, group 1 = second volume's children, etc.
const columns = computed(() => {
  const groups = [[], [], []] // groups[depth] = array of { nodes: [] }
  flattenLevel(props.tree, 0, groups, 0)
  return groups
})

function flattenLevel(nodes, depth, groups) {
  if (depth >= 3) return
  groups[depth].push([])
  const currentGroup = groups[depth][groups[depth].length - 1]
  for (const node of nodes) {
    currentGroup.push(node)
    if (node.children && node.children.length && !props.collapsedNodes.has(node.id)) {
      flattenLevel(node.children, depth + 1, groups)
    }
  }
}

// Branch color palette — assigns distinct color per volume branch
const branchColors = ['#8b5cf6', '#3b82f6', '#f59e0b', '#10b981', '#ec4899', '#6366f1']

// Calculate SVG connector lines with level info for visual hierarchy
function getConnections() {
  const lines = []
  props.tree.forEach((vol, volIdx) => {
    const branchColor = branchColors[volIdx % branchColors.length]
    const volCollapsed = props.collapsedNodes.has(vol.id)
    if (vol.children && vol.children.length && !volCollapsed) {
      for (const ch of vol.children) {
        lines.push({
          from: `n${vol.id}`,
          to: `n${ch.id}`,
          level: 0,
          stroke: branchColor,
        })
        if (ch.children && ch.children.length && !props.collapsedNodes.has(ch.id)) {
          for (const sc of ch.children) {
            lines.push({
              from: `n${ch.id}`,
              to: `n${sc.id}`,
              level: 1,
              stroke: branchColor,
            })
          }
        }
      }
    }
  })
  return lines
}

const svgLines = ref([])

function recalcLines() {
  const result = []
  for (const conn of getConnections()) {
    const fromEl = document.getElementById(conn.from)
    const toEl = document.getElementById(conn.to)
    if (!fromEl || !toEl) continue
    const svg = document.getElementById('mindmap-svg')
    if (!svg) continue
    const svgRect = svg.getBoundingClientRect()

    const fromRect = fromEl.getBoundingClientRect()
    const toRect = toEl.getBoundingClientRect()

    const x1 = fromRect.right - svgRect.left
    const y1 = fromRect.top + fromRect.height / 2 - svgRect.top
    const x2 = toRect.left - svgRect.left
    const y2 = toRect.top + toRect.height / 2 - svgRect.top

    const midX = (x1 + x2) / 2

    result.push({
      d: `M${x1},${y1} C${midX},${y1} ${midX},${y2} ${x2},${y2}`,
      stroke: conn.stroke,
      level: conn.level,
    })
  }
  svgLines.value = result
}

let resizeTimer = null
function scheduleRecalc() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(recalcLines, 50)
}

onMounted(() => {
  // Wait for DOM to settle
  setTimeout(recalcLines, 100)
  window.addEventListener('resize', scheduleRecalc)
})

onUnmounted(() => {
  window.removeEventListener('resize', scheduleRecalc)
  if (resizeTimer) clearTimeout(resizeTimer)
})

// Recalculate when tree changes
watch(() => props.tree, () => {
  setTimeout(recalcLines, 150)
}, { deep: true })

watch(() => props.collapsedNodes, () => {
  setTimeout(recalcLines, 200)
}, { deep: true })

// ── Interactions ──

function handleNodeClick(node) {
  if (node.node_type === 'VOLUME' || (node.children && node.children.length > 0)) {
    emit('toggle-collapse', node.id)
  } else if (node.node_type === 'CHAPTER' || node.node_type === 'SCENE') {
    emit('navigate', node)
  }
}

function getNodeUrl(node) {
  return `/books/${props.bookId}/write/${node.id}?title=${encodeURIComponent(node.title)}&node_type=${node.node_type}`
}
</script>

<template>
  <div class="mindmap-wrapper">
    <svg id="mindmap-svg" class="mindmap-svg">
      <defs>
        <marker id="arrow-vol" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" fill="#c4b5fd" />
        </marker>
        <marker id="arrow-ch" markerWidth="5" markerHeight="5" refX="4" refY="2.5" orient="auto">
          <path d="M0,0 L5,2.5 L0,5 Z" fill="#93c5fd" />
        </marker>
      </defs>
      <path
        v-for="(line, i) in svgLines"
        :key="i"
        :d="line.d"
        :stroke="line.stroke"
        :stroke-width="line.level === 0 ? 2.5 : 1.6"
        fill="none"
        stroke-linecap="round"
        :stroke-opacity="line.level === 0 ? 0.55 : 0.4"
        :marker-end="line.level === 0 ? 'url(#arrow-vol)' : 'url(#arrow-ch)'"
      />
    </svg>

    <div class="mindmap-columns">
      <!-- Column 0: 卷 -->
      <div v-if="columns[0].length" class="mindmap-col">
        <div class="col-header" :style="{ color: NODE_COLOR.VOLUME }">卷</div>
        <template v-for="(group, gi) in columns[0]" :key="'g0-'+gi">
          <div
            v-for="node in group"
            :id="`n${node.id}`"
            :key="node.id"
            class="mindmap-node"
            :class="{ clickable: node.children && node.children.length }"
            :data-node-type="node.node_type"
            :style="{ '--node-color': NODE_COLOR[node.node_type] }"
            @click="handleNodeClick(node)"
          >
            <div class="node-chip">
              <span v-if="node.children && node.children.length" class="node-collapse-icon">{{ collapsedNodes.has(node.id) ? '▸' : '▾' }}</span>
              <span class="node-type-tag" :style="{ background: NODE_COLOR[node.node_type] }">
                {{ NODE_TYPE[node.node_type] }}
              </span>
              <span class="node-label">{{ node.title }}</span>
            </div>
            <div v-if="node.writing_goal" class="node-goal">{{ node.writing_goal }}</div>
          </div>
        </template>
      </div>

      <!-- Column 1: 章 (grouped by volume) -->
      <div v-if="columns[1].length" class="mindmap-col">
        <div class="col-header" :style="{ color: NODE_COLOR.CHAPTER }">章</div>
        <template v-for="(group, gi) in columns[1]" :key="'g1-'+gi">
          <div v-if="gi > 0 && group.length" class="group-separator"></div>
          <div
            v-for="node in group"
            :id="`n${node.id}`"
            :key="node.id"
            class="mindmap-node clickable"
            :data-node-type="node.node_type"
            :style="{ '--node-color': NODE_COLOR[node.node_type] }"
            @click="handleNodeClick(node)"
          >
            <div class="node-chip">
              <span v-if="node.children && node.children.length" class="node-collapse-icon">{{ collapsedNodes.has(node.id) ? '▸' : '▾' }}</span>
              <span class="node-type-tag" :style="{ background: NODE_COLOR[node.node_type] }">
                {{ NODE_TYPE[node.node_type] }}
              </span>
              <span class="node-label">{{ node.title }}</span>
            </div>
            <router-link
              :to="getNodeUrl(node)"
              class="node-write-link"
              @click.stop
            >写正文</router-link>
          </div>
        </template>
      </div>

      <!-- Column 2: 节 (grouped by chapter) -->
      <div v-if="columns[2].length" class="mindmap-col">
        <div class="col-header" :style="{ color: NODE_COLOR.SCENE }">节</div>
        <template v-for="(group, gi) in columns[2]" :key="'g2-'+gi">
          <div v-if="gi > 0 && group.length" class="group-separator"></div>
          <div
            v-for="node in group"
            :id="`n${node.id}`"
            :key="node.id"
            class="mindmap-node clickable"
            :data-node-type="node.node_type"
            :style="{ '--node-color': NODE_COLOR[node.node_type] }"
            @click="handleNodeClick(node)"
          >
            <div class="node-chip">
              <span class="node-type-tag scene-tag" :style="{ background: NODE_COLOR[node.node_type] }">
                {{ NODE_TYPE[node.node_type] }}
              </span>
              <span class="node-label">{{ node.title }}</span>
            </div>
            <router-link
              :to="getNodeUrl(node)"
              class="node-write-link"
              @click.stop
            >写正文</router-link>
          </div>
        </template>
      </div>

      <!-- Empty -->
      <div v-if="!tree.length" class="mindmap-empty">
        暂无大纲节点，请先在列表视图中创建
      </div>
    </div>
  </div>
</template>

<style scoped>
.mindmap-wrapper {
  position: relative;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 24px 16px 32px;
  min-height: 300px;
}

.mindmap-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.mindmap-columns {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 48px;
  min-width: max-content;
}

.mindmap-col {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 200px;
  max-width: 280px;
}

.col-header {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 0 12px;
  margin-bottom: 4px;
}

.mindmap-node {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-left: 3px solid var(--node-color, #d1d5db);
  border-radius: 8px;
  padding: 12px 14px;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
}

.mindmap-node.clickable {
  cursor: pointer;
}

.mindmap-node.clickable:hover {
  border-color: var(--node-color, #5b3cc4);
  box-shadow: 0 2px 12px rgba(91, 60, 196, 0.08);
  transform: translateY(-1px);
}

.node-chip {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-collapse-icon {
  font-size: 10px;
  color: var(--text-muted);
  flex-shrink: 0;
  width: 12px;
  text-align: center;
}

.node-type-tag {
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
  line-height: 1.4;
}

.scene-tag {
  font-size: 10px;
}

.node-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
  word-break: break-word;
}

.node-goal {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.node-write-link {
  display: inline-block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--color-brand);
  text-decoration: none;
  font-weight: 500;
  padding: 2px 10px;
  border: 1px solid var(--color-brand);
  border-radius: 4px;
  transition: background 0.12s, color 0.12s;
}

.node-write-link:hover {
  background: var(--color-brand);
  color: #fff;
}

.group-separator {
  height: 16px;
  border-bottom: 1px dashed var(--border-color);
  margin-bottom: 4px;
  opacity: 0.5;
}

.mindmap-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
