---
title: AI Coding 与 tmux 实践
date: 2026-04-03 14:30:00
category: work
tags: [tmux, ai, claude-code, codex, kimi-code, terminal]
---

命令行 AI Agent（Claude Code、Codex、Kimi-Code 等）的长时间运行场景下，网络中断或终端关闭会导致任务失败。_传统 `nohup` 无法处理交互式确认，Agent 会卡死_。tmux 的会话持久化机制是生产环境的最佳实践。

---

## 核心问题：AI Agent 的会话管理困境

命令行 AI Agent 具有三个关键特征：

1. **长时运行**：代码重构、技术债清理、大规模迁移等任务耗时数小时至数天
2. **交互依赖**：文件系统变更、命令执行、敏感操作需人工确认（y/n/e）
3. **输出密集**：实时日志、代码差异、错误堆栈等信息流持续输出

### 传统方案的局限性

| 方案     | 致命缺陷                       | 适用场景         |
| -------- | ------------------------------ | ---------------- |
| 前台运行 | 网络抖动/SSH 超时导致任务中断  | 仅适合短任务     |
| `nohup`  | 标准输入断开，Agent 确认时死锁 | 非交互式批处理   |
| `screen` | 脚本能力弱，生态工具匮乏       | 简单后台任务     |
| systemd  | 服务化配置重，调试困难         | 生产环境守护进程 |

### tmux 的技术优势

- ✅ **会话持久化**：进程与终端解耦，SSH 断开后继续运行
- ✅ **交互式重连**：随时 attach 回会话处理确认、查看进度
- ✅ **布局管理**：分屏监控资源占用、Git 状态、测试结果
- ✅ **脚本化**：可编程管理多个 Agent 实例，适合 CI/CD 集成

---

## tmux 核心概念

三个层级的关系：

```text
Session（会话）
  └─ Window（窗口）
      └─ Pane（窗格）
```

- **Session**：一个独立的工作环境，包含多个窗口
- **Window**：类似于浏览器标签页
- **Pane**：窗口内的分屏区域

最简单的理解：Session 是一个"容器"，即使你离开了（detach），里面的程序仍在运行。

---

## 基础操作：5 分钟上手

### 1. 创建和连接会话

```bash
# 创建命名会话（最佳实践：语义化命名）
tmux new -s claude-refactor
tmux new -s codex-migration
tmux new -s kimi-cleanup

# 在会话中启动对应的 AI Agent
# Claude Code
claude --permission-mode acceptEdits

# Codex
codex --auto-confirm

# Kimi-Code
kimi-code --interactive
```

### 2. 分离会话（让 Agent 后台运行）

**快捷键**：`Ctrl+B` -> `d`（小写 d）

这是最常用的操作，务必记住。按下后你会回到普通终端，但 Agent 仍在后台运行。

### 3. 查看和重连会话

```bash
# 列出所有后台会话
tmux ls

# 输出示例：
# my-agent: 1 windows (created Fri Apr  3 14:30:00 2026)

# 重新连接到会话
tmux attach -t my-agent

# 如果只有一个会话，可以省略名字
tmux attach
```

### 4. 关闭会话

```bash
# 在会话内部直接退出
exit

# 或者从外部杀死会话
tmux kill-session -t my-agent
```

---

## 高频快捷键速查

默认前缀键：`Ctrl+B`（先按 Ctrl+B，松开后再按其他键）

### 会话管理

| 功能         | 快捷键             | 说明               |
| ------------ | ------------------ | ------------------ |
| 分离会话     | `Ctrl+B` -> `d`    | **最常用**         |
| 查看所有会话 | `Ctrl+B` -> `s`    | 可切换到其他会话   |
| 创建新窗口   | `Ctrl+B` -> `c`    | 类似浏览器新标签页 |
| 切换窗口     | `Ctrl+B` -> `数字` | 切换到第 N 个窗口  |

### 分屏操作

| 功能             | 快捷键             | 记忆提示           |
| ---------------- | ------------------ | ------------------ |
| 垂直分屏（左右） | `Ctrl+B` -> `%`    | 竖线 `\|` 需 Shift |
| 水平分屏（上下） | `Ctrl+B` -> `"`    | 双引号需 Shift     |
| 切换窗格         | `Ctrl+B` -> 方向键 | 直觉操作           |
| 关闭当前窗格     | `Ctrl+B` -> `x`    | 会提示确认         |

---

## 生产实践：自动化 Agent 工作环境

### 多 Agent 协同脚本

创建 `tmux-agents.sh`（支持参数化启动不同 Agent）：

```bash
#!/bin/bash
set -e

AGENT=${1:-claude}  # 默认 claude，支持 codex/kimi-code
PROJECT=${2:-$(basename $(pwd))}
SESSION="${AGENT}-${PROJECT}"

# 会话存在性检查
if tmux has-session -t $SESSION 2>/dev/null; then
  echo "Session $SESSION already exists. Attaching..."
  tmux attach -t $SESSION
  exit 0
fi

# 创建会话并启动 Agent
tmux new-session -s $SESSION -d

# 主窗格：运行 AI Agent
case $AGENT in
  claude)
    tmux send-keys -t $SESSION 'claude --permission-mode acceptEdits' C-m
    ;;
  codex)
    tmux send-keys -t $SESSION 'codex --workspace .' C-m
    ;;
  kimi-code)
    tmux send-keys -t $SESSION 'kimi-code' C-m
    ;;
esac

# 右侧窗格：实时监控
tmux split-window -h -t $SESSION
tmux send-keys -t $SESSION 'watch -n 1 "git status --short && echo && git diff --stat"' C-m

# 底部窗格：资源监控
tmux split-window -v -t $SESSION
tmux send-keys -t $SESSION 'htop' C-m

# 切回主窗格
tmux select-pane -t $SESSION -L
tmux select-pane -t $SESSION -U

# 附加到会话
tmux attach -t $SESSION
```

