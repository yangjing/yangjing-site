title: Alpakka Kafka，设计消费者
date: 2019-02-27 14:21:18
categories:
  - scala
  - akka
tags:
  - scala
  - akka
  - kafka
  - alpakka-kafka
---

上一篇文章：

- [Alpakka Kafka，反应式Kafka客户端（开源中国）](https://my.oschina.net/yangjing/blog/3014041)
- [Alpakka Kafka，反应式Kafka客户端](https://www.yangjing.me/2019/02/23/alpakka-kafka%EF%BC%8C%E5%8F%8D%E5%BA%94%E5%BC%8Fkafka%E5%AE%A2%E6%88%B7%E7%AB%AF/)

介绍了alpakka-kafka，并使用一个简单的示例演示了怎样生产数据到Kafka并从Kafka消费数据。本文将介绍怎样通过alpakka-kafka来控制消费者的行为并设计一个好的消费者。

