title: Why gRPC (Akka) ?
date: 2019-11-25 09:49:03
category: scala
tags:
  - akka
  - akka-grpc
  - grpc
---

*原文链接：https://doc.akka.io/docs/akka-grpc/current/whygrpc.html*

# 什么是gRPC？

gRPC是一个支持请求/响应和流式处理（非持久化）用例的传输机制。

它是一个模式优先的RPC框架，协议在 **Protobuf服务描述符**（**protobuf service descriptor**）中声明，请求和响应将通过 HTTP/2 连接流式的传输。

它有几个优点：

- 模式优先设计倾向于定义良好且分享的服务接口，而不是脆弱的自组织方案；
- 基于Protobuf的wire协议是高效的、众所周知的，并且允许兼容的模式演化；
- 基于 HTTP/2 ，这样它允许在单个连接上复用多个数据流；
- 流式处理的请求和响应是第一类的；
- 许多语言都有可用的工具，不同语言编写的客户端与服务之间可无缝互操作。

这使得gRPC非常适合：

- 内部服务之间的连接
- 连接到公开的gRPC API外部服务（甚至是用其它语言编写的服务）
- 给Web或移动设备前端提供数据

## gRPC vs REST

- REST在编码方面更灵活，而gRPC基于Protobuf标准化了编码格式；
- REST是无模式的或可以使用第三方模式，而gRPC总是在Protobuf模式定义中声明服务和消息。

*注：REST的编码灵活性是有代价的，随着时间的推移，不兼容或不同步会造成越来越多的问题；而gRPC通过Protobuf在编码阶段既强制了模式与数据类型，同时还提供了很好的向后兼容性解决方案。*

## gRPC vs SOAP

- SOAP在传输（协议）方面更灵活，而gRPC只能在 HTTP/2 上使用；
- 在SOPA中，一旦定义了协议，它们通常就固定下来（通常要求每个服务版本都使用一个新的路径），而Protobuf明确地支持模式演进（注：可增加字段）。

## gRPC vs Message bus

- 虽然gRPC建立在高效的非阻塞实现之上，但它仍然是 **同步的** ，因为它要求通信 **双方** 同时可用。当使用（持久地）消息总线（Message bus）时，需要生产者和总线必须启动，消息者可按需启动，从而导致更高程序的分离（解耦）；
- gRPC支持每个请求的双向数据流处理，而消息总线的数据流处理是分离的。

*注：解耦并不总是好的，某些时候更需要请求可以即时响应，通常消息总线并不适合用于提供服务API的场景。*

## gRPC vs Akka Remoting

- 虽然Akka Remoting允许在不同的Akka ActorSystem之间透明地交换消息，但它仍然需要大量地工作来支持高效并兼容的消息序列化。且大消息会阻塞消息传输。与gRPC相比，Akka Remoting不是流式的（streaming，不直接支持streams），它需要建立在消息传递的基础之上（例如：使用 StreamRefs 来模拟流式传输）；
- Akka Remoting的协议（数据序列化协议）可能会随着Akka版本和配置的变化而变化，这需要你确保系统的所有部分都运行足够相似的版本（版本不兼容问题比较突出）。而gRPC，保证了协议的长期稳定，因此gRPC客户端和服务更加松耦合；
- 当Akka Remoting中的消息使用 fire-and-forget 方式传递会分离服务的执行，而任务类型的RPC都需要等待远程过程调用得到响应。任何RPC情况下等待（甚至是非阻塞的）响应通常都会绑定重要的资源（*注：请求ID、上下文、超时等*）。公平地说，（Akka）actor通信（Akka Remoting）通常是以请求/响应的方式构造的，这使得它非常类似于更传统的RPC技术，并且具有相同的缺点（比如：需要保持 **客户端** 状态，在等待响应时需要超时机制）。

## Akka生态里怎样使用gRPC？

Akka从2.6开始，`akka-remote`已不建议在用户代码里使用，同时`akka-cluster-client`也被 **Deprecated**。这样导致两个结果：

1. 使用 Akka Cluster 机制进行集群内消息通信，如：akka-cluster-sharding、akka-cluster-distributed-data、akka-cluster-pubsub。
2. Akka Cluster 之间（集群外）使用 Akka gRPC 进行通信。

***一句话总结：Akka Cluster之外推荐使用Akka gRPC！***

接下来，也许你会对 [《Akka Cookbook》](https://www.yangjing.me/akka-cookbook/) 感兴趣。
