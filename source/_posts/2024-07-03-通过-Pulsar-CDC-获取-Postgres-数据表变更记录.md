title: 通过 Pulsar CDC 获取 Postgres 数据表变更记录
date: 2024-07-03 21:59:01
tags:

- pulsar
- cdc
- postgresql

categories:

- data
- pulsar

---

在当今数据驱动的时代，数据的实时性、完整性和一致性成为了企业业务成功的关键因素。随着微服务单服单库（每个微服务都有自己单独的数据库）的应用，以及数据量的爆炸性增长和业务的快速迭代，传统的数据处理和同步方式已难以满足现代企业的需求。Apache Pulsar，作为一个云原生的分布式消息和流处理平台，凭借其卓越的吞吐量和低延迟特性，正在逐渐成为大数据和流处理领域的明星。而 Pulsar CDC 技术的引入，更是为数据的实时捕获和同步提供了强有力的支持。

Apache Pulsar CDC 技术允许用户实时捕获数据库中的变化数据（如 INSERT、UPDATE、DELETE 等操作），并将这些变更以流的形式传输到 Pulsar 中。通过这种方式，用户可以在几乎无延迟的情况下获取到最新的数据变化，进而实现数据的实时分析、处理和同步。这一技术的核心优势在于其高可靠性、低延迟和可扩展性，使得它成为现代数据架构中不可或缺的一部分。

~~本文将深入探讨 Apache Pulsar CDC 的技术原理、应用场景以及实现方式。我们将从 Pulsar 的基础架构讲起，逐步介绍如何配置和使用 Pulsar CDC 连接器，以及如何通过 Pulsar CDC 实现数据的实时捕获和同步。此外，我们还将分享一些实际的案例和最佳实践，帮助读者更好地理解和应用这一技术。~~

## 环境准备

本文将以 Pulsar 2.11、PostgreSQL 12 为例进行讲解。

### 安装 Pulsar

```shell
# 需要 Java 17，此安装步骤略
curl https://archive.apache.org/dist/pulsar/pulsar-2.11.4/apache-pulsar-2.11.4-bin.tar.gz -o apache-pulsar-2.11.4-bin.tar.gz
tar zxf apache-pulsar-2.11.4-bin.tar.gz
cd apache-pulsar-2.11.4

# 下载 debezium postgres io 连接器
mkdir connectors && cd connectors
curl https://archive.apache.org/dist/pulsar/pulsar-2.11.4/connectors/pulsar-io-debezium-postgres-2.11.4.nar \
    -o pulsar-io-debezium-postgres-2.11.4.nar

# 回到 Pulsar 安装目录
cd ../
# 启动 Pulsar
./bin/pulsar-daemon start standalone
```

### 安装 PostgreSQL

```shell
docker run -d --name postgres-12 \
    -p 5432:5432 \
    -e POSTGRES_PASSWORD=postgres \
    postgres:12
```

确定 `postgres-12` 启动成功后登录进容器配置支持逻辑解码。设置 `wal_level` 为 `logical` 以让 PostgreSQL 生成的预写日志支持被逻辑解码。

```shell
docker exec -it -u postgres postgres-12 bash

# 以下步骤在 docker 容器中执行
echo 'wal_level = logical' >> /var/lib/postgresql/data/postgresql.conf
exit
```

重启 docker 容器（这一步在宿主机中运行）

```shell
docker restart postgres-12
```

创建测试表和测试数据

```shell
docker exec -it -u postgres postgres-12 psql
```

以下步骤在 psql 中执行：

```postgresql
create table inventory (
  id bigserial primary key,
  name varchar(255) not null,
  price decimal(22,4) not null,
  quantity decimal(22,4) not null,
  status int not null default 1,
  create_by bigint not null,
  create_time timestamptz not null,
  update_by bigint,
update_time timestamptz);
alter table inventory replica identity FULL; -- 可选，后文说明
insert into inventory(name, price, quantity, create_by, create_time)
values ('榴莲', 21, 78, 1, now()), ('黄桃', 12.5, 202, 1, now()), ('芒果', 8.32, 13.32, 1, now());
```

## CDC

```yaml
tenant: "public"
namespace: "default"
name: "debezium-postgres-source"
topicName: "debezium-postgres-topic"
archive: "connectors/pulsar-io-debezium-postgres-2.11.4.nar"
parallelism: 1
runtimeFlags: "-Xms64M -Xmx128M"
configs:
  plugin.name: "pgoutput"
  poll.interval.ms: 1000
  heartbeat.interval.ms: 30000
  pulsar.service.url: "pulsar://127.0.0.1:6650"
  #snapshot.mode: "never"
  database.hostname: "127.0.0.1"
  database.port: "5432"
  database.user: "postgres"
  database.password: "postgres"
  database.dbname: "postgres"
  database.server.name: "yangjing"
  schema.include.list: "public"
  table.include.list: "public.inventory"
  decimal.handling.mode: "string"
  time.precision.mode: "connect"
```

