# 术语与用词词汇表（glossary）

| | |
| --- | --- |
| Status | Active |
| 生效日期 | 2026-08-15 |
| 适用范围 | `source/_posts/` 中新建及修改的文章正文、标题、frontmatter |
| 不适用 | 已发布旧文不回溯改写；代码块、命令行、路径、包名、API 字段名、tag 保留技术原样 |
| 读者 | 为本站撰稿的作者与 AI Agent（Claude Code / ZCode 等） |

## 0. 执行协议（AI Agent 先读这里）

1. 写或改任何文章前，按第 1–3 节查表选词；表中没有的概念，遵循「官方名称优先」：以厂商官网、标准组织（全国科学技术名词审定委员会、W3C、IETF）的写法为准。
2. 首次出现的缩写用「全称（缩写）」括注一次，之后用缩写或简称。
3. 术语在同篇文章内 MUST 保持一致；不同文章之间 SHOULD 与本表一致。
4. 本文档生效前发布的旧文章 MUST NOT 按本表批量改写，仅在作者明确要求时修改。

## 1. AI 领域术语

规范依据：全国科技名词委推荐译名（[cnterm.cn](http://www.cnterm.cn/)）、Anthropic 官方用法、IBM / NVIDIA / Google Cloud 中文官网通行译名。

| 概念 | 规范写法 | 禁用 / 避免 | 说明 |
| --- | --- | --- | --- |
| coding agent | 首次：AI 编程智能体（Claude Code / ZCode 等），后文简称 AI | 代理、AI 代理、编程代理 | 「代理」MUST ONLY 用于网络代理语境（见第 3 节），避免与 proxy 混淆 |
| agent（泛指） | 智能体 | 代理 | 全国科技名词委方向；IBM / NVIDIA / Google 中文官网通行 |
| 指称 AGENTS.md 生态 | Agent 规则文件、Agent 执行协议 | — | 指文件、协议专名时保留英文 Agent |
| artificial intelligence | 日常叙述用 AI；正式 / 科普语境可人工智能 | 一篇内中英两套混用 | |
| LLM | 首次：大语言模型（LLM），之后 LLM | 规范文本中单独用「大模型」 | |
| token | 首次：词元（token）；工程语境（token limit、API 字段）直接用 token | — | 全国科技名词委 2026-03 推荐译名 |
| prompt | 提示词 | 提示语 | 代码、字段名保留 prompt |
| RAG | 首次：检索增强生成（RAG），之后 RAG | — | |
| embedding | 嵌入；嵌入向量 | 向量嵌入 | 站内统一语序「嵌入向量」 |
| MCP | 首次：模型上下文协议（MCP），之后 MCP | — | Anthropic 协议；维基百科、IBM、阿里云、Red Hat 通行译名 |
| hallucination | 幻觉 | — | |
| AGI | 通用人工智能（AGI） | — | 全国科技名词委推荐译名 |
| AIGC | 人工智能生成内容（AIGC） | — | 同上 |
| skill（Anthropic 生态） | 小写 skill；正式标题可用 Agent Skills | 正文用 Skill、技能 | `SKILL.md`、skill 目录名保留原样 |
| progressive disclosure | 渐进式披露 | 渐进披露 | 首次出现括注英文原词 |
| single source of truth | 真相源 | 与 SSOT 混用 | 首次可括注 SSOT，之后统一中文 |

## 2. 品牌 / 产品名大小写（正文）

规则：正文一律使用官方大小写；命令行、包名、tag、路径、镜像名中的小写是正确用法，不算违规。表中「站内错例」取自本站已发布文章，供自查。

| 规范写法 | 站内出现过的错例 |
| --- | --- |
| PostgreSQL（首次全称；简称 PG 需先声明） | Postgres、正文小写 postgresql |
| GitLab | Gitlab |
| MongoDB | Mongodb |
| TypeScript | Typescript |
| Node.js | node.js、NodeJS |
| Next.js | 正文小写 next.js |
| gRPC | GRPC、正文小写 grpc |
| gRPC-Web | gRPC-WEB |
| Protobuf（叙述）；proto3 / proto2（版本号，无空格） | proto 3 |
| Rust / React / JavaScript | — |
| DeepSeek | deepseek |
| Ollama | ollama（仅命令行可小写） |
| OpenAI | openai |
| NVIDIA | Nvidia |
| macOS | MacOS |
| Docker / Docker Compose | 正文小写 docker |
| Hugging Face | huggingface |
| Elasticsearch | ElasticSearch |
| SQLite | 正文小写 sqlite |
| Excel / Word / PPT | excel |
| Milvus / Playwright / Crawl4AI | 正文小写 crawl4ai |
| Claude Code / ZCode / Codex | zcode |
| GLM Coding Plan / Kimi Coding Plan | glm、kimi（正文） |
| tmux / nginx | 不要大写（小写即官方写法） |
| EPUB（标准名） | 文件名、skill 名保留小写 epub |

## 3. 架构与通用工程术语

| 概念 | 规范写法 | 说明 |
| --- | --- | --- |
| microservice | 微服务 | 正文不用英文 |
| proxy（网络语境） | 反向代理、用户代理（User-Agent） | 「代理」唯一保留的合法语境 |
| single sign-on | 单点登录 | |
| BFF / DAG / CRON | 缩写可直接用，首次括注全称 | |
| 通用工程概念 | 分布式、集群、高可用、序列化 / 反序列化 | 已是稳定中文，不再夹英文 |

## 4. 排版规则

依据：[中文文案排版指北](https://github.com/sparanoid/chinese-copywriting-guidelines)、W3C《中文排版需求》（[clreq](https://www.w3c/TR/clreq/)）。

- 中英文之间、中文与数字之间加空格：`AI 编程`、`208 个 commit`、`7B 模型`；缩写词同样遵守：`HTML 格式`、`文章 ID`、`JSON 存储`。
- 全角标点与其它字符之间不加空格。
- 标点以全角为主：，。：？！（）、；省略号用……；范围可用 ～。
- 引号：中文引语用直角引号「」，嵌套『』；单独标注英文单词可用半角引号（如 "must"）。弯引号 “” 不再用于新文章。
- 书名、文章名用《》。
- 代码、命令、路径、文件名用反引号包裹。

## 5. 文件名与 frontmatter

- 文件名：`YYYY-MM-DD-标题连字符分词.md`；专名保留大小写（Rust、gRPC）；点号省略（Next.js → Next-js）；标题内冒号用全角「：」。
- frontmatter 遵循 `scaffolds/post.md`：title、date、category、tags；tags 一律小写。

## 6. 参考来源

- 全国科学技术名词审定委员会：[cnterm.cn](http://www.cnterm.cn/)、术语在线 termonline.cn（token→词元、AGI→通用人工智能、AIGC→人工智能生成内容）
- Anthropic：[Equipping agents for the real world with Agent Skills](https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills)（Agent Skills、progressive disclosure）
- IBM：[什么是 AI agent（智能体）](https://www.ibm.com/cn-zh/think/topics/ai-agents)；Google Cloud：[什么是智能体编码](https://cloud.google.com/discover/what-is-agentic-coding?hl=zh-CN)；NVIDIA 术语表
- 模型上下文协议（MCP）通行译名：[中文维基百科](https://zh.wikipedia.org/zh-cn/模型上下文协议)、[IBM](https://www.ibm.com/cn-zh/think/topics/model-context-protocol)、[阿里云](https://help.aliyun.com/zh/model-studio/mcp-introduction)
- [sparanoid/chinese-copywriting-guidelines](https://github.com/sparanoid/chinese-copywriting-guidelines)（中文文案排版指北）
