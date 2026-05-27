/**
 * Shared SSE stream reader — parses text/event-stream response.
 */
async function readSSE(resp, { onChunk, onDone, onError, onConsistency }) {
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let lastEvent = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) { lastEvent = ''; continue }
      if (trimmed.startsWith('event:')) {
        lastEvent = trimmed.slice(6).trim()
        continue
      }
      if (trimmed.startsWith('data:')) {
        const data = trimmed.slice(5).trim()
        if (lastEvent === 'error') {
          onError?.(data || '生成失败')
          return
        }
        if (lastEvent === 'done') {
          onDone?.()
          return
        }
        if (lastEvent === 'consistency') {
          try { onConsistency?.(JSON.parse(data)) } catch { onConsistency?.(data) }
          continue
        }
        if (lastEvent === 'chunk' || !lastEvent) {
          onChunk?.(data)
        }
      }
    }
  }
  onDone?.()
}

function authHeaders() {
  const token = localStorage.getItem('token')
  return {
    'Content-Type': 'application/json',
    'Authorization': token ? `Bearer ${token}` : '',
  }
}

/**
 * AI 续写 — SSE streaming
 */
export async function streamContinue({ bookId, context, instructions, temperature, topP, frequencyPenalty, presencePenalty, maxTokens, signal, structureId }, callbacks) {
  try {
    const resp = await fetch('/api/generation/continue', {
      method: 'POST',
      headers: authHeaders(),
      signal,
      body: JSON.stringify({
        book_id: bookId,
        context,
        instructions: instructions || '续写下一段',
        temperature: temperature ?? 1.2,
        top_p: topP ?? 0.95,
        frequency_penalty: frequencyPenalty ?? 0.3,
        presence_penalty: presencePenalty ?? 0.2,
        max_tokens: maxTokens ?? 800,
        structure_id: structureId,
      }),
    })

    if (!resp.ok) {
      const err = await resp.text().catch(() => '请求失败')
      callbacks.onError?.(`AI 服务错误 (${resp.status}): ${err}`)
      return
    }
    await readSSE(resp, callbacks)
  } catch (e) {
    callbacks.onError?.(e.name === 'AbortError' ? 'AbortError' : (e.message || '网络错误'))
  }
}

/**
 * AI 改写 — 润色/扩写/缩写 SSE streaming
 */
export async function streamRewrite({ context, mode, temperature, maxTokens, signal }, callbacks) {
  try {
    const resp = await fetch('/api/generation/rewrite', {
      method: 'POST',
      headers: authHeaders(),
      signal,
      body: JSON.stringify({
        context,
        mode: mode || 'polish',
        temperature: temperature ?? 0.8,
        max_tokens: maxTokens ?? 1200,
      }),
    })

    if (!resp.ok) {
      const err = await resp.text().catch(() => '请求失败')
      callbacks.onError?.(`AI 服务错误 (${resp.status}): ${err}`)
      return
    }
    await readSSE(resp, callbacks)
  } catch (e) {
    callbacks.onError?.(e.name === 'AbortError' ? 'AbortError' : (e.message || '网络错误'))
  }
}

/** Check for uncompleted stream buffer */
export async function getStreamBuffer(structureId) {
  const resp = await abortableFetch(`/api/generation/stream-buffer/${structureId}`, {
    headers: authHeaders(),
  }, 10000)
  if (!resp.ok) return { buffer: '' }
  return resp.json()
}

// ── 生成状态 ──
export async function getGenerationStatus(bookId) {
  const resp = await abortableFetch(`/api/generation/random/status/${bookId}`, {
    headers: authHeaders(),
  }, 15000)
  if (!resp.ok) throw new Error('获取状态失败')
  return resp.json()
}

// ── 随机生成 ──
async function randomPost(url, body) {
  const resp = await abortableFetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body || {}),
  }, 30000)
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({ message: '生成失败' }))
    throw new Error(err.message || `请求失败 (${resp.status})`)
  }
  return resp.json()
}

export function randomBookIdea(genre) {
  return randomPost('/api/generation/random/book-idea', genre ? { genre } : {})
}

