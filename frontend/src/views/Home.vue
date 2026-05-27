<script setup>
import { onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBookStore } from '@/stores/book'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const router = useRouter()
const authStore = useAuthStore()
const store = useBookStore()

onMounted(() => {
  store.fetchBooks()
})

const stats = computed(() => {
  const books = store.books
  const draft = books.filter(b => b.status === 'DRAFT' || b.status === 'SERIALIZING').length
  const completed = books.filter(b => b.status === 'COMPLETED').length
  return { total: books.length, draft, completed }
})

const recentBooks = computed(() => store.books.slice(0, 4))

const modules = [
  { id: 'books', icon: '📚', name: '书籍管理', desc: '创建 · 编辑 · 导出', color: '#6366f1', link: '/books' },
  { id: 'characters', icon: '🎭', name: '角色工坊', desc: '人格画像 · 关系图谱 · AI 对话', color: '#7c3aed', link: '/modules/characters' },
  { id: 'world', icon: '🌍', name: '世界观管理', desc: '时代地理 · 势力文化 · 规则体系', color: '#059669', link: '/modules/world-setting' },
  { id: 'outline', icon: '📋', name: '大纲编排', desc: '卷章节树 · 思维导图 · 智能生成', color: '#d97706', link: '/modules/outline' },
  { id: 'foreshadowings', icon: '📌', name: '伏笔管理', desc: '类型标记 · 状态流转 · AI 扫描', color: '#dc2626', link: '/modules/foreshadowings' },
  { id: 'stats', icon: '📊', name: '统计分析', desc: '字数趋势 · 角色频次 · 质量指标', color: '#0891b2', link: '/modules/stats' },
  { id: 'feedback', icon: '💬', name: '用户反馈', desc: '问题报告 · AI 智能分析 · 等级划分', color: '#7c3aed', link: '/feedback' },
]

function goToModule(mod) {
  if (mod.link) { router.push(mod.link); return }
}
</script>

<template>
  <div class="home">
    <!-- Hero + Stats -->
    <section class="hero">
      <div class="hero-welcome">
        <h1>欢迎回来<span v-if="authStore.user?.username">，{{ authStore.user.username }}</span></h1>
        <p class="hero-sub" v-if="store.books.length > 0 || store.loading">继续你的创作之旅</p>
        <p class="hero-sub" v-else>创建你的第一本书，开启 AI 辅助创作之旅</p>
      </div>

      <LoadingSpinner v-if="store.loading" />

      <div class="stats-row" v-if="store.books.length > 0 && !store.loading">
        <div class="stat-card clickable" @click="router.push('/books')">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">本书籍</span>
        </div>
        <div class="stat-card clickable" @click="router.push('/books?status=in_progress')">
          <span class="stat-num">{{ stats.draft }}</span>
          <span class="stat-label">创作中</span>
        </div>
        <div class="stat-card clickable" @click="router.push('/books?status=COMPLETED')">
          <span class="stat-num">{{ stats.completed }}</span>
          <span class="stat-label">已完成</span>
        </div>
        <div class="stat-card highlight" @click="router.push('/books/create')">
          <span class="stat-num">＋</span>
          <span class="stat-label">新建书籍</span>
        </div>
      </div>

      <router-link to="/books/create" class="btn-primary btn-lg"
        v-if="store.books.length === 0 && !store.loading">
        开始你的第一本书
      </router-link>

      <!-- 最近书籍 -->
      <div class="recent-row" v-if="recentBooks.length > 0 && !store.loading">
        <span class="recent-label">最近书籍</span>
        <router-link v-for="book in recentBooks" :key="book.id" :to="`/books/${book.id}`"
          class="recent-chip">{{ book.title }}</router-link>
      </div>
    </section>

    <!-- Module Grid — always show when logged in, regardless of books -->
    <section class="section" v-if="!store.loading">
      <h2>创作工坊</h2>
      <div class="module-grid">
        <div
          v-for="mod in modules"
          :key="mod.id"
          class="module-card clickable"
          :style="{ '--mod-color': mod.color }"
          @click="goToModule(mod)"
        >
          <div class="module-icon-wrap">
            <span class="module-icon">{{ mod.icon }}</span>
          </div>
          <div class="module-info">
            <span class="module-name">{{ mod.name }}</span>
            <span class="module-desc">{{ mod.desc }}</span>
          </div>
          <span class="module-arrow">→</span>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.home { max-width: 1140px; margin: 0 auto; }