### 启动 pulsar CDC

通过本地运行的方式启动 source

```shell
./apache-pulsar-2.11.4/bin/pulsar-admin source localrun \
    --source-config-file $(pwd)/debezium-postgres-source-config.yaml
```

> 在生产环境，可以将 `localrun` 换成 `create` 来创建一个 Pulsar Source，这里使用 `localrun` 只是演示使用。

#### 监听 topic 查看消息

```shell
./bin/pulsar-client consume -s "sub-inventory" -n 0 -p Earliest persistent://public/default/dbserver1.public.inventory
```

因为指定了 `-p Earliest`，我们将从 Topic 里最早的消息开始消费。我们将看到终端输出获取到两条消息（为了显示效果，对输出内容进行了换行）。

其 `op` 操作类型为 `"r"`，代表是读取数据（快照）。

第一条消息的 `source.snapshot` 值为 `"true"`，而第三条的值为 `last`，这指明了这些数据为第一次初始化 CDC 时读取数据表的全是快照数据，而 `"last"` 代表快照数据的最后一条。我们可以通过此判断快照数据是否完成，因为通常情况下在初始化快照数据时不需要做额外的业务逻辑处理。

```shell
----- got message -----
key:[eyJpZCI6MX0=], properties:[], content:{
  "before":null,
  "after":{"id":1,"name":"榴莲","price":"21.0000","quantity":"78.0000","status":1,"create_by":1,"create_time":"2024-07-03T15:05:46.244534Z","update_by":null,"update_time":null},
  "source":{"version":"1.7.2.Final","connector":"postgresql","name":"yangjing","ts_ms":1720019387942,
    "snapshot":"true",
    "db":"postgres","sequence":"[null,\"23848312\"]","schema":"public","table":"inventory","txId":515,"lsn":23848312,"xmin":null},
  "op":"r",
  "ts_ms":1720019387945,"transaction":null}
----- got message -----
key:[eyJpZCI6Mn0=], properties:[], content:{"before":null,"after":{"id":2,"name":"黄桃","price":"12.5000","quantity":"202.0000","status":1,"create_by":1,"create_time":"2024-07-03T15:05:46.244534Z","update_by":null,"update_time":null},
  "source":{"version":"1.7.2.Final","connector":"postgresql","name":"yangjing","ts_ms":1720019387948,
    "snapshot":"true",
    "db":"postgres","sequence":"[null,\"23848312\"]","schema":"public","table":"inventory","txId":515,"lsn":23848312,"xmin":null},
  "op":"r",
  "ts_ms":1720019387948,"transaction":null}
----- got message -----
key:[eyJpZCI6M30=], properties:[], content:{"before":null,"after":{"id":3,"name":"芒果","price":"8.3200","quantity":"13.3200","status":1,"create_by":1,"create_time":"2024-07-03T15:05:46.244534Z","update_by":null,"update_time":null},
  "source":{"version":"1.7.2.Final","connector":"postgresql","name":"yangjing","ts_ms":1720019387948,
    "snapshot":"last",
    "db":"postgres","sequence":"[null,\"23848312\"]","schema":"public","table":"inventory","txId":515,"lsn":23848312,"xmin":null},
  "op":"r",
  "ts_ms":1720019387948,"transaction":null}
```

#### 执行 DML 操作

```postgresql
update inventory set update_by = 5, update_time = now() where id = 2;
insert into inventory(name, price, quantity, create_by, create_time)
values ('芒果', 8.32, 13.32, 1, now());
delete from inventory where id = 1;
```

#### 从 Topic 中获得数据更新信息

