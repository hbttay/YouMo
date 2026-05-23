<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getMe, changePassword, updateProfile } from '@/api/auth'
import { useRequest } from '@/composables/useRequest'

const router = useRouter()
const authStore = useAuthStore()

const user = ref(authStore.user)
const profileMsg = ref('')
const profileErr = ref('')

const email = ref(authStore.user?.email || '')
const { execute: updateProfileExec, loading: profileLoading } = useRequest(updateProfile)
const { execute: changePwdExec, loading: pwdLoading } = useRequest(changePassword)

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const pwdMsg = ref('')
const pwdErr = ref('')

onMounted(async () => {
  try {
    const res = await getMe()
    if (res?.data) {
      user.value = res.data
      email.value = res.data.email
    }
  } catch (e) { /* use cached */ }
})

async function handleUpdateProfile() {
  profileMsg.value = ''
  profileErr.value = ''
  if (!email.value.trim()) {
    profileErr.value = '邮箱不能为空'
    return
  }
  try {
    const res = await updateProfileExec({ email: email.value.trim() })
    user.value = res.data
    authStore.user = res.data
    localStorage.setItem('user', JSON.stringify(res.data))
    profileMsg.value = '修改成功'
  } catch (e) {
    profileErr.value = e?.response?.data?.message || '修改失败'
  }
}

async function handleChangePassword() {
  pwdMsg.value = ''
  pwdErr.value = ''
  if (!oldPassword.value || !newPassword.value) {
    pwdErr.value = '请填写所有密码字段'
    return
  }
  if (newPassword.value.length < 6) {
    pwdErr.value = '新密码至少 6 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    pwdErr.value = '两次新密码不一致'
    return
  }
  try {
    await changePwdExec({ old_password: oldPassword.value, new_password: newPassword.value })
    pwdMsg.value = '密码修改成功'
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    pwdErr.value = e?.response?.data?.message || '修改失败'
  }
}
</script>

<template>
  <div class="user-center">
    <router-link to="/" class="back-link">&larr; 返回列表</router-link>
    <h1 class="page-title">个人中心</h1>

    <!-- Profile -->
    <section class="card">
      <h2 class="card-title">基本信息</h2>
      <div class="info-row">
        <span class="label">用户 ID</span>
        <span class="value">{{ user?.id }}</span>
      </div>
      <div class="info-row">
        <span class="label">账号状态</span>
        <span class="value" :class="user?.status === 'ACTIVE' ? 'active' : 'inactive'">
          {{ user?.status === 'ACTIVE' ? '正常' : user?.status }}
        </span>
      </div>
      <div class="info-row">
        <span class="label">注册时间</span>
        <span class="value">{{ user?.created_at }}</span>
      </div>

      <div class="form-group">
        <label class="field-label">邮箱</label>
        <div class="field-row">
          <input v-model="email" class="input" type="email" />
          <button class="btn-save" :disabled="profileLoading" @click="handleUpdateProfile">
            {{ profileLoading ? '保存中...' : '修改' }}
          </button>
        </div>
        <p v-if="profileMsg" class="msg-ok">{{ profileMsg }}</p>
        <p v-if="profileErr" class="msg-err">{{ profileErr }}</p>
      </div>
    </section>

    <!-- Password -->
    <section class="card">
      <h2 class="card-title">修改密码</h2>
      <div class="form-group">
        <label class="field-label">原密码</label>
        <input v-model="oldPassword" class="input" type="password" />
      </div>
      <div class="form-group">
        <label class="field-label">新密码</label>
        <input v-model="newPassword" class="input" type="password" placeholder="至少 6 位" />
      </div>
      <div class="form-group">
        <label class="field-label">确认新密码</label>
        <input v-model="confirmPassword" class="input" type="password" />
      </div>
      <button class="btn-save" :disabled="pwdLoading" @click="handleChangePassword">
        {{ pwdLoading ? '修改中...' : '修改密码' }}
      </button>
      <p v-if="pwdMsg" class="msg-ok">{{ pwdMsg }}</p>
      <p v-if="pwdErr" class="msg-err">{{ pwdErr }}</p>
    </section>
  </div>
</template>

<style scoped>
.user-center {
  max-width: 520px;
  margin: 0 auto;
  padding: 24px 0 80px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 16px 0 24px;
}

.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 24px;
  margin-bottom: 20px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
  font-size: 14px;
}

.label { color: var(--text-muted); }
.value { color: var(--text-primary); font-weight: 500; }
.value.active { color: #059669; }
.value.inactive { color: #ef4444; }

.form-group { margin-top: 16px; }

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.field-row {
  display: flex;
  gap: 10px;
}

.input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-primary);
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.input:focus {
  outline: none;
  border-color: var(--color-brand);
  box-shadow: 0 0 0 3px rgba(91, 60, 196, 0.08);
}

.btn-save {
  padding: 10px 22px;
  background: var(--color-brand);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
  transition: background 0.15s;
}

.btn-save:hover:not(:disabled) { background: var(--color-brand-hover); }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }

.msg-ok { font-size: 13px; color: #059669; margin: 8px 0 0; }
.msg-err { font-size: 13px; color: #ef4444; margin: 8px 0 0; }
</style>
