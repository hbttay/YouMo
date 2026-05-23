<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createBook } from '@/api/book'
import { randomBookIdea } from '@/api/generation'
import { useDrafts } from '@/composables/useDrafts'
import RandomPreviewModal from '@/components/RandomPreviewModal.vue'
import { CREATION_LABEL, LENGTH_LABEL } from '@/utils/labels'

const router = useRouter()
const { add: addDraft } = useDrafts(null)

const form = reactive({
  title: '',
  core_idea: '',
  creation_mode: 'LINEAR',
  target_length: 'MEDIUM',
})

const submitting = ref(false)
const error = ref('')
const generating = ref(false)

const preview = ref({ show: false, type: 'book-idea', data: null })

async function handleRandomIdea() {
  generating.value = true
  error.value = ''
  try {
    const data = await randomBookIdea()
    preview.value = { show: true, type: 'book-idea', data }
  } catch (e) {
    error.value = e.message || '生成失败'
  } finally {
    generating.value = false
  }
}

function applyPreview() {
  const d = preview.value.data
  if (d) {
    if (d.title) form.title = d.title
    if (d.core_idea) form.core_idea = d.core_idea
    if (d.creation_mode) form.creation_mode = d.creation_mode
    if (d.target_length) form.target_length = d.target_length
  }
  preview.value = { show: false, type: 'book-idea', data: null }
}

function draftPreview() {
  addDraft('book-idea', preview.value.data, preview.value.data?.title || '未命名创意')
  preview.value = { show: false, type: 'book-idea', data: null }
}

function closePreview() {
  preview.value = { show: false, type: 'book-idea', data: null }
}

async function handleSubmit() {
  if (!form.title.trim()) {
    error.value = '请输入书名'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const res = await createBook(form)
    router.push(`/books/${res.data.id}`)
  } catch (e) {
    error.value = e.response?.data?.message || '创建失败，请检查后端是否启动'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="book-create">
    <h1>新建书籍</h1>

    <form @submit.prevent="handleSubmit" class="create-form">
      <div class="form-group">
        <label for="title">
          书名 <span class="required">*</span>
          <button
            type="button"
            class="btn-random"
            :disabled="generating"
            @click="handleRandomIdea"
          >{{ generating ? '生成中...' : '随机生成' }}</button>
        </label>
        <input
          id="title"
          v-model="form.title"
          type="text"
          placeholder="给你的书取个名字"
          maxlength="100"
        />
      </div>

      <div class="form-group">
        <label for="desc">简介</label>
        <textarea
          id="desc"
          v-model="form.core_idea"
          placeholder="简单描述一下这本书（可选）"
          rows="3"
          maxlength="2000"
        ></textarea>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label for="mode">创作模式</label>
          <select id="mode" v-model="form.creation_mode">
            <option value="LINEAR">{{ CREATION_LABEL.LINEAR }}</option>
            <option value="DIVERGENT">{{ CREATION_LABEL.DIVERGENT }}</option>
          </select>
        </div>

        <div class="form-group">
          <label for="length">目标篇幅</label>
          <select id="length" v-model="form.target_length">
            <option value="SHORT">{{ LENGTH_LABEL.SHORT }}</option>
            <option value="MEDIUM">{{ LENGTH_LABEL.MEDIUM }}</option>
            <option value="LONG">{{ LENGTH_LABEL.LONG }}</option>
          </select>
        </div>
      </div>

      <div v-if="error" class="error-msg">{{ error }}</div>

      <div class="form-actions">
        <button type="button" class="btn-cancel" @click="router.push('/')">取消</button>
        <button type="submit" class="btn-primary" :disabled="submitting">
          {{ submitting ? '创建中...' : '创建书籍' }}
        </button>
      </div>
    </form>

    <RandomPreviewModal
      :visible="preview.show"
      type="book-idea"
      :data="preview.data"
      :loading="false"
      @apply="applyPreview"
      @draft="draftPreview"
      @close="closePreview"
    />
  </div>
</template>

<style scoped>
.book-create {
  max-width: 560px;
  margin: 0 auto;
}

h1 {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 24px;
}

.btn-random {
  font-size: 12px;
  padding: 2px 10px;
  background: var(--bg-surface);
  color: var(--color-brand);
  border: 1px solid var(--color-brand);
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  margin-left: 8px;
  transition: all 0.15s;
}
.btn-random:hover:not(:disabled) { background: #f5f3ff; }
.btn-random:disabled { opacity: 0.5; cursor: not-allowed; }

.create-form {
  background: var(--bg-surface);
  border-radius: 8px;
  padding: 24px;
  border: 1px solid var(--border-color);
}

.error-msg {
  background: var(--bg-error-soft);
  color: var(--color-danger);
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 16px;
}
</style>
