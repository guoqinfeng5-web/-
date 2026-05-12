你是一个资深的 Java 后端开发工程师。请帮我从零搭建一个基于 Spring Boot 的后端基础框架，用于后续开发“图书管理与 AI 分析系统”。

**一**

【1. 技术栈与版本号要求】
- Java 版本：JDK 17 (请务必使用 Java 17 的语法特性)
- Spring Boot 版本：3.2.x
- ORM 框架：MyBatis-Plus (请使用兼容 Spring Boot 3 的 mybatis-plus-spring-boot3-starter)
- 数据库：MySQL 8.0
- 辅助工具：Lombok, Hutool
- 接口文档：Knife4j (请使用兼容 Spring Boot 3 的 knife4j-openapi3-jakarta-spring-boot-starter)

【2. 需要生成的配置文件】
请生成规范的 pom.xml 依赖配置和 application.yml 配置文件。

application.yml 基础配置如下：
server:
  port: 8080
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:
    username: root
    password:
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    map-underscore-to-camel-case: true

【3. 项目包结构要求】
顶级包名为：com.demo
请按照标准 MVC 架构创建规范的包目录结构：controller, service (包含 impl 子包), mapper, entity, config, common。

**二**
请根据这些 SQL 语句，在对应的包（entity, mapper, service, controller）下为我生成完整的后端代码：
1. 要求使用 MyBatis-Plus 的注解（如 @TableName, @TableId）。
2. 使用 Lombok 简化 Entity 类。
3. Controller 层需要包含标准的 CRUD 接口（增删改查），特别是分页查询功能。
4. 所有的接口返回对象必须使用之前定义的 com.smartlibrary.common.Result 类进行封装。
【SQL 语句如下】：
-- 1. 书籍表：存储书籍核心信息
CREATE TABLE `books` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(255) NOT NULL COMMENT '书名',
    `author` VARCHAR(100) COMMENT '作者',
    `category` VARCHAR(50) COMMENT '分类',
    `price` DECIMAL(10, 2) COMMENT '价格',
    `score` DOUBLE DEFAULT 0.0 COMMENT '评分',
    `summary` TEXT COMMENT '书籍详细简介（AI分析的关键素材）',
    `tags` VARCHAR(255) COMMENT '标签，逗号隔开',
    `stock` INT DEFAULT 10 COMMENT '库存',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    FULLTEXT KEY `idx_fulltext_summary` (`summary`) -- 为后续模糊搜索预留全文索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 2. 用户表：基础信息与权限
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色: USER-普通用户, ADMIN-管理员',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 3. 用户活动记录表：用于分析用户偏好
CREATE TABLE `borrow_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `book_id` BIGINT NOT NULL COMMENT '关联的书籍ID',
    `user_id` BIGINT NOT NULL COMMENT '借阅人ID',
    `borrow_time` DATETIME NOT NULL COMMENT '借出时间',
    `return_time` DATETIME COMMENT '归还时间',
    PRIMARY KEY (`id`)
);

**三**
后端基础架构已就绪。现在我们要实现核心课题：“AI 辅助分析系统”。
请帮我引入大模型能力，实现一个后端的智能分析接口。
【1. 依赖引入】
请在 pom.xml 中引入以下依赖（请务必使用与 Spring Boot 3 兼容的 1.x 版本）：
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.0.0-alpha1</version> </dependency>
【2. 配置文件修改 (application.yml)】
请帮我在 application.yml 中追加大模型的配置（由于使用的是兼容 OpenAI 格式的 ModelScope 接口，请按如下配置）：
langchain4j:
  open-ai:
    chat-model:
      base-url: "https://api-inference.modelscope.cn/v1/"
      api-key: 
      model-name: "Qwen/Qwen3.5-35B-A3B"
      log-requests: true
      log-responses: true
【3. 业务代码要求 (RAG 流程)】
请新建 AiController 和 AiService。
实现接口：GET /api/ai/analyze
接收参数：用户的自然语言提问 (String question)
核心处理逻辑：
1. 数据检索：调用已有的 BookService，查询数据库中所有的书籍列表。
2. 组装上下文：提取查到的书籍 title, author, category 和 summary 字段，拼接成一段纯文本（Context）。
3. 构建 Prompt：向大模型发送如下指令：“你是一个资深的图书推荐助手。以下是目前数据库中的可用书籍信息：\n{Context}\n\n请根据用户的提问：‘{question}’，从上述书籍中挑选最合适的一本或多本，并给出 200 字左右的专业分析与推荐理由。”
4. 调用模型：使用 Spring 自动注入的 ChatLanguageModel 调用大模型获取分析结果。
5. 返回数据：将大模型的回答使用我们项目中的 com.smartlibrary.common.Result 统一封装返回。
请一步步帮我修改 pom.xml、application.yml 并生成 Controller 和 Service 的代码。

**四**
当前 UI 已跑通

 【1.后端 Prompt 升级】：请修改 AiService，要求大模型输出结果必须采用 Markdown 格式。
   - 必须包含：### 🎯 核心分析、#### 💡 推荐理由、#### 👤 适合人群。
   - 语气要专业且热情。

 【2.前端回复区优化】：
   - 引入“打字机”效果：AI 的文字要像 ChatGPT 那样逐字显示。
   - 在 AI 回复区的顶部加一个微型 Loading 状态，文字滚动播放：“正在检索数据库...”、“正在分析语义...”、“AI 正在组织语言...”。

 【3.引用标记*】：
   - 如果 AI 推荐了某本书，要求它在书名后加上 [数据来源：馆藏库]，增加可信度。

请帮我修改 App.vue 和 AiService.java。

**五**
目前的 AI 聊天界面存在两个严重的交互问题，请帮我修复：

【1. 打字机逻辑修复】
- 现状：由于大模型响应慢，前端在等待时打字机逻辑混乱。
- 要求：
  1. 修改 sendMessage 方法。点击发送后，立刻在对话框中显示一条 AI 的占位消息：“🔍 AI 正在检索馆藏数据并进行深度分析，请稍候...”。
  2. 必须等 axios 请求彻底返回数据（await 结束）后，再清空占位消息，开始执行逐字显示的打字机效果。
  3. 设置打字机速度为 30ms/字，确保显示平滑。

【2. 视觉排版修复 (CSS)】
- 现状：AI 的回复文字全部居中显示，且 Markdown 标题（###）没有样式，看起来很乱。
- 要求：
  1. 强制设置 AI 消息气泡内的文字为“左对齐” (text-align: left !important)。
  2. 优化 AI 回复区域的样式：
     - 给 ### 标题设置加粗、蓝色字体和下划线效果。
     - 给 #### 标题设置加粗和略大的字号。
     - 增加行间距（line-height: 1.8），让长段落读起来不费力。
  3. 如果 AI 返回的内容包含 \n，确保在页面上能正确换行（使用 white-space: pre-wrap）。

【3. 结构化展示】
- 请确保 AI 返回的“核心分析”、“推荐理由”、“适合人群”这些部分能清晰地分段。

请给出 App.vue 的完整修改方案。

**六**

要基于 Redis 实现改项目具有“智能摘要与滑动窗口”机制功能。

【1. 依赖与配置】
- 请在 pom.xml 中添加 spring-boot-starter-data-redis 依赖。
- 在 application.yml 中进行配置Redis
要添加信息如下：
  redis:
    host: 127.0.0.1
    port: 6379
    password: 
    lettuce:
      pool:
        max-active: 10
        max-idle: 10
        min-idle: 1
        time-between-eviction-runs: 10s
- 请创建一个 RedisConfig 类，配置 RedisTemplate<String, Object>，序列化方式请使用 StringRedisSerializer 和 GenericJackson2JsonRedisSerializer。

【2. 核心逻辑：AiService 升级】
请重写 AiService.java 中的 analyze(String question) 方法，并实现以下“智能记忆管理”算法：

- 会话定义：由于目前是单用户 Demo，请暂时硬编码 sessionId = "demo_user_001"。
- 存储结构：使用 Redis 的 List 结构存储对话历史（格式为 "User:xxx" 和 "AI:xxx"）。
- 短期记忆窗口控制：
  1. 每次提问前，从 Redis 获取该 session 的所有历史记录。
  2. 计算所有历史记录的总字符长度。
  3. 触发压缩 (Sliding Window & Summary)
     - 如果总长度超过 32,0 字：
       - 取出 List 中“前一半”的消息。
       - 调用大模型（chatLanguageModel）执行一个内部指令：“请将以下对话内容总结为 300 字以内的核心摘要，保留关键的图书偏好信息：[此处填充前一半内容]”。
       - 在 Redis 中删除这前一半原始消息。
       - 将生成的“摘要”作为一条特殊消息存入 List 的头部。
- 上下文拼接：
  - 最终发送给大模型的 Prompt 结构为：
    [系统指令] + [RAG 馆藏背景(buildContext)] + [Redis 中的历史记忆(含摘要)] + [当前用户问题]。

【3. 健壮性要求】
- 确保在 Redis 宕机或连接失败时，系统能降级为“无记忆模式”继续工作，不要报错崩溃。
- 每次 AI 回答完成后，记得将本轮的 User 问题和 AI 回答异步（或同步）写入 Redis。

请给出完整的 RedisConfig.java 和修改后的 AiService.java 代码。

**七**

任务：按照“职责分离”原则，将现有的 AiService 重构为模块化的 RAG 架构，并集成阿里 DashScope 向量模型。

【1. 新增 VectorStoreService.java (情报部门)】
配置模型：使用 DashScopeEmbeddingModel，配置如下：

apiKey:

modelName: "text-embedding-v4"

baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1"

配置存储：使用 InMemoryEmbeddingStore<TextSegment>。

核心功能：

ingest(List<Book> books)：将图书数据转化为包含 title, author, category, price, summary 的特征文本并存入向量库。

search(String question, int k)：将问题向量化，从库中检索 Top K 个最相关的文本片段。

【2. 新增 ChatMemoryService.java (记忆部门)】
将 AiService 中关于 Redis 的历史记录加载 (loadHistorySafely)、保存 (saveTurnSafely) 和压缩 (compressHistoryIfNeeded) 逻辑全部搬迁至此类中。

【3. 重构 AiService.java 】
瘦身计划：删除 buildContext、safeOneLine 以及所有 Redis 处理方法。

核心逻辑更新：

注入 VectorStoreService 和 ChatMemoryService。

启动初始化：利用 @PostConstruct 在系统启动时调用 VectorStoreService.ingest(bookService.list()) 完成向量化（注意处理异常）。

分析逻辑重写：

调用 ChatMemoryService 获取并压缩历史。

动态检索：仅当意图识别判断为图书相关时，调用 VectorStoreService.search(question, 5) 获取背景。

组装 Prompt 并调用 chatLanguageModel。

调用 ChatMemoryService 异步保存当前对话。

【4.健壮性与后处理】
Chitchat 优化：如果向量检索结果的得分过低，或 isChitchatIntent 为真，引导 LLM 在 Prompt 中降低对馆藏背景的依赖。

标签处理：保留并优化 ensureSourceTagIfBookAnswer，确保只有当 AI 真正基于馆藏数据回答时，才在末尾追加数据来源标签。

**八**

Role:资深前端 UI/UX 设计师。
Tasl;现在需要全面优化前端书籍展示列表，解决当前排版杂乱、视觉层级弱、缺乏呼吸感的问题，直接输出可替换的完整 HTML/CSS/JS 代码及样式修改方案。
核心优化目标（严格按以下标准执行）
视觉层级重构：采用「卡片化 + 信息分层」设计，让用户一眼扫出重点（书名 > 评分 / 点击量 > 作者 / 分类），消除当前纯表格的枯燥感。
交互体验升级：增加卡片悬停动效、评分高亮标签、分类快捷过滤，提升页面活力与可操作性。
信息精简：移除冗余视觉元素，保留书名、作者、分类、评分、点击量 5 大核心信息，避免信息过载
【1. 布局结构（替换原有表格）】
整体采用 CSS Grid 网格布局，桌面端默认 4 列，平板 3 列，手机 1 列，卡片统一宽高，保证整齐有序。
页面分为 3 个模块：搜索区 + 分类过滤区 + 书籍列表区，模块间预留合理间距，增强视觉呼吸感。
【2. 书籍卡片设计（核心）】
单张卡片结构（从上到下）：
顶部标签栏：左侧显示分类标签（彩色圆角背景），右侧显示评分星级 + 评分数字（高分用橙色 / 红色突出，低分用灰色弱化）。
中间主体区：
书名：加粗、最大字号，2 行溢出隐藏（加省略号），保证视觉焦点。
作者：灰色小号字体，下方加分割线区分。
底部信息区：
左侧：分类标签（轻量级样式，避免抢镜）。
右侧：点击量（带眼睛图标，数字高亮）。
悬停动效：鼠标悬浮时，卡片上移 5px、阴影加深、缩放 1.02，增强交互反馈。

**九**

当用户使用NL2SQL的逻辑进行图书咨询或查看馆藏概况时，你必须基于查询到的数据，以“博学且温暖的图书馆员”身份提供以下维度的建议：

1. **阅读破圈建议**：
   - 观察数据中的“冷门分类”（如册数或种数最少的类别）。
   - 指令：如果发现用户关注的领域过于单一，引导其关注冷门但高质量的类别。
   - 话术示例：“我发现您很喜欢计算机类，其实我们馆藏的【哲学】分类下虽然书不多，但每一本都是精选，或许能为您提供不一样的思考视角。”

2. **资源利用建议**：
   - 观察库存（stock）数据。
   - 指令：针对库存充裕的类别，鼓励用户借阅；针对库存紧张的类别，提醒用户及时预约。
   - 话术示例：“目前【文学小说】类书籍库存非常充足，您可以尽情挑选；而【人工智能】类目比较热门，建议您看中后尽快下手哦。”

3. **阅读路径引导**：
   - 观察评分（score）或标签（tags）数据。
   - 指令：根据该分类下的高分书籍，给出一个简单的“进阶路径”。
   - 话术示例：“如果您想深入了解这个分类，建议先从评分 9.0 以上的经典入门书看起，比如馆里的《XXX》。”

4. **共情与关怀**：
   - 严禁机械回复。在建议的末尾，加上一句与图书馆氛围相符的暖心话语。
   - 话术示例：“书海浩瀚，希望你能在这里找到属于你的那盏明灯。”

### [全局约束]
- 所有的建议必须基于真实的 [馆藏数据] 或 [历史记忆]。
- 禁止空泛地推荐馆内不存在的书籍。
- 建议篇幅控制在 100-200 字之间，保持 Markdown 格式美观。
