title: Akka HTTP 非官方中文翻译
date: 2020-04-06 14:54:36
category:
  - 作品
  - akka-http-chinese
tags:
  - akka-http
  - akka
  - scala
  - akka-http-chinese
---

Akka HTTP 10.1.11 非官文中文翻译在线阅读地址：

- 中文文档：[Akka HTTP Unofficial Chinese](https://www.yangjing.me/akka-http/)
- 码云镜像：[Akka HTTP Unofficial Chinese](https://yangjing.gitee.io/akka-http/)


翻译难免有错误或表达不够清楚的地方。因此，Akka HTTP 中文版翻译采用了基于原始 Paradox 的 md 文件对照翻译的形式，英文原文将显示在中文译文的上方。翻译的源码内容可以在 https://github.com/yangjing/akka-http 仓库的 docs-zh 子项目找到。

欢迎大家指出或改进不好的地方，一起完善并在未来跟进官方版本的更新。欢迎随时编辑并提交 Pull Request。

本次翻译完成了 Scala API 文档和大部分 Java API 文档的中文译文，但有关具体指令（Directives）使用说明的内容还未翻译。接下来除了继续完成剩余的 Java API 文档翻译以外，也将考虑优先挑选翻译常用及重要的指令，另外某些代码示例的注释也会考虑进行中文翻译。

Akka HTTP 模块组在 akka-actor 和 akka-stream 的基础上实现了全HTTP栈（ 服务器-和客户端 ）的功能。它并不是一个 web 框架，而是一个更通用的工具箱，以便生成提供或消费基于 HTTP 的网络服务。虽然与浏览器进行互动是其功能的组成部分，但这个并不是 Akka HTTP 的 主要目的（简单来说，别把 Akka HTTP 模组只当作页面服务器）。Akka HTTP 支持 HTTP 1.1/2.0、WebSocket、SSE等，并默认提供回压功能。

**欢迎随时编辑并提交 Pull Request。社区建设靠大家。**

***Long live Scala!***
