title: 写了两款 Agent Skills：电子书阅读与翻译
date: 2026-07-30 08:45:28
category: ai
tags: [claude-code, epub, ai, skill, ebook]

---

最近两年技术书的出版速度明显更快了，一个新概念从落地到成书，过去怎么也得一年，现在几个月就出来了。书多了，根本读不过来。

之前读英文原版，三四百页的书零碎时间读，读完一章忘一章，最后记住的也就几个核心概念。后来琢磨：AI 编程工具既然能读代码、写代码，能不能帮我读书？

于是写了两款 agent skill：[ebook-ai-notes](https://github.com/yangjing/skills/tree/main/skills/ebook-ai-notes) 做读书笔记，[translate-epub](https://github.com/yangjing/skills/tree/main/skills/translate-epub) 做全书翻译，放到 GitHub 上了。Claude Code 能用，zcode、codex、kimi、opencode 这些兼容 agent skills 的工具也一样用。

## 先读后译

我的习惯是先读后译。

不是每本书都值得全文翻译。先用 ebook-ai-notes 跑一遍，知道这本书在讲什么、哪些章节有干货，再决定要不要译。笔记阶段生成的术语表能直接喂给 translate-epub，翻译时不会出现同一术语前后译法不一致——这是技术书翻译最容易翻车的地方。而且读完笔记，你对全书框架有了概念，翻译时判断语境也更准。

反过来也行，想快速拿到一本双语版随时翻看，直接上 translate-epub 就好。

## ebook-ai-notes

给一本书，输出一套笔记，风格仿微信读书 AI 大纲。

用法很简单，装好 skill 后一句话：

```
使用 ebook-ai-notes skill，读这本书：/path/to/book.epub
```

同时支持 epub 和 pdf。不需要额外配置，但 epub 不能有 DRM 保护——这是所有 epub 工具的通用前提。

跑完输出 `notes/<书名>/` 目录，如：

```
notes
└── ai-agents-action-intelligent-workflows-2nd
    ├── _glossary.md            # 术语表
    ├── 01-智能体的崛起.md
    ├── 02-核心组件-LLM-提示与智能体.md
    ├── 03-用MCP为智能体赋能动作.md
    ├── 04-多智能体系统的架构与构建.md
    ├── 05-智能体的推理与规划.md
    ├── 06-为智能体处理记忆与知识RAG.md
    ├── 07-用评估与反馈构建鲁棒智能体.md
    ├── 08-部署智能体与智能体系统.md
    ├── 09-理解智能体循环.md
    ├── 10-会思考的认知智能体.md
    ├── 11-构建智能体系统的实战建议.md
    ├── README.md               # 全书总览
    ├── 附录A-配置示例代码仓库.md
    └── 附录B-为本地MCP服务器配置Node-js.md
```

`README.md` 相当于整本书的摘要：讲什么、核心概念、各章一句话、最值得带走的观点、阅读建议。只有 5 分钟就看这个。

每章笔记包括：一句话精要、本章覆盖、分节精要（图表和代码也会被提取出来）、术语表、三五条金句观点。

我拿它读了四五本技术书，说几个体会。

笔记的信息密度比我预期高。它不是那种「本章讲了 A、B、C」的摘要，而是把论证路径也保留了——概念定义、论证要点、关键证据（图表和代码）都在。读完笔记大概知道作者怎么论证的，不光是个结论。

术语表要自己过一遍。AI 拟的初版大多合理，但作者自创的概念词偶尔会翻车。跑完花一两分钟扫一眼术语表，不合适的改掉。之后写文章、做方案时查起来很方便。

pdf 支持不如 epub。epub 有清晰的 HTML 结构，提取质量高；pdf 碰到双栏排版或者扫描页偶尔丢信息。日常用优先 epub。

一本 12 章、每章 20-30 页的书，完整跑下来大概 5-10 分钟。

## translate-epub

把 epub 全文翻译成中文，默认输出双语版——原文一段、译文一段交替排列。也可以只要译文。

市面上翻译 epub 的工具不少，我试过的普遍几个毛病：排版乱掉，代码块和表格尤其容易翻车；术语飘移，同一概念前后译法不一致；代码被翻译，函数名和 API 名变成中文没法用。再加上订阅制，按月按年付费，偶尔翻一两本很不划算。

translate-epub 跑在我已有的 coding plan（比如 glm coding plan、kimi coding plan）上，不用额外付费。

同样一句话启动：

```
使用 translate-epub skill，翻译这本 epub：/path/to/book.epub
```

首次使用会问：目标语言、要不要双语、要不要打包成 .epub。我一般默认——简体中文、双语、不打包（解压目录留着方便校对）。

翻译中途断了可以续，每章翻完就写盘了。

输出在原书目录旁边：

```
book.epub           # 原书
book-dual/          # 双语版
├── GLOSSARY.md      # 翻译术语库
└── OEBPS/
    ├── Styles/      # CSS 原样保留
    ├── Images/      # 图片原样保留
    └── Text/        # 翻译后的 HTML
```

进入 `book-dual/` 目录，使用 `python -m http.server` 启动本地服务器，然后在浏览器中打开 `http://localhost:8000/OEBPS/Text/Chapter_1.xhtml`（注意：不同书籍的实际 url 地址可能不同）即可直接阅读，效果如下：

![浏览器阅读效果](img/2026/openclaw-architecture-dual.png)

打包成 .epub 后导入阅读器就行，Kindle、Apple Books、iReader、微信读书都能打开。效果跟看双语字幕差不多——眼睛随时跳回原文确认。

如果前面用 ebook-ai-notes 读过了，把术语表路径告诉它，术语一致性会更好。没读过也没关系，skill 会自己建，只是多花一两分钟。

翻译了三四本书，说几个体会。

表格翻译是惊喜。技术书的表格往往是全书最浓缩的对比信息，以前读英文原版经常跳过去。双语版把原表格保留、译表格追加在下方，对比着看效率高很多。

代码安全。全程不碰 `<code>` 和 `<img>` 标签，API 名、函数名、架构图都原样保留。这是底线要求，但很多通用翻译工具确实做不到。

阅读器兼容性还行。Apple Books、微信读书、Kindle（个人文档服务）都正常。Apple Books 对 XHTML 规范最严，踩过 `<ul>` 里混进 `<p>` 标签导致渲染异常的坑，后来加了每章翻译完自动验证。

长书先试翻一章。20 章以上的大块头，先指定翻第一章看术语译法对不对，确认了再全书翻译。翻完回头改术语，比重翻还费时间。

一本 300 页左右的书，完整翻译大概 15-30 分钟。

## 配合使用

用了一段时间，觉得比较顺的流程：

```
1. ebook-ai-notes 出笔记 → 5-10 分钟了解全书
2. 判断值不值得翻译。不值得？笔记够了。
3. 值得 → 把术语表喂给 translate-epub
4. translate-epub 全书翻译 → 拿到双语 epub
```

术语表是这两个 skill 之间的桥梁。笔记阶段定的译法给翻译阶段用，省一遍对齐时间。反过来也成立——先翻译再出笔记，术语表一样能复用。

## 安装

依赖 `uv`（Python 包管理器），没装的话：

```bash
brew install uv      # macOS
# 或者
pip install uv
```

安装 skill：

```bash
npx skills add https://github.com/yangjing/skills --skill ebook-ai-notes
npx skills add https://github.com/yangjing/skills --skill translate-epub
```

脚本用 PEP 723 内联声明依赖，uv 自动创建隔离环境，不用手动 `pip install`。

## 不替代阅读

说清楚一点：这两个 skill 不替代逐字阅读。它们解决的是书太多读不过来——帮你在有限时间里把最重要的部分筛出来。笔记给你骨架和关键观点，双语版让你需要时能深入原文。

要不要精读、精读到什么程度，自己把握。但一本 300 页的英文技术书从拿到文件到拥有一份中文笔记加一本双语 epub，大概半小时。而以前，这个过程可能需要很长时间。这在两年前不太敢想。
