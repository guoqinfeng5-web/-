<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  captchaAnswer: '',
})

const a = ref(0)
const b = ref(0)

const refreshCaptcha = () => {
  a.value = Math.floor(Math.random() * 9) + 1
  b.value = Math.floor(Math.random() * 9) + 1
  form.captchaAnswer = ''
}

const doLogin = () => {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  if (Number(form.captchaAnswer) !== a.value + b.value) {
    ElMessage.warning('验证码计算结果错误，请重试')
    refreshCaptcha()
    return
  }

  localStorage.setItem('demo_logged_in', 'true')
  localStorage.setItem('demo_username', form.username.trim())
  ElMessage.success('登录成功')
  router.push('/')
}

refreshCaptcha()
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="title-wrap">
          <h2>AI 图书管理系统</h2>
          <span>欢迎登录智能分析平台</span>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            clearable
            @keyup.enter="doLogin"
          />
        </el-form-item>

        <el-form-item label="验证码">
          <div class="captcha-row">
            <div class="captcha-expression">{{ a }} + {{ b }} = ?</div>
            <el-button :icon="Refresh" circle @click="refreshCaptcha" />
          </div>
          <el-input
            v-model="form.captchaAnswer"
            class="captcha-input"
            placeholder="请输入计算结果"
            @keyup.enter="doLogin"
          />
        </el-form-item>

        <el-button type="primary" class="login-btn" @click="doLogin">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eaf3ff 0%, #f6faff 45%, #edf6ff 100%);
  padding: 20px;
  box-sizing: border-box;
}

.login-card {
  width: 420px;
  border-radius: 16px;
  border: 1px solid #dbeaff;
  box-shadow: 0 16px 45px rgba(37, 85, 180, 0.18);
}

.title-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-wrap h2 {
  margin: 0;
  color: #1f3f7a;
}

.title-wrap span {
  color: #5f7cae;
  font-size: 13px;
}

.captcha-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.captcha-expression {
  flex: 1;
  height: 40px;
  border-radius: 8px;
  background: #f0f6ff;
  border: 1px dashed #bfd5ff;
  color: #2b4d8f;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.captcha-input {
  width: 100%;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
}
</style>

