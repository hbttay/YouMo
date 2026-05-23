<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { NODE_TYPE, NODE_COLOR } from '@/utils/labels'

const props = defineProps({
  tree: { type: Array, required: true },
  bookId: { type: [String, Number], required: true },
})

const emit = defineEmits(['navigate'])

// ── Layout engine ──

// Recursively flatten tree into columns (levels)
// column 0 = volumes, column 1 = chapters, column 2 = scenes
const columns = computed(() => {
  const cols = [[], [], []]
  flattenLevel(props.tree, 0, cols)
  return cols
})

function flattenLevel(nodes, depth, cols) {
  if (depth >= 3) return
  for (const node of nodes) {
    cols[depth].push(node)
    if (node.children && node.children.length) {
      flattenLevel(node.children, depth + 1, cols)
    }
  }
}

// Calculate SVG connector lines between parent and its children
function getConnections() {
  const lines = []
  for (const vol of props.tree) {
    if (vol.children && vol.children.length) {
      for (const ch of vol.children) {
        lines.push({
          from: `n${vol.id}`,
          fromSide: 'right',
          to: `n${ch.id}`,
          toSide: 'left',
        })
        if (ch.children && ch.children.length) {
          for (const sc of ch.children) {
            lines.push({
              from: `n${ch.id}`,
              fromSide: 'right',
              to: `n${sc.id}`,
              toSide: 'left',
            })
          }
        }
      }
    }
  }
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
      stroke: NODE_COLOR[getNodeTypeByEl(fromEl)] || '#d1d5db',
    })
  }
  svgLines.value = result
}

function getNodeTypeByEl(el) {
  return el.dataset?.nodeType || 'SCENE'
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

// ── Interactions ──

function handleNodeClick(node) {
  if (node.node_type === 'CHAPTER' || node.node_type === 'SCENE') {
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
      <path
        v-for="(line, i) in svgLines"
        :key="i"
        :d="line.d"
        :stroke="line.stroke"
        stroke-width="2"
        fill="none"
        stroke-linecap="round"
        stroke-opacity="0.4"
      />
    </svg>

    <div class="mindmap-columns">
      <!-- Column 0: 卷 -->
      <div v-if="columns[0].length" class="mindmap-col">
        <div class="col-header" :style="{ color: NODE_COLOR.VOLUME }">卷</div>
        <div
          v-for="node in columns[0]"
          :id="`n${node.id}`"
          :key="node.id"
          class="mindmap-node"
          :data-node-type="node.node_type"
          :style="{ '--node-color': NODE_COLOR[node.node_type] }"
          @click="handleNodeClick(node)"
        >
          <div class="node-chip">
            <span class="node-type-tag" :style="{ background: NODE_COLOR[node.node_type] }">
              {{ NODE_TYPE[node.node_type] }}
            </span>
            <span class="node-label">{{ node.title }}</span>
          </div>
          <div v-if="node.writing_goal" class="node-goal">{{ node.writing_goal }}</div>
        </div>
      </div>

      <!-- Column 1: 章 -->
      <div v-if="columns[1].length" class="mindmap-col">
        <div class="col-header" :style="{ color: NODE_COLOR.CHAPTER }">章</div>
        <div
          v-for="node in columns[1]"
          :id="`n${node.id}`"
          :key="node.id"
          class="mindmap-node clickable"
          :data-node-type="node.node_type"
          :style="{ '--node-color': NODE_COLOR[node.node_type] }"
          @click="handleNodeClick(node)"
        >
          <div class="node-chip">
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
      </div>

      <!-- Column 2: 节 -->
      <div v-if="columns[2].length" class="mindmap-col">
        <div class="col-header" :style="{ color: NODE_COLOR.SCENE }">节</div>
        <div
          v-for="node in columns[2]"
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
      </div>

      <!-- Empty -->
      <div v-if="columns.every(c => !c.length)" class="mindmap-empty">
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
  gap: 40px;
  min-width: max-content;
}

.mindmap-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 180px;
  max-width: 260px;
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

.mindmap-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
