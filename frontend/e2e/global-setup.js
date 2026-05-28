import { chromium } from '@playwright/test'

const TEST_USER = {
  email: 'e2e-test@youmo.dev',
  username: 'E2ETester',
  password: 'test1234',
}

const apiBase = 'http://localhost:8080/api'
let token = ''

async function api(method, path, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  }
  if (token) opts.headers['Authorization'] = `Bearer ${token}`
  if (body) opts.body = JSON.stringify(body)
  return fetch(`${apiBase}${path}`, opts)
}

export default async function globalSetup() {
  // 1. Register test user
  try {
    const res = await fetch(`${apiBase}/users/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(TEST_USER),
    })
    console.log(`Register: ${res.status} — ${res.ok ? 'created' : 'already exists'}`)
  } catch (e) {
    console.log('Backend not running — skipping setup:', e.message)
    return
  }

  // 2. Login
  const loginRes = await fetch(`${apiBase}/users/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account: TEST_USER.email, password: TEST_USER.password }),
  })

  if (!loginRes.ok) {
    console.error('Login failed:', loginRes.status)
    return
  }

  const body = await loginRes.json()
  token = body.data?.token
  if (!token) {
    console.error('No token in login response')
    return
  }
  console.log('Token obtained')

  // 3. Seed a test book (so tests have data to work with)
  const listRes = await api('GET', '/books')
  if (listRes.ok) {
    const listBody = await listRes.json()
    const books = listBody.data || []
    if (books.length === 0) {
      const createRes = await api('POST', '/books', {
        title: 'E2E 测试书',
        core_idea: '自动化测试用书籍',
        creation_mode: 'LINEAR',
        target_length: 'MEDIUM',
      })
      if (createRes.ok) {
        const book = (await createRes.json()).data
        console.log(`Seed book created: id=${book.id}`)

        // Create a character
        await api('POST', `/books/${book.id}/characters`, {
          name: '测试角色', gender: 'Male', depth_level: 'L1',
        })

        // Create an outline node
        const nodeRes = await api('POST', `/books/${book.id}/outline/node`, {
          title: '测试章', node_type: 'CHAPTER', sequence: 0,
        })
        if (nodeRes.ok) {
          const node = (await nodeRes.json()).data
          // Save chapter content
          await api('POST', `/chapters/${node.id}/content`, {
            content: '这是 E2E 测试章节的内容。用于验证写作编辑器功能。',
          })
          console.log(`Seed chapter created: id=${node.id}`)
        }
      }
    } else {
      console.log(`Test user already has ${books.length} book(s)`)
    }
  }

  // 4. Save auth state via browser
  const browser = await chromium.launch({ channel: 'chrome' })
  const page = await browser.newPage()
  await page.goto('http://localhost:5173/login')
  await page.evaluate((t) => {
    localStorage.setItem('token', t)
  }, token)
  await page.context().storageState({ path: 'e2e/.auth/state.json' })
  await browser.close()

  console.log('Auth state saved for:', TEST_USER.email)
}
