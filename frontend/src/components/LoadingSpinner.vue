<script setup>
defineProps({
  text: { type: String, default: '加载中...' },
})
</script>

<template>
  <div class="spinner-wrapper">
    <div class="spinner"></div>
    <span class="spinner-text">{{ text }}</span>
  </div>
</template>

<style scoped>
.spinner-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 16px;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--color-brand, #5b3cc4);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.spinner-text {
  font-size: 14px;
  color: var(--text-muted);
}
</style>
