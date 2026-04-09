<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const loadingBooks = ref(false)
const loadingAi = ref(false)
const books = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const currentBookMode = ref('all')
const searchKeyword = ref('')
const question = ref('')
const chatMessages = ref([
  {
    role: 'assistant',
    content: '你好，我是 AI 图书助手。你可以选择左侧书籍，或直接输入你的问题。',
  },
])
const chatBodyRef = ref(null)
const thinkingTips = ['正在检索数据库...', '正在分析语义...', 'AI 正在组织语言...']
const thinkingTipIndex = ref(0)
let thinkingTimer = null

const parseBooks = (rawData) => {
  if (Array.isArray(rawData)) return rawData
  if (rawData && Array.isArray(rawData.records)) return rawData.records
  if (rawData && Array.isArray(rawData.list)) return rawData.list
  return []
}

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const requestBooks = async (mode = currentBookMode.value) => {
  currentBookMode.value = mode
  loadingBooks.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    let url = '/api/books/page'

    if (mode === 'hot') {
      url = '/api/books/hot'
    } else if (mode === 'highScore') {
      url = '/api/books/high-score'
    }

    const { data } = await axios.get(url, { params })
    if (data?.code !== 200) {
      throw new Error(data?.message || '获取图书列表失败')
    }
    books.value = parseBooks(data?.data)
    total.value = Number(data?.data?.total || 0)
    pageNum.value = Number(data?.data?.current || pageNum.value)
    pageSize.value = Number(data?.data?.size || pageSize.value)
  } catch (error) {
    ElMessage.error(error?.message || '获取图书列表失败')
  } finally {
    loadingBooks.value = false
  }
}

const onHot = () => {
  pageNum.value = 1
  requestBooks('hot')
}
const onHighScore = () => {
  pageNum.value = 1
  requestBooks('highScore')
}
const onAll = () => {
  pageNum.value = 1
  requestBooks('all')
}

const onPageChange = (p) => {
  pageNum.value = p
  requestBooks()
}

const onPageSizeChange = (s) => {
  pageSize.value = s
  pageNum.value = 1
  requestBooks()
}

const onSearch = () => {
  pageNum.value = 1
  requestBooks()
}

const onResetSearch = () => {
  searchKeyword.value = ''
  pageNum.value = 1
  requestBooks()
}

const onRowClick = (row) => {
  question.value = row?.title || ''
}

const renderMarkdown = (text) => {
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')

  return escaped
    .split('\n')
    .map((line) => {
      const trimmed = line.trim()
      if (!trimmed) return '<p class="md-empty"></p>'
      if (trimmed.startsWith('#### ')) {
        return `<h4>${trimmed.slice(5)}</h4>`
      }
      if (trimmed.startsWith('### ')) {
        return `<h3>${trimmed.slice(4)}</h3>`
      }
      return `<p>${line}</p>`
    })
    .join('')
}

const startThinkingTips = () => {
  thinkingTipIndex.value = 0
  if (thinkingTimer) clearInterval(thinkingTimer)
  thinkingTimer = setInterval(() => {
    thinkingTipIndex.value = (thinkingTipIndex.value + 1) % thinkingTips.length
  }, 1300)
}

const stopThinkingTips = () => {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = null
  }
}

const typewriterAppend = async (text, targetMessage) => {
  targetMessage.content = ''
  const content = String(text ?? '')
  for (let i = 0; i < content.length; i++) {
    targetMessage.content += content[i]
    if (i % 4 === 0) {
      await scrollChatToBottom()
    }
    await new Promise((resolve) => setTimeout(resolve, 30))
  }
  await scrollChatToBottom()
}

const sendQuestion = async () => {
  const q = question.value.trim()
  if (!q) {
    ElMessage.warning('请输入问题')
    return
  }
  chatMessages.value.push({ role: 'user', content: q })
  question.value = ''
  await scrollChatToBottom()

  loadingAi.value = true
  startThinkingTips()
  const assistantMessage = {
    role: 'assistant',
    content: '🔍 AI 正在检索馆藏数据并进行深度分析，请稍候...',
  }
  chatMessages.value.push(assistantMessage)
  await scrollChatToBottom()
  try {
    const { data } = await axios.get('/api/ai/analyze', {
      params: { question: q },
    })
    if (data?.code !== 200) {
      throw new Error(data?.message || 'AI 分析失败')
    }
    assistantMessage.content = ''
    await typewriterAppend(data?.data ?? '未返回分析内容', assistantMessage)
  } catch (error) {
    assistantMessage.content = `请求失败：${error?.message || '未知错误'}`
  } finally {
    loadingAi.value = false
    stopThinkingTips()
    await scrollChatToBottom()
  }
}

onMounted(() => {
  onAll()
})

onBeforeUnmount(() => {
  stopThinkingTips()
})
</script>

