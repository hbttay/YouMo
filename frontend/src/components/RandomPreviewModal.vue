<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: Boolean,
  type: String,   // 'book-idea' | 'character' | 'world-setting' | 'outline'
  data: Object,
  loading: Boolean,
})

const emit = defineEmits(['apply', 'draft', 'close'])

const title = computed(() => {
  const map = { 'book-idea': '随机书名/创意', 'character': '随机角色', 'world-setting': '随机世界观', 'outline': '随机大纲' }
  return map[props.type] || '生成结果'
})

function formatOutline(volumes) {
  if (!volumes?.length) return '无'
  return volumes.map(v =>
    `【${v.title}】\n` + (v.chapters || []).map(ch =>
      `  ${ch.title}\n` + (ch.scenes || []).map(sc => `    - ${sc.title}`).join('\n')
    ).join('\n')
  ).join('\n\n')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="emit('close')">
        <div class="modal-panel">
          <div class="modal-header">
            <h2>{{ title }}</h2>
            <button class="modal-close" @click="emit('close')">&times;</button>
          </div>

          <div class="modal-body">
            <div v-if="loading" class="modal-loading">生成中...</div>

            <!-- Book Idea -->
            <template v-else-if="type === 'book-idea'">
              <div class="preview-field"><label>书名</label><p>{{ data?.title || '-' }}</p></div>
              <div class="preview-field"><label>简介</label><p class="preview-text">{{ data?.core_idea || '-' }}</p></div>
              <div class="preview-row">
                <div class="preview-field"><label>模式</label><p>{{ data?.creation_mode === 'DIVERGENT' ? '发散分支' : '线性叙事' }}</p></div>
                <div class="preview-field"><label>篇幅</label><p>{{ {SHORT:'短篇',MEDIUM:'中篇',LONG:'长篇'}[data?.target_length] || data?.target_length || '-' }}</p></div>
              </div>
            </template>

            <!-- Character -->
            <template v-else-if="type === 'character'">
              <div class="preview-field"><label>角色名</label><p>{{ data?.name || '-' }}</p></div>
              <div class="preview-row">
                <div class="preview-field"><label>性别</label><p>{{ data?.gender || '-' }}</p></div>
                <div class="preview-field"><label>深度</label><p>{{ {L0:'背景板',L1:'配角',L2:'重要配角',L3:'主角'}[data?.depth_level] || data?.depth_level || '-' }}</p></div>
              </div>
              <div class="preview-field"><label>年龄</label><p>{{ data?.age_description || '-' }}</p></div>
              <div class="preview-field"><label>身份</label><p>{{ data?.identity || '-' }}</p></div>
              <div class="preview-field"><label>外貌</label><p class="preview-text">{{ data?.appearance || '-' }}</p></div>
              <div class="preview-field"><label>出身</label><p class="preview-text">{{ data?.origin || '-' }}</p></div>
            </template>

            <!-- World Setting -->
            <template v-else-if="type === 'world-setting'">
              <div class="preview-field"><label>时代</label><p>{{ data?.era || '-' }}</p></div>
              <div class="preview-field"><label>地理</label><p class="preview-text">{{ data?.geography || '-' }}</p></div>
              <div class="preview-field"><label>历史</label><p class="preview-text">{{ data?.history_events || '-' }}</p></div>
              <div class="preview-row">
                <div class="preview-field"><label>政治</label><p class="preview-text">{{ data?.politics || '-' }}</p></div>
                <div class="preview-field"><label>经济</label><p class="preview-text">{{ data?.economy || '-' }}</p></div>
              </div>
              <div class="preview-row">
                <div class="preview-field"><label>文化</label><p class="preview-text">{{ data?.culture || '-' }}</p></div>
                <div class="preview-field"><label>军事</label><p class="preview-text">{{ data?.military || '-' }}</p></div>
              </div>
              <div class="preview-field"><label>规则类型</label><p>{{ data?.core_rule_type || '-' }}</p></div>
              <div class="preview-field"><label>规则概要</label><p class="preview-text">{{ data?.core_rule_summary || '-' }}</p></div>
            </template>

            <!-- Outline -->
            <template v-else-if="type === 'outline'">
              <pre class="preview-outline">{{ formatOutline(data?.volumes) }}</pre>
            </template>
          </div>

          <div v-if="!loading" class="modal-footer">
            <button class="btn-draft" @click="emit('draft')">存草稿</button>
            <button class="btn-cancel" @click="emit('close')">放弃</button>
            <button class="btn-apply" @click="emit('apply')">应用</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal-panel {
  background: var(--bg-surface);
  border-radius: 12px;
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.modal-header h2 { margin: 0; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.modal-close {
  background: none; border: none; font-size: 24px; color: var(--text-muted); cursor: pointer; padding: 0; line-height: 1;
}
.modal-close:hover { color: var(--text-primary); }
.modal-body {
  flex: 1; overflow-y: auto; padding: 20px 24px;
}
.modal-loading {
  text-align: center; padding: 40px; color: var(--text-muted); font-size: 15px;
}
.preview-field { margin-bottom: 14px; }
.preview-field label {
  display: block; font-size: 12px; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; letter-spacing: 0.5px;
}
.preview-field p { margin: 0; font-size: 14px; color: var(--text-primary); line-height: 1.6; }
.preview-text { max-height: 120px; overflow-y: auto; }
.preview-row { display: flex; gap: 16px; }
.preview-row .preview-field { flex: 1; min-width: 0; }
.preview-outline {
  white-space: pre-wrap; font-family: inherit; font-size: 14px; color: var(--text-primary);
  line-height: 1.8; margin: 0; background: var(--bg-surface-hover); padding: 12px; border-radius: 6px;
}
.modal-footer {
  display: flex; gap: 8px; padding: 16px 24px; border-top: 1px solid var(--border-color);
  flex-shrink: 0; justify-content: flex-end;
}
.btn-apply, .btn-cancel, .btn-draft {
  padding: 8px 20px; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.btn-apply { background: var(--color-brand); color: #fff; border: none; }
.btn-apply:hover { background: var(--color-brand-hover); }
.btn-cancel { background: var(--bg-surface); color: var(--text-secondary); border: 1px solid var(--border-input); }
.btn-cancel:hover { background: var(--bg-surface-hover); }
.btn-draft { background: var(--bg-surface); color: var(--color-brand); border: 1px solid var(--color-brand); margin-right: auto; }
.btn-draft:hover { background: var(--bg-info); }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s ease; }
.modal-enter-active .modal-panel, .modal-leave-active .modal-panel { transition: transform 0.2s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-from .modal-panel { transform: scale(0.95) translateY(10px); }
.modal-leave-to .modal-panel { transform: scale(0.95) translateY(10px); }
</style>
