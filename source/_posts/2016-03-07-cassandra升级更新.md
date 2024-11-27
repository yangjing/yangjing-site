title: Cassandra升级更新
date: 2016-03-07 11:52:36
categories:
- bigdata
- cassandra
tags:
- cassandra
- upgrading
---

今天需要把集群安装的 cassandra 2.2.4 升级到 cassandra 2.2.5 ，这里记录下升级步骤。

*（升级脚本见：[https://gist.github.com/yangjing/12461fcab190689f2499](https://gist.github.com/yangjing/12461fcab190689f2499)）*

## 升级的主意事项和限制条件

**需求条件**

- Cassandra 2.0.x and 2.1.x: 需要 JRE 7 或更高版本（推荐JDK）
- Cassandra 2.2.x, 3.0.x, and 3.x: 需要 JRE 8 或更高版本（推荐JDK）
- 移出所有 dead 节点：[`nodetool removenode`](https://docs.datastax.com/en/cassandra/2.1/cassandra/tools/toolsRemoveNode.html)

**升级限制**

在执行升级步骤时有一些常用限制需要在整个集群遵循：

- 不要启用新特性（Do not enable new features.）
- 不要运行`nodetool repair`命令（Do not run nodetool repair.）
- 不要运行DDL和TRUNCATE这类CQL查询语句（Do not issue these types of CQL queries during a rolling restart: DDL and TRUNCATE.）
- 在升级期间，不同版本的节点显示的schema可能会不一致（During upgrades, the nodes on different versions might show a schema disagreement.）


## 升级步骤

1. 设置新版 cassandra 2.2.5：

```
tar zxf /home/scdata/dsc-cassandra-2.2.5-bin.tar.gz -C /opt/local
```

如上，先解压新版cassandra到磁盘，再复制配置文件到cassandra 2.2.5目录中覆盖默认配置。可能需要复制的配置文件有：

- bin/cassandra.in.sh
- conf/cassandra-env.sh
- conf/cassandra.yaml
- conf/cassandra-rackdc.properties
- conf/cassandra-topology.properties

2. 创建快照，防止升级失败。

```
nodetool snapshot KEYSPACE -t snapshot_`date "+%Y-%m-%d"`
```

把 KEYSPACE 换成实际的keyspace名。若使用了JNA，则快照是通过硬链接实现的，并不会增加磁盘空间。创建快照时间很快。

3. 停节点

```
nodetool drain   # 关闭写入，同时把数据写入文件
nodetool stopdaemon    # 停掉本节点
```

4. 启动新节点


5. 可选的 SSTables 更新

当升级cassandra是一个主要版本更新（如：1.2 to 2.0），或者是一个主要点的更新（如：2.0 to 2.1）时，需要在每个节点升级 SSTable。

```
nodetool upgradesstables
```

6. 结果日志，查看升级过程中是否有：warnings、errors和exceptions

7. 在每个节点上重复以上步骤

## 最后

官方升级详细文档见：[upgradeCassandraDetails](https://docs.datastax.com/en/upgrade/doc/upgrade/cassandra/upgradeCassandraDetails.html)。

Cassandra的升级与维护应该是一个常态，推荐一年进行一次大版本的升级。这样可以避免版本跨度太大而引起的很多升级问题。


