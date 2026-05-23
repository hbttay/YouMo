<script setup>
import { watch, onUnmounted } from 'vue'

const props = defineProps({
  visible: Boolean,
  title: { type: String, default: '确认操作' },
  message: String,
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  danger: Boolean,
})

const emit = defineEmits(['confirm', 'cancel'])

function onEsc(e) {
  if (e.key === 'Escape') emit('cancel')
}

watch(() => props.visible, (v) => {
  if (v) document.addEventListener('keydown', onEsc)
  else document.removeEventListener('keydown', onEsc)
})

onUnmounted(() => document.removeEventListener('keydown', onEsc))
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="modal-overlay" @click.self="$emit('cancel')">
        <div class="modal-box">
          <h3 class="modal-title">{{ title }}</h3>
          <p class="modal-msg">{{ message }}</p>
          <div class="modal-btns">
            <button class="btn-cancel" @click="$emit('cancel')">{{ cancelText }}</button>
            <button :class="danger ? 'btn-danger' : 'btn-ok'" @click="$emit('confirm')">
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.modal-box {
  background: var(--bg-surface); border-radius: 12px; box-shadow: 0 8px 32px rgba(0,0,0,0.18);
  max-width: 400px; width: 90%; padding: 28px 32px 24px;
}
.modal-title { margin: 0 0 12px; font-size: 18px; font-weight: 600; color: var(--text-primary); }
.modal-msg { margin: 0 0 24px; font-size: 14px; line-height: 1.6; color: var(--text-secondary); }
.modal-btns { display: flex; justify-content: flex-end; gap: 12px; }

.btn-cancel, .btn-ok, .btn-danger {
  padding: 8px 22px; border-radius: 6px; border: none; font-size: 14px; font-weight: 500; cursor: pointer;
}
.btn-cancel { background: var(--bg-surface-hover); color: var(--text-primary); }
.btn-cancel:hover { background: var(--border-color); }
.btn-ok { background: #1677ff; color: #fff; }
.btn-ok:hover { opacity: 0.85; }
.btn-danger { background: #ff4d4f; color: #fff; }
.btn-danger:hover { opacity: 0.85; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
