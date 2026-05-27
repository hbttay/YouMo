import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'BookList',
    component: () => import('@/views/Home.vue'),
  },
  {
    path: '/books',
    name: 'Books',
    component: () => import('@/views/BookList.vue'),
  },
  {
    path: '/books/create',
    name: 'BookCreate',
    component: () => import('@/views/BookCreate.vue'),
  },
  {
    path: '/books/:id',
    name: 'BookDetail',
    component: () => import('@/views/BookDetail.vue'),
  },
  {
    path: '/books/:id/characters',
    name: 'CharacterList',
    component: () => import('@/views/CharacterList.vue'),
  },
  {
    path: '/books/:id/outline',
    name: 'OutlineEditor',
    component: () => import('@/views/OutlineEditor.vue'),
  },
  {
    path: '/books/:id/world-setting',
    name: 'WorldSetting',
    component: () => import('@/views/WorldSetting.vue'),
  },
  {
    path: '/books/:id/foreshadowings',
    name: 'ForeshadowingList',
    component: () => import('@/views/ForeshadowingList.vue'),
  },
  {
    path: '/books/:bookId/write/:structureId',
    name: 'ChapterWrite',
    component: () => import('@/views/ChapterWrite.vue'),
  },
  {
    path: '/modules/:type',
    name: 'ModuleHub',
    component: () => import('@/views/ModuleHub.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
  },
  {
    path: '/user/center',
    name: 'UserCenter',
    component: () => import('@/views/UserCenter.vue'),
  },
  {
    path: '/feedback',
    name: 'Feedback',
    component: () => import('@/views/FeedbackView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const PUBLIC_PAGES = ['Login', 'Register']

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const isPublic = PUBLIC_PAGES.includes(to.name)

  if (!isPublic && !token) {
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }
  if (isPublic && token) {
    return next({ name: 'BookList' })
  }
  next()
})

export default router
