# AGENTS.md — 羊八井花园

杨景的个人技术博客（Hexo 静态站点，发布到 GitHub Pages yangjing.github.io）。构建命令见 `package.json`，站点配置见 `_config.yml`；本文档只写代码与配置无法表达的约束。

## 写文章

- 新文章放 `source/_posts/`，文件名与 frontmatter 惯例参照 `scaffolds/post.md` 与近期文章。
- 用词与排版遵循 `docs/specs/glossary.md`（术语表），动笔前查表。
- 已发布旧文章不按词汇表回溯改写；作者点名修改时只改点名内容，不顺手重排其它。
- 文章完成后先 `pnpm server` 本地预览，确认渲染、链接、代码块无误再提交。

## 仓库约束

- `weeks/`、`gliffy/`、`project/`、`examples/` 是历史资料，与博客构建无关，不要修改。
- `themes/hueman/` 与 `scaffolds/` 影响全站渲染，改动前需作者确认。
- 个人仓库：直接在 main 分支提交，无 PR 流程。

## Git 与发布

- commit 规范见 `.agents/skills/committing/SKILL.md`（可用 `/committing`）；message 描述用中文。
- `pnpm deploy` 推送 `public/` 到 GitHub Pages，属对外发布动作，仅在作者明确要求时执行。
