title: Nacos SDK for Scala
date: 2020-04-09 12:56:40
category:
  - 作品
  - nacos-sdk-scala
tags:
  - nacos
  - spring-cloud
  - scala
  - akka
  - play
---

[Nacos](https://nacos.io/) SDK for Scala：[https://github.com/yangjing/nacos-sdk-scala](https://github.com/yangjing/nacos-sdk-scala) 。

支持 Scala 2.12, 2.13 ；支持 Akka Discovery 和 Play WS。

## 使用

```scala
// Scala API
libraryDependencies += "me.yangjing.nacos4s" %% "nacos-client-scala" % "1.2.0"

// Akka Discovery
libraryDependencies += "me.yangjing.nacos4s" %% "nacos-akka" % "1.2.0"

// Play WS
libraryDependencies += "me.yangjing.nacos4s" %% "nacos-play-ws" % "1.2.0"
```

需要添加以下源：

```scala
resolvers += Resolver.bintrayRepo("helloscala", "maven")
```

## 在线文档

在线文档：[https://yangjing.github.io/nacos-sdk-scala](https://yangjing.github.io/nacos-sdk-scala)


**本地阅读：**

以下命令将自动编译并打开默认浏览器以阅读文档：

```
git clone https://github.com/yangjing/nacos-sdk-scala
cd nacos-sdk-scala
sbt nacos-docs/paradoxBrowse
```

