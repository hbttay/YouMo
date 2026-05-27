<script setup>
import { computed } from 'vue'

const props = defineProps({
  suggestions: { type: Array, required: true },
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['accept', 'reject-all', 'close'])

const acceptedCount = computed(() => props.suggestions.filter(s => s.accepted).length)
const totalCount = computed(() => props.suggestions.length)

const typeLabels = {
  polish: '润色',
  expand: '扩写',
  consistency: '纠错',
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="review-overlay" @click.self="emit('close')">
      <div class="review-panel">
        <div class="review-header">
          <h3>AI 审改建议</h3>
          <span class="review-count">已采纳 {{ acceptedCount }}/{{ totalCount }}</span>
          <div class="review-header-actions">
            <button class="btn-accept-all" :disabled="acceptedCount === totalCount" @click="$emit('reject-all')">全部拒绝</button>
            <button class="btn-reject-all" :disabled="acceptedCount === 0" @click="emit('accept')">应用已选</button>
          </div>
          <button class="review-close" @click="emit('close')">&times;</button>
        </div>

        <div class="review-list">
          <div
            v-for="(s, i) in suggestions"
            :key="i"
            class="review-card"
            :class="{ accepted: s.accepted, rejected: s.rejected }"
          >
            <div class="review-card-header">
              <span class="review-type" :class="'type-' + s.type">{{ typeLabels[s.type] || s.type }}</span>
              <span class="review-reason">{{ s.reason }}</span>
              <div class="review-card-actions">
                <button
                  v-if="!s.accepted && !s.rejected"
                  class="btn-accept"
                  @click="s.accepted = true; s.rejected = false"
                >采纳</button>
                <button
                  v-if="!s.accepted && !s.rejected"
                  class="btn-reject"
                  @click="s.accepted = false; s.rejected = true"
                >拒绝</button>
                <span v-if="s.accepted" class="status-accepted">已采纳</span>
                <span v-if="s.rejected" class="status-rejected">已拒绝</span>
              </div>
            </div>
            <div class="review-diff">
              <div class="review-original">{{ s.original }}</div>
              <div class="review-suggested">{{ s.suggested }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.review-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  z-index: 200; display: flex; justify-content: flex-end;
}
.review-panel {
  width: 520px; max-width: 100vw; height: 100vh;
  background: var(--bg-primary, #fff);
  display: flex; flex-direction: column;
  box-shadow: -6px 0 24px rgba(0,0,0,0.12);
}
.review-header {
  display: flex; align-items: center; gap: 12px;
  padding: 16px 24px; border-bottom: 1px solid var(--border-color, #e5e7eb);
  flex-shrink: 0;
}
.review-header h3 { font-size: 16px; font-weight: 600; margin: 0; flex: 1; }
.review-count { font-size: 13px; color: var(--text-secondary); }
.review-header-actions { display: flex; gap: 8px; }
.btn-accept-all, .btn-reject-all {
  padding: 5px 14px; font-size: 12px; font-weight: 500;
  border: 1px solid var(--border-color, #d1d5db);
  border-radius: 6px; cursor: pointer; font-family: inherit;
}
.btn-accept-all { background: #059669; color: #fff; border-color: #059669; }
.btn-accept-all:hover { background: #047857; }
.btn-reject-all { background: var(--bg-surface); color: var(--text-secondary); }
.btn-reject-all:hover { background: var(--bg-surface-hover); }
.btn-accept-all:disabled, .btn-reject-all:disabled { opacity: 0.4; cursor: not-allowed; }
.review-close {
  width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center;
  background: none; border: none; font-size: 18px; color: var(--text-muted);
  cursor: pointer; border-radius: 4px; flex-shrink: 0;
}
.review-close:hover { background: #f3f4f6; color: var(--text-primary); }

.review-list {
  flex: 1; overflow-y: auto; padding: 16px 24px;
  display: flex; flex-direction: column; gap: 16px;
}
.review-card {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 10px; overflow: hidden;
  transition: opacity 0.2s;
}
.review-card.accepted { border-color: #86efac; background: #f0fdf4; }
.review-card.rejected { opacity: 0.4; }
.review-card-header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-bottom: 1px solid #f3f4f6;
}
.review-type {
  font-size: 11px; font-weight: 600; padding: 2px 8px;
  border-radius: 10px; white-space: nowrap;
}
.type-polish { background: #ede9fe; color: #7c3aed; }
.type-expand { background: #dbeafe; color: #2563eb; }
.type-consistency { background: #fef3c7; color: #d97706; }
.review-reason {
  font-size: 12px; color: var(--text-secondary); flex: 1;
}
.review-card-actions { display: flex; gap: 6px; flex-shrink: 0; }
.btn-accept {
  padding: 3px 12px; font-size: 11px; font-weight: 500;
  background: #059669; color: #fff; border: none; border-radius: 4px;
  cursor: pointer; font-family: inherit;
}
.btn-accept:hover { background: #047857; }
.btn-reject {
  padding: 3px 12px; font-size: 11px; font-weight: 500;
  background: var(--bg-surface); color: var(--text-secondary);
  border: 1px solid #d1d5db; border-radius: 4px;
  cursor: pointer; font-family: inherit;
}
.btn-reject:hover { background: var(--bg-surface-hover); }
.status-accepted { font-size: 11px; color: #059669; font-weight: 500; }
.status-rejected { font-size: 11px; color: var(--text-muted); }

.review-diff {
  display: flex; flex-direction: column; gap: 4px; padding: 10px 14px;
}
.review-original {
  padding: 8px 12px; background: #fef2f2; border-radius: 6px;
  font-size: 13px; line-height: 1.6; color: #991b1b;
  white-space: pre-wrap; word-break: break-all;
}
.review-suggested {
  padding: 8px 12px; background: #f0fdf4; border-radius: 6px;
  font-size: 13px; line-height: 1.6; color: #166534;
  white-space: pre-wrap; word-break: break-all;
}
</style>
