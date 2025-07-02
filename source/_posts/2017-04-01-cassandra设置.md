title: Cassandra 设置
date: 2017-04-01 12:29:41
categories:

- data
- cassandra

tags:

- cassandra

---

## 操作系统

**修改操作系统的 TCP keepalive**

```
sudo /sbin/sysctl -w net.ipv4.tcp_keepalive_time=60 net.ipv4.tcp_keepalive_intvl=60 net.ipv4.tcp_keepalive_probes=5
```

## 集群机制

**一致性哈希**

- Gossip 协议：用于在环内节点之间传播 Cassandra 状态信息
- Snitch：支持多个数据中心
- 复制策略：数据的冗余生策略

**commit log**

- 进行写操作时，先把数据定入 commit log
- 只有数据被写入 commit log 时，才算写入成功
- 当发生掉电、实例崩溃等问题时，可以使用 commit log 进行恢复

**memtable**

- 数据成功写入 commit log 后，就开始写入内存中的 memtable
- memtable 中的数据达到一定阈值后，开始把数据写入硬盘中的 SSTable，然后在内存中重新建立一个 memtable 接收下一批数据
- 上述过程是非阻塞的
- 查询时优先查询 memtable

**副本因子----控制数据的冗余份数**

```sql
CREATE KEYSPACE Excelsior WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };
CREATE KEYSPACE Excalibur WITH REPLICATION = {'class' : 'NetworkTopologyStrategy', 'dc1' : 3, 'dc2' : 2};
```

## 可调节的一致性

### 写操作的 Consistency Level

**ANY**

- 任意一个节点写操作已经成功。如果所有的 replica 节点都挂了，写操作还是可以在记录一个 hinted handoff 事件之后，返回成功。如果所有的 replica 节点都挂了，写入的数据，在挂掉的 replica 节点恢复之前，读不到。
- 最小的延时等待，并且确保写请求不会失败。相对于其他级别提供最低的一致性和最高的可用性。

**ALL**

- 写操作必须将指定行的数据写到所有 replica 节点的 commit log 和 memtable。
- 相对于其他级别提供最高的一致性和最低的可用性。

**EACH_QUORUM**

- 写操作必须将指定行的数据写到每个数据中心的 quorum 数量的 replica 节点的 commit log 和 memtable。
- 用于多数据中心集群严格的保证相同级别的一致性。例如，如果你希望，当一个数据中心挂掉了，或者不能满足 quorum 数量的 replica 节点写操作成功时，写请求返回失败。

**LOCAL_ONE**

- 任何一个本地数据中心内的 replica 节点写操作成功。
- 对于多数据中心的情况，往往期望至少一个 replica 节点写成功，但是，又不希望有任何跨数据中心的通信。LOCAL_ONE 正好能满足这样的需求。

**LOCAL_QUORUM**

- 本地数据中心内 quorum 数量的 replica 节点写操作成功。避免跨数据中心的通信。
- 不能和 SimpleStrategy 一起使用。用于保证本地数据中心的数据一致性。

**LOCAL_SERIAL**

- 本地数据中心内 quorum 数量的 replica 节点有条件地（conditionally）写成功。
- 用于轻量级事务（lightweight transaction）下实现 linearizable consistency，避免发生无条件的（unconditional）更新。。

**ONE**

- 任意一个 replica 节点写操作已经成功。
- 满足大多数用户的需求。一般离 coordinator 节点具体最近的 replica 节点优先执行。

（即使指定了 consistency level ON 或 LOCAL_QUORUM，写操作还是会被发送给所有的 replica 节点，包括其他数据中心的里 replica 节点。consistency level 只是决定了，通知客户端请求成功之前，需要确保写操作成功的 replica 节点的数量。）

### 读操作的 Consistency Level

**ALL**

- 向所有 replica 节点查询数据，返回所有的 replica 返回的数据中，timestamp 最新的数据。如果某个 replica 节点没有响应，读操作会失败。
- 相对于其他级别，提供最高的一致性和最低的可用性。

**EACH_QUORUM**

- 向每个数据中心内 quorum 数量的 replica 节点查询数据，返回时间戳最新的数据。
- 同 LOCAL_QUORUM。

**LOCAL_SERIAL**

- 同 SERIAL，但是只限制为本地数据中心。
- 同 SERIAL。

**LOCAL_QUORUM**

- 向每个数据中心内 quorum 数量的 replica 节点查询数据，返回时间戳最新的数据。避免跨数据中心的通信。
- 使用 SimpleStrategy 时会失败。

**LOCAL_ONE**

- 返回本地数据中心内离 coordinator 节点最近的 replica 节点的数据。
- 同写操作 Consistency level 中该级别的用法。

**ONE**

- 返回由 snitch 决定的最近的 replica 返回的结果。默认情况下，后台会触发 read repair 确保其他 replica 的数据一致。
- 提供最高级别的可用性，但是返回的结果不一定最新。

**QUORUM**

- 读取所有数据中心中 quorum 数量的节点的结果，返回合并后 timestamp 最新的结果。
- 保证很强的一致性，虽然有可能读取失败。

**SERIAL**

- 允许读取当前的（包括 uncommitted 的）数据，如果读的过程中发现 uncommitted 的事务，则 commit 它。
- 轻量级事务。

**TWO**

- 返回两个最近的 replica 的最新数据。
- 和 ONE 类似。

**THREE**

- 返回三个最近的 replica 的最新数据。
- 和 TWO 类似。

### 关于 QUORUM 级别

QUORUM 级别确保数据写到指定 quorum 数量的节点。一个 quorum 的值由下面的公式四舍五入计算而得：

```
(sum_of_replication_factors / 2) + 1
```

`sum_of_replication_factors` 指每个数据中心的所有 replication_factor 设置的总和。

例如，如果某个单数据中心的 replication factor 是 3，quorum 值为 2-表示集群可以最多容忍 1 个节点 down。如果 replication factor 是 6，quorum 值为 4-表示集群可以最多容忍 2 个节点 down。如果是双数据中心，每个数据中心的 replication factor 是 3，quorum 值为 4-表示集群可以最多容忍 2 个节点 down。如果是 5 数据中心，每个数据中心的 replication factor of 3，quorum 值为 8 。

如果想确保读写一致性可以使用下面的公式：

```
(nodes_written + nodes_read) > replication_factor
```

例如，如果应用程序使用 QUORUM 级别来读和写，replication factor 值为 3，那么，该设置能够确保 2 个节点一定会被写入和读取。读节点数加上写写点数（4）个节点比 replication factor （3）大，这样就能确保一致性。

## 应用开发

### Java 批量查询、写入配置

在使用 `BatchStatement` 进行插入操作时会发现，当数据量稍大以后数据库中并没有加入新的数据。这是因为 Cassandra 默认对批量操作的数据大小限制得比较低。我们将其修改即可。

```shell
# Log WARN on any batch size exceeding this value. 5kb per batch by default.
# Caution should be taken on increasing the size of this threshold as it can lead to node instability.
batch_size_warn_threshold_in_kb: 1000

# Fail any batch exceeding this value. 50kb (10x warn threshold) by default.
batch_size_fail_threshold_in_kb: 2000
```
