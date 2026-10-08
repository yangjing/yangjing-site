title: AI 重写了 Adobe 全家桶：ArtCraft 与软件的出路
date: 2026-10-07 21:30:00
category: ai
tags: [ai, rust, adobe, opensource]

---

十一假期刷 Hacker News，撞见一条热闹帖子：有人用 Rust 把 Adobe 全家桶重写了一遍，开源、免费，不要订阅。我顺着点进 GitHub，PhotoCraft（对标 Photoshop）的仓库 9 月 30 日才创建，一周 1.4 万 star，七个仓库加起来两万四千多。

这个项目叫 ArtCraft，应用页在 [getartcraft.com/apps](https://getartcraft.com/apps)，标语是 "Seven apps. One craft."。

## 七个 Craft，对着 Adobe 打

页面很克制，通篇不提 Adobe；GitHub 仓库的描述倒是一个字不藏："An open-source, clean-room reimplementation of Adobe Photoshop in pure Rust"。

| 应用 | 功能 | 对标 | 状态 |
|---|---|---|---|
| PhotoCraft | 图像编辑 | Photoshop | 早期 alpha |
| VectorCraft | 矢量插画 | Illustrator | 开发中 |
| FilmCraft | 视频剪辑 | Premiere Pro | 开发中 |
| LightCraft | 照片管理与 RAW 显影 | Lightroom | 开发中 |
| PrintCraft | PDF 工作台 | Acrobat | 早期 alpha |
| EffectCraft | 动效与视觉特效 | After Effects | 开发中 |
| DesignCraft | 排版与出版 | InDesign | 开发中 |

七个仓库集中创建于 9 月 30 日到 10 月 1 日。macOS、Windows、Linux 安装包都齐，除 FilmCraft 外还能编译成 WebAssembly，扔进浏览器就能跑。

## Built the hard way

页面管这套原则叫 "Built the hard way"。我最认同两条：一是纯 Rust 原生编译，图形走 Metal、Vulkan、DirectX 12、WebGPU，明确写了 "No Electron, no web views"；二是全部本地运行，文件不过云，无遥测，不注册账号。另外两条也值得记一笔：快捷键和布局照着专业软件抄，上手不用重学；每个应用都能用 CLI、JSON 控制通道或 MCP server 驱动，官方管这个叫 agent-ready，后面细说。

PhotoCraft 的功能清单一看就是照着 Photoshop 说明书写的：打开保存带图层的 PSD/PSB，135 个测试文件里 134 个能 byte-for-byte 往返；16 种调整图层、70 多个实时预览滤镜、智能对象、完整图层样式；8/16/32 位文档，RGB、CMYK、Lab、灰度，ICC 带软打样；主体选择和蒙版细化在本地跑。

PrintCraft 稍微朴素些，但 PDF 该做的都列了：983 个测试文件渲染零崩溃，竖排日文、彩色 emoji 都支持，能开 RC4 到 AES-256 的加密文档，原子保存带崩溃恢复。

## 谁在做，图什么

联系邮箱是 storyteller.ai 的域名，GitHub 组织 [storytold](https://github.com/storytold) 里躺着他们 2021 年以来的仓库：UE 插件、动作捕捉、语音转换，有年头的技术团队，不是空壳。

他们的主产品是同名的 AI 生成桌面应用，定位 "Controllable AI for Artists"，2D 画布加 3D 场景编辑，聚合 Nano Banana、GPT-Image、Kling、Seedance 这些模型生图生视频，订阅每月 $8 到 $48（年付价），也支持自带 API key。Craft 系列就靠这个养着，官方原话是 "Your subscription helps keep ArtCraft free and open for everyone"。拿 AI 应用的订阅费养一套开源 Adobe 替代品，很 2026。

创作者在 [HN 帖子](https://news.ycombinator.com/item?id=49958850)里的发言：承认还是 super early alpha，Craft 系列 Apache-2.0、无遥测，要拉人一起干，发 stipend 和 bug bounty，接下来做 Microsoft Office、AutoCAD、SolidWorks，原话 "Everything can be open source multiplatform Rust now"。我写作这天又去看了眼仓库，wordcraft（Word）、gridcraft（Excel）、deckcraft（PowerPoint）、soundcraft（Pro Tools）、cadcraft（AutoCAD）真挂出来了，全是 10 月 7 日创建。说话算话，就是这速度有点吓人。

看空的也不少。有人担心 1:1 复刻 UI 招律师函，懂行的回复说 UI 交互几乎没有可专利的东西；有人说 AI 生成代码的版权归属本身就存疑。最损的一条评论送了他们一个外号：Icarus with a Claude jetpack，插着 Claude 喷气背包的伊卡洛斯。

## 实测的人怎么说

HN 帖子 119 分、184 条评论，吵得很凶。

亲测党的反馈难看。选择工具和光标对不上；拖字号滑块，文字没反应；到处卡顿；快捷键显示着，按了没动静。一位做了多年设计与影视的从业者把简单的排版稿喂给 PhotoCraft，丢下一句 "literally nothing works"。最狠的评价就一句话："very broken software produced by AI"。

也有公道话。有人说它更像高级版画图，离 Krita 都还远，但"考虑到这是 AI 做的，还是挺吓人"。还有位老哥晒经历：不想付 Creative Suite 订阅，花几个小时给自己 vibecode 了个只含常用功能的 Rust 图像编辑器，PSD 图层文字都支持，用了 9 个月，好好的。

专业人士的提醒更值得听。printpdf crate 的维护者说，光是把 PDF 的文字编码和字体子集化做对，他就试错了几个月，别低估 QA 的量。还有条分析我最服气：Excel 是既有思想的实现，可以用穷举测试证明 clone 是对的；Photoshop 是一种"表面和介质"，手感和响应就是本体，这部分没法 one-shot。

创作者在 Reddit 上放过狠话："Software is over. But for real. I just one-shotted Photoshop. The whole thing."，并承诺一个月内 100% parity。我赌他说满了。**功能轮廓的 90% 确实可以 one-shot，剩下 10% 是长尾里的长尾**，那个 134/135 的 PSD 往返通过率就是证据：差的那 1 个文件，才是工程的开始。

## 功能不值钱了，然后呢

写这件事，真正想聊的是另一个问题：实现一个商业软件 90% 功能的成本，从几十年、几百号工程师，掉到几个 prompt，软件的出路在哪？

HN 有条评论把我点醒了，来自 tonyedgecombe：

> Adobe's problem isn't that we replace their tools with AI created tools. It's that we replace the use of their tools with the use of AI.

Adobe 的麻烦不在工具被重写，在这些工具的用途本身正在被 AI 拿走。Photoshop 最大的对手不是 PhotoCraft，是 prompt。修图、去背景这类需求，在生成模型里一句话就没了。

那还剩什么？我想了一圈，大概四条，分量不一样。

最实惠的是卖结果。有条评论说得直白：软件行业还有卖软件、支持软件的生意，"造软件"的生意没了，门槛从研发挪到销售和服务。功能会贬值，结果和服务不会。

最扎实的是格式。PSD、PDF、docx，谁掌握格式谁就掌握迁移成本，PhotoCraft 下血本做 PSD 的 byte-for-byte 兼容，打的就是这里。教程、预设、插件、印刷厂对接，全挂在格式上。

我最看好的是给 agent 造软件。这是整套设计里最有先见之明的一条：每个 Craft 从第一天就带 CLI、JSON 控制通道和 MCP server，赌未来软件的一半用户是 agent，不是人。软件的形态会从"给人用的 GUI"变成"给 agent 调的 API，顺带一个 GUI"。这件事现在还没人做对。

最后是信任。实现成本打到地板之后，值钱的是"谁保证它能用、坏了谁修"。开源加社区加 AI 补长尾，是眼下能看到的最好答案。

所以 "Software is over" 对了一半。软件没完，完的是写软件这件事的稀缺性；软件从资产变耗材，值钱的地方从实现挪到了信任。

## 护城河搬家了

顺着这个话题再往深一层：工具的护城河越来越少，该从业务上砌。这个判断我认同一半，得先把"业务"说清楚。

前半句没什么可争的。功能层被 AI 压到周级交付，格式可以逆向，PhotoCraft 连 PSD 都做到 byte-for-byte 兼容；UI 交互几乎没有可专利的东西，快捷键照抄不犯法。工具软件靠"别人做不出来"的时间差吃饭，这个时间差现在以星期计。

后半句的关键在"业务"指什么。给工具换个收费姿势，订阅、云空间、会员壳，这不解决问题：壳没有壁垒，SaaSocalypse 骂的就是它，ArtCraft 主页那句 "Stop renting from websites" 打的也是它。真正成立的做法，是在工具之外攒随使用增长的资产：数据、社区、市场、标准、服务。这些 clone 不出来，越用越厚。

ArtCraft 自己就是照这个打法设计的。七个 Craft 全部免费开源，一分钱不收；钱从 AI 生成来，订阅卖的是 credits 和模型服务。用 Spolsky 2002 年那篇 [Strategy Letter V](https://www.joelonsoftware.com/2002/06/12/strategy-letter-v/) 的话说，这叫把互补品做成大路货：收费业务是 AI 生成，那就把创作工具这个互补品干到免费，生成的需求自然变大。IBM 当年公开 PC 架构、微软非独占授权 MS-DOS，都是同一招。工具在他们的账本上是获客成本，不是利润中心。

Adobe 自己也早就在往这边挪。HN 评论区有人点破，Adobe 几年前就把重心转向面向企业的云服务了。看看 Creative Cloud 订阅里装的东西：云协作、Firefly 生成额度、Stock 素材库、Behance 社区，纯工具的成分在稀释，资产和服务的成分在加厚。

Figma 走的同一条路。编辑器本身被 clone 了许多轮，开源的 Penpot、国产的 Motiff 都在做，但多人协作里沉淀的社区文件、插件市场、企业账号体系搬不走。工具决定你能不能上桌，资产决定你能不能留在桌上。

两个提醒。工具仍是要拼的入场券：业务接不住一个难用的工具，那 10% 的工程深度就是筛子，从业务上砌墙不等于工具可以躺平。业务壁垒自己也得过同一条检验：是否随使用增值，不增值的照样是壳。

## 小结

我准备先装个 PrintCraft 试试。七个里面它需求最普适，谁都躲不开 PDF，而 Acrobat 的订阅实在谈不上讨人喜欢；PhotoCraft 等过了 beta 再看。

创作者承诺一个月内 100% parity。行，一个月后回来对账。哪怕最后只有 PrintCraft 长成能日常用的工具，这波也不亏；要是真让他把 Adobe 和 Office 都 collapse 了，那就是这个时代最值得围观的一场实验。

一家之言，一个月后见分晓。
