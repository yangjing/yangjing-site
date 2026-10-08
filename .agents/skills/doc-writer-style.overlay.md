---
overlay-for: doc-writer-style
purpose: 登记 yangjing-site（Hexo 7.3.0）对 doc-writer-style skill 的站点取值：frontmatter 格式、文件名、分类词表
ssot: 本文件只登记站点侧取值与指针；通用写作规则留在 skill 正文与 references/，MUST NOT 在此复制
---

# yangjing-site — doc-writer-style 站点 overlay

## 1. 站点事实

- 博客系统：Hexo 7.3.0（frontmatter 解析器 hexo-front-matter 4.2.1，两种分隔写法均兼容，见 [references/hexo-frontmatter.md](doc-writer-style/references/hexo-frontmatter.md)）
- 文章目录：`source/_posts/`；`_config.yml` 配置 `new_post_name: :year-:month-:day-:title.md`
- 风格语料：`source/_posts/` 下 140+ 篇已发布文章（2010-2026）

## 2. frontmatter（明确要求：新文章沿用存量格式）

不以 `---` 开头，直接 `title:` 起始，字段顺序固定 title → date → category → tags，结尾用单个 `---` 与正文分隔，`---` 前后各留一个空行：

```
title: Milvus 向量数据库实践入门
date: 2025-07-23 09:31:35
category: ai
tags: [milvus, postgresql, qwen3, embedding]

---
```

- category 单数，从已有分类中选：scala、java、rust、data、ai、essay、work、programming、akka、pulsar；历史另有 spark、elasticsearch、cassandra、postgresql、unix/linux，确有需要可新增
- tags 用小写英文；随笔可留空（`category:`、`tags:` 后不填）
- Hexo 7.3.0 实测对标准 `---` 包裹格式同样兼容；如决定新文章改用标准格式，改本节登记即可，skill 正文无需动

## 3. 文件名

`source/_posts/YYYY-MM-DD-标题.md`，标题保留中文原词、词间用 `-` 连接（如 `2025-08-22-AI-协作编程-SOP：架构师驱动-结对编程模式.md`）；翻译文的"译-"前缀同样进文件名。