使用方法：

```bash
chmod +x tmux-agents.sh

# 启动不同 Agent
./tmux-agents.sh claude my-project
./tmux-agents.sh codex legacy-refactor
./tmux-agents.sh kimi-code codebase-cleanup
```

布局效果：

```text
┌───────────────────────────┬─────────────────────┐
│                           │                     │
│  AI Agent 主交互区         │  Git 状态实时监控    │
│  (Claude/Codex/Kimi)      │  (变更统计)          │
│                           │                     │
│                           ├─────────────────────┤
│                           │                     │
│                           │  系统资源监控        │
│                           │  (htop)             │
└───────────────────────────┴─────────────────────┘
```

---

## 生产场景实战

### 场景 1：跨时区长时间重构

**需求**：下班前启动大规模重构，次日上班验收结果

```bash
# 创建专用会话
tmux new -s legacy-refactor

# 启动 Claude Code（自动接受文件变更）
claude --permission-mode acceptEdits
> Refactor all DAO classes to use repository pattern, 
> add comprehensive unit tests, maintain backward compatibility

# 关键操作：分离会话
# Ctrl+B -> d

# 次日从家里检查进度
ssh office-gateway
tmux attach -t legacy-refactor

# 验证后清理会话
tmux kill-session -t legacy-refactor
```

### 场景 2：多仓库并行处理

**需求**：同时用不同 Agent 处理多个微服务

```bash
# 服务 A：使用 Codex 处理 API 迁移
tmux new -s codex-service-a
cd ~/microservices/service-a
codex --task "Migrate REST API to GraphQL"

# 服务 B：使用 Kimi-Code 处理性能优化
tmux new -s kimi-service-b
cd ~/microservices/service-b
kimi-code --focus performance

# 服务 C：使用 Claude Code 处理依赖升级
tmux new -s claude-service-c
cd ~/microservices/service-c
claude --permission-mode auto "Upgrade all dependencies to latest stable"

# 统一监控所有会话
tmux ls
# 输出：
# codex-service-a: 1 windows
# kimi-service-b: 1 windows  
# claude-service-c: 1 windows

# 快速切换：Ctrl+B -> s
```

### 场景 3：远程服务器无人值守任务

**需求**：在跳板机上执行长时间数据库迁移

```bash
# 在跳板机创建会话
ssh jump-server
tmux new -s db-migration

# 启动迁移脚本（假设脚本内部调用 AI Agent 辅助处理异常）
./smart-migration.sh --agent claude --target production-replica

# 分离后断开 SSH
# Ctrl+B -> d
exit

# 12小时后重新检查
ssh jump-server
tmux attach -t db-migration

# 如果需要多人协作查看进度
# 第二个开发者执行：
tmux attach -t db-migration -r  # 只读模式
```

---

## 保活要点：合盖不断电

**重要区分**：tmux 只保证会话不因 SSH 断开或终端关闭而退出；**不能阻止操作系统睡眠**。

如果笔记本合盖后进入睡眠，CPU 停止，tmux 中的 Agent 也会暂停。

### 配置操作系统保持唤醒

**Windows**：

```
控制面板 → 硬件和声音 → 电源选项
→ 选择关闭盖子的功能
→ 接通电源时：不采取任何操作
```

**Linux**（systemd）：

编辑 `/etc/systemd/logind.conf`：

```ini
HandleLidSwitch=ignore
HandleLidSwitchDocked=ignore
```

重启服务：

```bash
sudo systemctl restart systemd-logind
```

**macOS**：

- 官方支持：合盖模式（Clamshell）需同时满足 **电源 + 外接显示器 + 外接键鼠**
- 第三方工具：Amphetamine 等可阻止休眠（注意散热）

---

## 配置优化（可选）

创建 `~/.tmux.conf` 提升体验：

```bash
# 启用鼠标支持（点击切换窗格、滚轮查看历史）
set -g mouse on

# 从 1 开始编号窗口（默认从 0）
set -g base-index 1

# 关闭窗口时自动重新编号
set -g renumber-windows on

# 增加历史记录大小
set -g history-limit 50000

# 修改分屏快捷键（更直观）
bind | split-window -h  # Ctrl+B -> | 垂直分屏
bind - split-window -v  # Ctrl+B -> - 水平分屏
```

重新加载配置：

```bash
tmux source ~/.tmux.conf
```

---

## 小结

### 技术要点

1. **会话持久化**：tmux 实现进程与终端解耦，解决 SSH 不稳定和长时间任务问题
2. **交互式管理**：支持运行时重连、实时确认、进度监控
3. **生产级脚本**：参数化启动、多 Agent 支持、自动化布局
4. **系统级保障**：操作系统电源管理 + tmux 会话管理双重保险

### 最佳实践

- **命名规范**：`{agent}-{project}-{task}` 格式便于管理
- **监控集成**：分屏运行 htop、git watch、日志监控
- **会话复用**：避免重复创建，使用 `has-session` 检查
- **优雅退出**：任务完成后及时 `kill-session` 释放资源

---

## 延伸阅读

- [tmux 官方手册](https://github.com/tmux/tmux/wiki)
- [Claude Code 高级配置](https://docs.anthropic.com/claude-code)
- [Codex CLI 文档](https://github.com/openai/codex)
- [Kimi-Code 使用指南](https://platform.moonshot.cn/docs/kimi-code)
