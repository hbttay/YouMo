import { MOCK } from '../mocks/ai-responses.js'

/**
 * Create a minimal SSE ReadableStream that emits chunks then done.
 * @param {string[]} chunks - Text chunks to emit as SSE "chunk" events
 * @param {object} [opts]
 * @param {number} [opts.chunkDelay=40] - ms delay between chunks
 * @param {object} [opts.consistency] - optional consistency data to emit before chunks
 */
function createSSEStream(chunks, { chunkDelay = 40, consistency } = {}) {
  let i = 0
  let sentConsistency = false

  return new ReadableStream({
    start(controller) {
      function push() {
        // Emit consistency event first if configured
        if (consistency && !sentConsistency) {
          sentConsistency = true
          const cData = typeof consistency === 'string' ? consistency : JSON.stringify(consistency)
          controller.enqueue(new TextEncoder().encode(`event: consistency\ndata: ${cData}\n\n`))
          setTimeout(push, chunkDelay)
          return
        }

        if (i < chunks.length) {
          controller.enqueue(new TextEncoder().encode(`event: chunk\ndata: ${chunks[i]}\n\n`))
          i++
          setTimeout(push, chunkDelay)
        } else {
          controller.enqueue(new TextEncoder().encode('event: done\ndata: \n\n'))
          controller.close()
        }
      }
      push()
    },
  })
}

// Pre-made SSE chunks for common scenarios
const CONTINUE_CHUNKS = [
  '密室中弥漫着腐朽的气息，',
  '云汐的手指触碰到石棺的瞬间，一股寒意从指尖蔓延至全身。',
  '她顿了顿，运起灵力护住心脉，这才凝神看向棺盖上的铭文。',
  '那些古老的符文在黑暗中微微发光，仿佛在等待了千年之后，终于等到了对的人。',
]

const REWRITE_CHUNKS = [
  '云汐推开石门的瞬间，',
  '密室中的空气仿佛凝固了千年，带着腐朽与尘埃的气息扑面而来。',
  '她屏住呼吸，目光落在那具青石棺椁上。棺盖上的铭文闪烁着幽蓝色的光芒，',
  '似乎在诉说着某个被遗忘的上古秘密。她缓缓走近，每一步都小心翼翼。',
]

const CHAT_CHUNKS = [
  '（云汐抬起头，',
  '目光坚定）',
  '我是青云门掌门之女，无论前路有多艰难，',
  '我绝不会退缩。',
]

// ── Route matching helpers ──
function matchPath(url, pattern) {
  const re = new RegExp('^' + pattern.replace(/:\w+/g, '\\d+') + '$')
  return re.test(url)
}

function jsonBody(body, status = 200) {
  return { status, contentType: 'application/json', body: JSON.stringify(body) }
}

function sseBody(chunks, opts) {
  const stream = createSSEStream(chunks, opts)
  return { status: 200, contentType: 'text/event-stream', body: stream }
}

/**
 * Register ALL AI mock routes on the page.
 * Call once per test in beforeEach or before the test.
 */
export async function setupAllMocks(page) {
  await page.route('**/api/generation/**', handleGeneration)
  await page.route('**/api/chapters/*/analyze', handleRoute)
  await page.route('**/api/chapters/*/consistency-check', handleRoute)
  await page.route('**/api/books/*/foreshadowings/scan/**', handleRoute)
  await page.route('**/api/feedback/*/analyze', handleRoute)
}

/**
 * Handler for /api/generation/** routes
 */
async function handleGeneration(route) {
  const url = new URL(route.request().url())
  const path = url.pathname
  const method = route.request().method()

  // ── SSE streaming endpoints ──
  if (method === 'POST' && path === '/api/generation/continue') {
    return route.fulfill(sseBody(CONTINUE_CHUNKS))
  }
  if (method === 'POST' && path === '/api/generation/rewrite') {
    return route.fulfill(sseBody(REWRITE_CHUNKS))
  }
  if (method === 'POST' && path === '/api/generation/continue-execute') {
    return route.fulfill(sseBody(CONTINUE_CHUNKS, { consistency: MOCK.consistencyCheck.data.issues }))
  }

  // ── Non-streaming POST endpoints ──
  if (method === 'POST' && path === '/api/generation/random/book-idea') {
    return route.fulfill(jsonBody(MOCK.randomBookIdea))
  }
  if (method === 'POST' && path.includes('/api/generation/random/character/')) {
    return route.fulfill(jsonBody(MOCK.randomCharacter))
  }
  if (method === 'POST' && path.includes('/api/generation/random/character-fission/')) {
    return route.fulfill(jsonBody(MOCK.characterFission))
  }
  if (method === 'POST' && path.includes('/api/generation/random/world-setting/')) {
    return route.fulfill(jsonBody(MOCK.randomWorldSetting))
  }
  if (method === 'POST' && path.includes('/api/generation/random/outline/expand/')) {
    return route.fulfill(jsonBody(MOCK.randomOutlineExpand))
  }
  if (method === 'POST' && path.includes('/api/generation/random/outline/')) {
    return route.fulfill(jsonBody(MOCK.randomOutline))
  }
  if (method === 'POST' && path.includes('/api/generation/chat-character/')) {
    return route.fulfill(jsonBody(MOCK.chatCharacter))
  }
  if (method === 'POST' && path === '/api/generation/suggest') {
    return route.fulfill(jsonBody(MOCK.suggestions))
  }
  if (method === 'POST' && path === '/api/generation/writing-guide') {
    return route.fulfill(jsonBody(MOCK.writingGuide))
  }
  if (method === 'POST' && path === '/api/generation/continue-plan') {
    return route.fulfill(jsonBody(MOCK.continuePlan))
  }
  if (method === 'POST' && path === '/api/generation/optimize-instructions') {
    return route.fulfill(jsonBody(MOCK.optimizeInstructions))
  }
  if (method === 'POST' && path.includes('/api/generation/book-report/')) {
    return route.fulfill(jsonBody(MOCK.bookReport))
  }

  // ── GET endpoints ──
  if (method === 'GET' && path.includes('/api/generation/random/status/')) {
    return route.fulfill(jsonBody({ success: true, data: { running: false } }))
  }
  if (method === 'GET' && path.includes('/api/generation/stream-buffer/')) {
    return route.fulfill(jsonBody({ buffer: '' }))
  }

  // Fallback — let through (shouldn't hit in tests)
  return route.continue()
}

/**
 * Handler for non-generation AI routes (analyze, consistency-check, scan, feedback analyze)
 */
async function handleRoute(route) {
  const url = new URL(route.request().url())
  const path = url.pathname

  if (path.includes('/analyze') && path.includes('/chapters/')) {
    return route.fulfill(jsonBody(MOCK.analyzeChapter))
  }
  if (path.includes('/consistency-check')) {
    return route.fulfill(jsonBody(MOCK.consistencyCheck))
  }
  if (path.includes('/foreshadowings/scan/')) {
    return route.fulfill(jsonBody({ success: true, data: MOCK.scanForeshadowings }))
  }
  if (path.includes('/feedback/') && path.includes('/analyze')) {
    return route.fulfill(jsonBody(MOCK.analyzeFeedback))
  }

  return route.continue()
}

// Re-export MOCK for convenience
export { MOCK }
export { createSSEStream, jsonBody, sseBody }
