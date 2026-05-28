<script setup>
import { ref, watch, nextTick, computed } from 'vue'

const props = defineProps({
  visible: Boolean,
  mode: String,
  loading: Boolean,
  selectedText: String,
  result: String,
  position: Object,
  modes: Array,
  annotateComment: String,
  annotateCategory: String,
  annotateSeverity: String,
  annotating: Boolean,
})

const emit = defineEmits([
  'close',
  'select-mode',
  'accept',
  'submit-annotation',
  'update:annotateComment',
  'update:annotateCategory',
  'update:annotateSeverity',
])

// Drag state (self-contained)
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

function onDragStart(e) {
  if (e.target.closest('.inline-close') || e.target.closest('button') || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return
  isDragging.value = true
  dragOffset.value = { x: e.clientX - props.position.left, y: e.clientY - props.position.top }
}
function onDragMove(e) {
  if (!isDragging.value) return
  props.position.top = Math.max(0, e.clientY - dragOffset.value.y)
  props.position.left = Math.max(0, e.clientX - dragOffset.value.x)
}
function onDragEnd() { isDragging.value = false }

function modeLabel(value) {
  return props.modes.find(m => m.value === value)?.label || ''
}

// Character-level diff for Chinese text
const diffSegments = computed(() => {
  const a = props.selectedText || ''
  const b = props.result || ''
  if (!a || !b) return [{ type: 'same', text: a || b }]
  const m = a.length, n = b.length
  const dp = Array.from({ length: m + 1 }, () => new Uint16Array(n + 1))
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }
  // Backtrack
  const segments = []
  let i = m, j = n
  const buf = { same: '', add: '', remove: '' }
  function flush(type) {
    if (buf[type]) { segments.unshift({ type, text: buf[type] }); buf[type] = '' }
  }
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i - 1] === b[j - 1]) {
      flush('add'); flush('remove')
      buf.same = a[i - 1] + buf.same
      i--; j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      flush('remove')
      buf.add = b[j - 1] + buf.add
      j--
    } else {
      flush('add')
      buf.remove = a[i - 1] + buf.remove
      i--
    }
  }
  flush('add'); flush('remove'); flush('same')
  return segments.filter(s => s.text)
})

const popupEl = ref(null)

watch(() => props.visible, async (v) => {
  if (!v) return
  await nextTick()
  const el = popupEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const margin = 16
  if (rect.bottom > window.innerHeight - margin) {
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  } else if (rect.top < margin) {
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
})
</script>

<template>
  <div
    v-if="visible"
    ref="popupEl"
    class="inline-popup"
    :class="{ dragging: isDragging }"
    :style="{ top: position.top + 'px', left: position.left + 'px' }"
    @mousedown="onDragStart"
    @mousemove="onDragMove"
    @mouseup="onDragEnd"
    @mouseleave="onDragEnd"
  >
    <div v-if="!mode" class="inline-actions">
      <button v-for="m in modes.filter(m => !m._internal)" :key="m.value" class="inline-btn" @click="$emit('select-mode', m.value)">{{ m.icon }} {{ m.label }}</button>
      <button class="inline-close" @click="$emit('close')">&times;</button>
    </div>
    <div v-else-if="mode === 'annotate'" class="inline-annotate">
      <div class="inline-annotate-header">
        <span>💬 添加批注</span>
        <span class="inline-selected-preview">{{ selectedText }}</span>
      </div>
      <textarea :value="annotateComment" class="inline-annotate-input" rows="3" placeholder="写下你的备注..." @input="$emit('update:annotateComment', $event.target.value)"></textarea>
      <div class="inline-annotate-meta">
        <select :value="annotateCategory" class="inline-select" @change="$emit('update:annotateCategory', $event.target.value)">
          <option value="suggestion">建议</option>
          <option value="grammar">语法</option>
          <option value="style">文风</option>
          <option value="plot_hole">剧情漏洞</option>
          <option value="character">角色</option>
        </select>
        <select :value="annotateSeverity" class="inline-select" @change="$emit('update:annotateSeverity', $event.target.value)">
          <option value="INFO">提示</option>
          <option value="MINOR">轻微</option>
          <option value="MAJOR">严重</option>
        </select>
      </div>
      <div class="inline-result-actions">
        <button class="btn-apply" :disabled="!annotateComment.trim() || annotating" @click="$emit('submit-annotation')">{{ annotating ? '提交中...' : '提交批注' }}</button>
        <button class="btn-undo" @click="$emit('close')">取消</button>
      </div>
    </div>
    <div v-else class="inline-result">
      <div class="inline-loading" v-if="loading">AI 正在{{ modeLabel(mode) }}...</div>
      <template v-else>
        <div class="inline-diff">
          <div class="inline-diff-view">
            <span v-for="(seg, si) in diffSegments" :key="si" :class="'diff-' + seg.type">{{ seg.text }}</span>
          </div>
        </div>
        <div class="inline-result-actions">
          <button class="btn-apply" @click="$emit('accept')">采纳</button>
          <button class="btn-undo" @click="$emit('close')">拒绝</button>
        </div>
      </template>
      <div class="inline-mode-label">{{ modeLabel(mode) }}</div>
    </div>
  </div>
</template>
