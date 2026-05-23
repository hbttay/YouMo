import { defineStore } from 'pinia'
import { listBooks, getBook, createBook } from '@/api/book'

export const useBookStore = defineStore('book', {
  state: () => ({
    books: [],
    currentBook: null,
    loading: false,
    error: null,
  }),

  actions: {
    async fetchBooks() {
      this.loading = true
      this.error = null
      try {
        const res = await listBooks()
        this.books = res.data || []
      } catch (e) {
        this.error = e.response?.data?.message || '加载书籍列表失败'
      } finally {
        this.loading = false
      }
    },

    async fetchBook(id) {
      this.loading = true
      this.error = null
      try {
        const res = await getBook(id)
        this.currentBook = res.data
      } catch (e) {
        this.error = e.response?.data?.message || '加载书籍失败'
      } finally {
        this.loading = false
      }
    },

    async addBook(data) {
      this.loading = true
      this.error = null
      try {
        const res = await createBook(data)
        this.books.unshift(res.data)
        return res.data
      } catch (e) {
        this.error = e.response?.data?.message || '创建书籍失败'
        throw e
      } finally {
        this.loading = false
      }
    },
  },
})
