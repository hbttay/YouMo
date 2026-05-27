<script setup>
import { ref, nextTick, watch } from 'vue'
import { chatCharacter } from '@/api/generation'

const props = defineProps({
  character: { type: Object, required: true },
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])

const messages = ref([])
const input = ref('')
const sending = ref(false)
const chatBody = ref(null)

const avatarColors = [
  '#E8D5F5', '#D5E8F5', '#D5F5E8', '#F5ECD5',
  '#F5D5E8', '#D5F5F5', '#F5F5D5', '#E8E8E8',
]

function getAvatarColor(name) {
  if (!name) return avatarColors[0]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return avatarColors[Math.abs(hash) % avatarColors.length]
}

watch(() => props.character?.id, () => {
  messages.value = [
    { role: 'assistant', content: `你好，我是${props.character.name}。有什么事吗？` }
  ]
})

watch(() => props.visible, (v) => {
  if (v && messages.value.length === 0) {
    messages.value = [
      { role: 'assistant', content: `你好，我是${props.character.name}。有什么事吗？` }
    ]
  }
})

function scrollToBottom() {
  nextTick(() => {
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
  })
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  scrollToBottom()

  sending.value = true
  try {
    const history = messages.value.slice(0, -1).map(m => ({
      role: m.role,
      content: m.content,
    }))
    const data = await chatCharacter(props.character.id, text, history)
    if (data.error) {
      messages.value.push({ role: 'assistant', content: `（${data.error}）`, isError: true })
    } else {
      messages.value.push({ role: 'assistant', content: data.reply || '（角色没有回应）' })
    }
  } catch (e) {
    messages.value.push({ role: 'assistant', content: `（请求失败：${e.message}）`, isError: true })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="chat-overlay" @click.self="emit('close')">
      <div class="chat-panel">
        <div class="chat-header">
          <div class="chat-char-info">
            <div class="chat-avatar" :style="{ backgroundColor: getAvatarColor(character.name) }">
              {{ character.name?.charAt(0)?.toUpperCase() || '?' }}
            </div>
            <div>
              <h3>{{ character.name }}</h3>
              <span class="chat-char-identity">{{ character.identity || '角色对话' }}</span>
            </div>
          </div>
          <button class="chat-close" @click="emit('close')">&times;</button>
        </div>

        <div class="chat-body" ref="chatBody">
          <div
            v-for="(m, i) in messages"
            :key="i"
            :class="['chat-bubble', m.role === 'user' ? 'bubble-user' : 'bubble-assistant', { 'bubble-error': m.isError }]"
          >
            <div class="bubble-label">{{ m.role === 'user' ? '你' : character.name }}</div>
            <div class="bubble-text">{{ m.content }}</div>
          </div>
          <div v-if="sending" class="chat-bubble bubble-assistant">
            <div class="bubble-label">{{ character.name }}</div>
            <div class="bubble-text typing">正在输入<span class="dots">...</span></div>
          </div>
        </div>

        <div class="chat-input-area">
          <input
            v-model="input"
            class="chat-input"
            placeholder="输入消息，按 Enter 发送..."
            maxlength="500"
            :disabled="sending"
            @keydown="onKeydown"
          />
          <button class="chat-send" :disabled="!input.trim() || sending" @click="send">发送</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.chat-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  z-index: 1001; display: flex; justify-content: flex-end;
}
.chat-panel {
  width: 440px; max-width: 100vw; height: 100vh;
  background: var(--bg-primary, #fff);
  display: flex; flex-direction: column;
  box-shadow: -6px 0 24px rgba(0,0,0,0.12);
}
.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid var(--border-color, #e5e7eb);
  flex-shrink: 0;
}
.chat-char-info {
  display: flex; align-items: center; gap: 12px;
}
.chat-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700; color: var(--text-primary);
}
.chat-char-info h3 {
  margin: 0; font-size: 16px; font-weight: 600; color: var(--text-primary);
}
.chat-char-identity {
  font-size: 12px; color: var(--text-secondary);
}
.chat-close {
  width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
  background: none; border: none; font-size: 20px; color: var(--text-muted);
  cursor: pointer; border-radius: 4px;
}
.chat-close:hover { background: #f3f4f6; color: var(--text-primary); }

.chat-body {
  flex: 1; overflow-y: auto; padding: 16px 20px;
  display: flex; flex-direction: column; gap: 12px;
}
.chat-bubble {
  max-width: 85%;
}
.bubble-user {
  align-self: flex-end;
}
.bubble-assistant {
  align-self: flex-start;
}
.bubble-label {
  font-size: 11px; font-weight: 600; color: var(--text-secondary);
  margin-bottom: 4px; padding: 0 4px;
}
.bubble-user .bubble-label {
  text-align: right;
}
.bubble-text {
  padding: 10px 14px; border-radius: 12px;
  font-size: 14px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
}
.bubble-user .bubble-text {
  background: #7c3aed; color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble-assistant .bubble-text {
  background: #f3f4f6; color: var(--text-primary);
  border-bottom-left-radius: 4px;
}
.bubble-error .bubble-text {
  background: #fef2f2; color: #991b1b;
}
.typing .dots {
  animation: dot-blink 1.4s infinite;
}
@keyframes dot-blink {
  0%, 20% { opacity: 0; }
  50% { opacity: 1; }
  80%, 100% { opacity: 0; }
}

.chat-input-area {
  display: flex; gap: 8px;
  padding: 12px 20px; border-top: 1px solid var(--border-color, #e5e7eb);
  flex-shrink: 0;
}
.chat-input {
  flex: 1; padding: 10px 14px;
  border: 1px solid #e0e0e0; border-radius: 8px;
  font-size: 14px; color: var(--text-primary);
  background: var(--bg-surface); outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
}
.chat-input:focus { border-color: #7c3aed; }
.chat-send {
  padding: 10px 18px; border: none; border-radius: 8px;
  background: #7c3aed; color: #fff;
  font-size: 14px; font-weight: 500; cursor: pointer;
  font-family: inherit; transition: background 0.2s;
  white-space: nowrap;
}
.chat-send:hover:not(:disabled) { background: #6d28d9; }
.chat-send:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
