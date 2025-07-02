title: Flink Kafka 确定一次消费与写入
date: 2020-06-04 16:04:33
category:

- data
- flink

tags:

- flink
- kafka
- exactly-once

---

Flink Kafka Exactly Once，确定一次消费/写入。示例代码：[https://github.com/yangjing/learn-bigdata/tree/develop/learn-flink/src/main/scala/connector/kafka](https://github.com/yangjing/learn-bigdata/tree/develop/learn-flink/src/main/scala/connector/kafka) 。

## Consumer

## Producer

Producer exactly once 需要启用 flink 的检查点，并在实例化 `FlinkKafkaProducer` 时指定 `FlinkKafkaProducer.Semantic.EXACTLY_ONCE` ：

```scala
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(1000)

    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "localhost:9092")

    val producer = new FlinkKafkaProducer[NameTimestamp](
      topic,
      new NameTimestampSerializationSchema(topic),
      properties,
      Semantic.EXACTLY_ONCE)
```

## 问题

### Kafka 事物超时

- `transaction.timeout.ms`：客户端事物协调器超时时间。Flink 默认设置为 1 小时。
- `transaction.max.timeout.ms`：设置服务端 broker 支持的最大事物超时时间，默认值为 900000 毫秒（15 分钟）。

#### Interrupted while joining ioThread java.lang.InterruptedException

```
16:02:28,124 ERROR org.apache.kafka.clients.producer.KafkaProducer               - [Producer clientId=producer-57, transactionalId=Sink: name-sink-0a448493b4782967b150582570326227-75] Interrupted while joining ioThread java.lang.InterruptedException
```

该异常实现上很可能是：`Unexpected error in InitProducerIdResponse; The transaction timeout is larger than the maximum value allowed by the broker (as configured by transaction.max.timeout.ms).`，我们只需要调整 `transaction.timeout.ms` 或 `transaction.max.timeout.ms` 的值即可解决该问题。

修改 `transaction.timeout.ms`：

```scala
properties.setProperty("transaction.timeout.ms", s"${60 * 5 * 1000}")
```

### watermark assigner 未触发

**请注意**：如果 watermark assigner 依赖于从 Kafka 读取的消息来上涨其 watermark (通常就是这种情况)，那么所有主题和分区都需要有连续的消息流。否则，整个应用程序的 watermark 将无法上涨，所有基于时间的算子(例如时间窗口或带有计时器的函数)也无法运行。单个的 Kafka 分区也会导致这种反应。这是一个已在计划中的 Flink 改进，目的是为了防止这种情况发生（请见[FLINK-5479: Per-partition watermarks in FlinkKafkaConsumer should consider idle partitions](https://issues.apache.org/jira/browse/FLINK-5479)）。同时，可能的解决方法是将*心跳消息*发送到所有 consumer 的分区里，从而上涨空闲分区的 watermark。

_注：Flink 1.11.0 已提供了此问题的解决方案，通过设置合适 [idleness timeouts](https://ci.apache.org/projects/flink/flink-docs-release-1.11/dev/event_timestamps_watermarks.html#dealing-with-idle-sources) 来解决此问题（[https://ci.apache.org/projects/flink/flink-docs-release-1.11/dev/event_timestamps_watermarks.html#dealing-with-idle-sources](https://ci.apache.org/projects/flink/flink-docs-release-1.11/dev/event_timestamps_watermarks.html#dealing-with-idle-sources)）。_