export function randomCharacter(bookId, hint, depthLevel) {
  const body = {}
  if (hint) body.hint = hint
  if (depthLevel) body.depth_level = depthLevel
  return randomPost(`/api/generation/random/character/${bookId}`, body)
}

export function randomWorldSetting(bookId, hint) {
  return randomPost(`/api/generation/random/world-setting/${bookId}`, hint ? { hint } : {})
}

export function randomOutline(bookId, hint) {
  return randomPost(`/api/generation/random/outline/${bookId}`, hint ? { hint } : {})
}

export function randomOutlineExpand(bookId, oneSentence) {
  return randomPost(`/api/generation/random/outline/expand/${bookId}`, oneSentence ? { one_sentence: oneSentence } : {})
}

// ── Character chat ──
export async function chatCharacter(characterId, message, history) {
  const resp = await abortableFetch(`/api/generation/chat-character/${characterId}`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ message, history: history || [] }),
  }, 30000)
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({ message: '请求失败' }))
    throw new Error(err.message || `请求失败 (${resp.status})`)
  }
  return resp.json()
}

// ── Author review ──
function abortableFetch(url, opts, timeoutMs = 60000) {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeoutMs)
  return fetch(url, { ...opts, signal: ctrl.signal }).catch(e => {
    clearTimeout(timer)
    if (e.name === 'AbortError') throw new Error('请求超时（60秒无响应），请检查后端是否正常运行')
    throw e
  }).finally(() => clearTimeout(timer))
}

export async function getSuggestions({ bookId, context, structureId }) {
  const resp = await abortableFetch('/api/generation/suggest', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ book_id: bookId, context, structure_id: structureId }),
  }, 60000)
  const data = await resp.json()
  if (!resp.ok || !data.success) throw new Error(data.message || '审改请求失败')
  return data.data
}

// ── Plan-then-Execute ──

/** Step 1: Generate writing plan (non-streaming) */
export async function continuePlan({ bookId, context, structureId }) {
  const resp = await abortableFetch('/api/generation/continue-plan', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      book_id: bookId,
      context,
      structure_id: structureId,
    }),
  }, 60000)
  const data = await resp.json()
  if (!resp.ok || !data.success) throw new Error(data.message || '生成计划失败')
  return data.data
}

/** Analyze chapter content via AI */
export async function analyzeChapter(structureId) {
  const resp = await abortableFetch(`/api/chapters/${structureId}/analyze`, {
    method: 'POST',
    headers: authHeaders(),
  }, 60000)
  const data = await resp.json()
  if (!resp.ok || !data.success) throw new Error(data.message || '分析失败')
  return data.data
}

/** AI optimize continuation instructions */
export async function optimizeInstructions({ bookId, draft, context }) {
  const resp = await abortableFetch('/api/generation/optimize-instructions', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ book_id: bookId, draft, context }),
  }, 60000)
  const data = await resp.json()
  if (!resp.ok || !data.success) throw new Error(data.message || '优化失败')
  return data.data
}

/** Step 2: Execute approved plan (SSE streaming) */
export async function streamContinueExecute({ bookId, context, plan, instructions, temperature, topP, frequencyPenalty, presencePenalty, maxTokens, signal, structureId }, callbacks) {
  try {
    const resp = await fetch('/api/generation/continue-execute', {
      method: 'POST',
      headers: authHeaders(),
      signal,
      body: JSON.stringify({
        book_id: bookId,
        context,
        plan,
        instructions: instructions || '续写下一段',
        temperature: temperature ?? 1.2,
        top_p: topP ?? 0.95,
        frequency_penalty: frequencyPenalty ?? 0.3,
        presence_penalty: presencePenalty ?? 0.2,
        max_tokens: maxTokens ?? 800,
        structure_id: structureId,
      }),
    })

    if (!resp.ok) {
      const err = await resp.text().catch(() => '请求失败')
      callbacks.onError?.(`AI 服务错误 (${resp.status}): ${err}`)
      return
    }
    await readSSE(resp, callbacks)
  } catch (e) {
    callbacks.onError?.(e.name === 'AbortError' ? 'AbortError' : (e.message || '网络错误'))
  }
}
