import { ref } from 'vue'

/**
 * 统一封装异步请求的 loading / error 状态。
 * execute 不抛异常，失败返回 null，调用方判空即可。
 */
export function useRequest(fn) {
  const loading = ref(false)
  const error = ref('')

  async function execute(...args) {
    loading.value = true
    error.value = ''
    try {
      return await fn(...args)
    } catch (e) {
      error.value = e.response?.data?.message || '操作失败'
      return null
    } finally {
      loading.value = false
    }
  }

  return { loading, error, execute }
}