<template>
  <div class="screen">
    <section class="left-panel">
      <header class="panel-header">
        <h2>图书概览</h2>
        <el-button-group>
          <el-button :type="currentBookMode === 'hot' ? 'primary' : 'default'" @click="onHot">🔥 热门推荐</el-button>
          <el-button :type="currentBookMode === 'highScore' ? 'primary' : 'default'" @click="onHighScore">⭐ 高分好评</el-button>
          <el-button :type="currentBookMode === 'all' ? 'primary' : 'default'" @click="onAll">📚 全量列表</el-button>
        </el-button-group>
      </header>

      <div class="search-row">
        <el-input
          v-model="searchKeyword"
          clearable
          placeholder="模糊查询：书名 / 作者 / tags"
          @keyup.enter="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onResetSearch">重置</el-button>
      </div>

      <main class="table-wrap" v-loading="loadingBooks">
        <el-table
          :data="books"
          height="calc(100% - 112px)"
          stripe
          highlight-current-row
          @row-click="onRowClick"
        >
          <el-table-column prop="title" label="书名" min-width="220" />
          <el-table-column prop="author" label="作者" min-width="120" />
          <el-table-column prop="category" label="分类" min-width="120" />
          <el-table-column prop="score" label="评分" min-width="80" />
          <el-table-column prop="borrowCount" label="点击量" min-width="90" />
        </el-table>

        <div class="pager-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[5, 10, 20, 50]"
            :total="total"
            @current-change="onPageChange"
            @size-change="onPageSizeChange"
          />
        </div>
      </main>
    </section>

    <section class="right-panel">
      <header class="panel-header">
        <h2>AI 智能助手</h2>
      </header>

      <div v-if="loadingAi" class="ai-thinking">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ thinkingTips[thinkingTipIndex] }}</span>
      </div>

      <div ref="chatBodyRef" class="chat-body">
        <div
          v-for="(msg, idx) in chatMessages"
          :key="idx"
          class="bubble-row"
          :class="msg.role"
        >
          <div class="bubble" v-html="renderMarkdown(msg.content)" />
        </div>
      </div>

      <footer class="chat-input" v-loading="loadingAi">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入你的问题，比如：推荐几本评分高且适合新手的数据库书籍"
          @keydown.ctrl.enter.prevent="sendQuestion"
        />
        <div class="action-row">
          <span class="hint">Ctrl + Enter 发送</span>
          <el-button type="primary" :loading="loadingAi" @click="sendQuestion">
            发送给 AI
          </el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.screen {
  height: 100vh;
  display: flex;
  gap: 16px;
  padding: 16px;
  box-sizing: border-box;
  background: #f6f8fc;
  overflow: hidden;
}

.left-panel,
.right-panel {
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(10, 48, 108, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.left-panel {
  flex: 6;
  min-width: 0;
}

.right-panel {
  flex: 4;
  min-width: 0;
}

.panel-header {
  height: 72px;
  padding: 0 18px;
  border-bottom: 1px solid #edf1f8;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header h2 {
  margin: 0;
  color: #243b66;
  font-size: 18px;
}

.table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.search-row {
  height: 50px;
  padding: 0 14px;
  border-bottom: 1px solid #edf1f8;
  display: flex;
  align-items: center;
  gap: 10px;
}

.pager-wrap {
  height: 62px;
  padding: 0 14px;
  border-top: 1px solid #edf1f8;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.chat-body {
  flex: 1;
  min-height: 0;
  padding: 14px;
  overflow-y: auto;
  background: #eef3fb;
}

.ai-thinking {
  height: 34px;
  padding: 0 14px;
  border-bottom: 1px dashed #dbe7fb;
  background: #f5f8ff;
  color: #4a6aa3;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.bubble-row {
  display: flex;
  margin-bottom: 12px;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 82%;
  padding: 10px 12px;
  border-radius: 10px;
  line-height: 1.8;
  font-size: 14px;
  word-break: break-word;
  background: #ffffff;
  color: #2c3e50;
  text-align: left;
}

.bubble-row.user .bubble {
  background: #2f6bff;
  color: #fff;
  border-top-right-radius: 4px;
}

.bubble-row.assistant .bubble {
  border-top-left-radius: 4px;
  text-align: left !important;
}

.bubble-row.assistant .bubble :deep(h3) {
  margin: 10px 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: #2f6bff;
  padding-bottom: 4px;
  border-bottom: 1px solid #bcd1ff;
}

.bubble-row.assistant .bubble :deep(h4) {
  margin: 10px 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: #355a97;
}

.bubble-row.assistant .bubble :deep(p) {
  margin: 6px 0;
  line-height: 1.8;
  white-space: pre-wrap;
}

.bubble-row.assistant .bubble :deep(.md-empty) {
  height: 8px;
  margin: 0;
}

.chat-input {
  border-top: 1px solid #edf1f8;
  padding: 12px;
  background: #fff;
}

.action-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hint {
  font-size: 12px;
  color: #7d8aa8;
}

.chat-body::-webkit-scrollbar,
:deep(.el-scrollbar__bar) {
  width: 0;
  height: 0;
}
</style>