```shell
----- got message -----
key:[eyJpZCI6Mn0=], properties:[], content:{"before":{"id":2,"name":"黄桃","price":"12.5000","quantity":"202.0000","status":1,"create_by":1,"create_time":"2024-07-03T13:15:56.757356Z","update_by":5,"update_time":"2024-07-03T13:16:10.938649Z"},"after":{"id":2,"name":"黄桃","price":"12.5000","quantity":"202.0000","status":1,"create_by":1,"create_time":"2024-07-03T13:15:56.757356Z","update_by":5,"update_time":"2024-07-03T13:17:11.847856Z"},"source":{"version":"1.7.2.Final","connector":"postgresql","name":"yangjing","ts_ms":1720012631848,"snapshot":"false","db":"postgres","sequence":"[\"23685152\",\"23692648\"]","schema":"public","table":"inventory","txId":507,"lsn":23692648,"xmin":null},"op":"u","ts_ms":1720012632359,"transaction":null}
2024-07-03T21:17:28,363+0800 [pulsar-timer-6-1] INFO  org.apache.pulsar.client.impl.ConsumerStatsRecorderImpl - [persistent://public/default/yangjing.public.inventory] [sub-inventory] [18db1] Prefetched messages: 0 --- Consume throughput received: 0.02 msgs/s --- 0.00 Mbit/s --- Ack sent rate: 0.02 ack/s --- Failed messages: 0 --- batch messages: 0 ---Failed acks: 0
----- got message -----
key:[eyJpZCI6M30=], properties:[], content:{"before":null,"after":{"id":3,"name":"芒果","price":"8.3200","quantity":"13.3200","status":1,"create_by":1,"create_time":"2024-07-03T13:18:27.223533Z","update_by":null,"update_time":null},"source":{"version":"1.7.2.Final","connector":"postgresql","name":"yangjing","ts_ms":1720012707224,"snapshot":"false","db":"postgres","sequence":"[\"23692896\",\"23692952\"]","schema":"public","table":"inventory","txId":508,"lsn":23692952,"xmin":null},"op":"c","ts_ms":1720012707403,"transaction":null}
2024-07-03T21:18:28,366+0800 [pulsar-timer-6-1] INFO  org.apache.pulsar.client.impl.ConsumerStatsRecorderImpl - [persistent://public/default/yangjing.public.inventory] [sub-inventory] [18db1] Prefetched messages: 0 --- Consume throughput received: 0.02 msgs/s --- 0.00 Mbit/s --- Ack sent rate: 0.02 ack/s --- Failed messages: 0 --- batch messages: 0 ---Failed acks: 0
----- got message -----
key:[eyJpZCI6MX0=], properties:[], content:{"before":{"id":1,"name":"榴莲","price":"21.0000","quantity":"78.0000","status":1,"create_by":1,"create_time":"2024-07-03T13:15:56.757356Z","update_by":null,"update_time":null},"after":null,"source":{"version":"1.7.2.Final","connector":"postgresql","name":"yangjing","ts_ms":1720012724177,"snapshot":"false","db":"postgres","sequence":"[\"23693184\",\"23693240\"]","schema":"public","table":"inventory","txId":509,"lsn":23693240,"xmin":null},"op":"d","ts_ms":1720012724249,"transaction":null}
```

#### Topic 命名规则

可以看到，Pulsar CDC 从 PostgreSQL WAL 解析后的数据变更将存储到对于 Topic 中，Topic 命名规则为：`serverName.schemaName.tableName`，对于配置键分别为：

- serverName: `database.server.name`
- schemaName: `schema.include.list`
- tableName: `table.include.list`

其中，`schema.include.list` 和 `table.include.list` 支持使用英文逗号分隔来配置多个 Schema 或 Table，而 Pulsar CDC 将为每张表生成一个对应的 Pulsar Topic。

> 需要注意的是，在配置 `tableName` 时需要指定 PG 模式（schema），不然默认为认为是 PG 的 public 模式。

#### 消息内容格式

可以看到，解析 WAL 生成的变更记录数据主要由以下部分组织：

```json
{
  "before": {},
  "after": {},
  "source": {},
  "op": "r",
  "ts_ms": 1720009847671,
  "transaction": null
}
```

##### `before`，输出 `update` 和 `delete` 之前的数据

回到之前初始化 PG 数据，注意有一句：`alter table inventory replica identity FULL;`。它设置 PG 在写入预写日志时，将旧值和新值都进行记录，这样我们就可以获取到数据修改以前的值。这可以方便我们对数据变更做进一步的对比，但它也会造成 WAL 的增大，需要合理评判是否启用此特性。

> 通常来说，我们可以保持默认配置：`DEFAULT`，只在 `delete` 时会在 `before` 字段显示删除前的数据，而 `update` 时不用。因为对于我们的 CDC 目的库表来说，既然是一条更新记录，那代表目的库已经有此记录，我们完全可以使用目的库已存在的记录来做新/旧数据对比。

##### `after`，输出 `create`、`update` 后以及快照初始化时的数据

一个可读示例如下：

```json
{
  "after": {
    "id": 3,
    "name": "芒果",
    "price": "8.0000",
    "quantity": "32.3000",
    "status": 1,
    "create_by": 1,
    "create_time": "2024-07-03T12:20:33.617094Z",
    "update_by": null,
    "update_time": null
  }
}
```

##### `op` 字段通常有如下几种情况

- `c`：创建记录，对应 SQL `insert`
- `u`：更新记录，对应 SQL `update`
- `d`：删除记录，对应 SQL `delete`
- `r`：读取记录，只有的快照模式（`snapshot.mode` 不为 `never`）时有用

## 总结

Pulsar CDC 除可用于对多个业务数据库的数据进行“实时”采集外，也可以用于“事件消息表”的实现。通过事件消息表，可以确保数据库事务和 MQ 发送事务保持一致，而且在一定程度上也可以简化业务程序员的开发复杂度。另文将对基于 Pulsar CDC 和 PostgreSQL 实现事件消息表进行较为详细的介绍。

本文完整示例脚本见：[https://github.com/yangjing/yangjing-site/tree/main/examples/pulsar-cdc](https://github.com/yangjing/yangjing-site/tree/main/examples/pulsar-cdc) 。

注：本文操作也适配 Pulsar 3.0 LTS 及 PostgreSQL 更高版本，当有差异时我将另文更新差异说明。