/* Hero */
.hero { text-align: center; padding: 40px 0 24px; }
.hero-welcome h1 { font-size: 28px; font-weight: 700; color: var(--text-primary); margin-bottom: 6px; }
.hero-sub { font-size: 15px; color: var(--text-muted); }
.btn-lg { padding: 12px 32px; font-size: 16px; border-radius: 8px; margin-top: 16px; }

/* Stats */
.stats-row { display: flex; justify-content: center; gap: 16px; margin-top: 24px; flex-wrap: wrap; }
.stat-card {
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 16px 28px; text-align: center; min-width: 100px;
  transition: box-shadow 0.2s, transform 0.15s;
}
.stat-card.clickable { cursor: pointer; }
.stat-card.clickable:hover { border-color: var(--color-brand); }
.stat-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06); transform: translateY(-1px); }
.stat-card.highlight { border-color: var(--color-brand); cursor: pointer; }
.stat-card.highlight:hover { box-shadow: 0 2px 16px var(--shadow-focus); }
.stat-card.highlight .stat-num, .stat-card.highlight .stat-label { color: var(--color-brand); }
.stat-num { display: block; font-size: 28px; font-weight: 700; color: var(--text-primary); line-height: 1.2; }
.stat-label { font-size: 13px; color: var(--text-secondary); }

/* Recent books */
.recent-row { display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 18px; flex-wrap: wrap; }
.recent-label { font-size: 13px; color: var(--text-muted); }
.recent-chip {
  display: inline-block; padding: 5px 14px; background: var(--bg-surface);
  border: 1px solid var(--border-color); border-radius: 20px; font-size: 13px;
  color: var(--text-primary); text-decoration: none; transition: border-color 0.15s, background 0.15s;
}
.recent-chip:hover { border-color: var(--color-brand); background: var(--bg-info); color: var(--color-brand); }
.recent-chip.more { color: var(--text-muted); font-size: 12px; padding: 5px 12px; }

/* Module Grid */
.section { margin-top: 40px; }
.section h2 { font-size: 18px; font-weight: 600; margin-bottom: 16px; color: var(--text-primary); }

.module-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }

.module-card {
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 20px; transition: box-shadow 0.2s, transform 0.15s, border-color 0.2s;
  display: flex; align-items: flex-start; gap: 14px;
}
.module-card.clickable { cursor: pointer; }
.module-card.clickable:hover {
  box-shadow: 0 3px 16px rgba(0,0,0,0.08); transform: translateY(-2px);
  border-color: var(--mod-color, var(--color-brand));
}
.module-icon-wrap {
  width: 44px; height: 44px; border-radius: 10px;
  background: color-mix(in srgb, var(--mod-color) 12%, transparent);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.module-icon { font-size: 22px; line-height: 1; }
.module-info { flex: 1; min-width: 0; }
.module-name { display: block; font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 2px; }
.module-desc { display: block; font-size: 13px; color: var(--text-secondary); }
.module-arrow {
  font-size: 16px; color: var(--text-muted); align-self: center; flex-shrink: 0;
  transition: transform 0.2s, color 0.2s;
}
.module-card.clickable:hover .module-arrow { transform: translateX(3px); color: var(--mod-color); }

@media (max-width: 750px) { .module-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 480px) { .module-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) {
  .hero { padding: 24px 0 16px; }
  .hero-welcome h1 { font-size: 22px; }
  .stats-row { gap: 10px; }
  .stat-card { padding: 12px 18px; min-width: 72px; }
  .stat-num { font-size: 22px; }
  .section { margin-top: 28px; }
}
</style>
