title: Canssandra 开始
date: 2015-10-22 21:50:49
updated: 2015-10-26 21:30:32
categories:

- data
- cassandra

tags:

- cassandra
- 集群

---

## Install Cassandra

```
sudo mkdir -p /usr/app/cassandra
sudo chown -R $(whoami) /usr/app
cd /usr/app/cassandra
wget http://apache.fayea.com/cassandra/2.1.11/apache-cassandra-2.1.11-bin.tar.gz
tar zxf apache-cassandra-2.1.11-bin.tar.gz
mv apache-cassandra-2.1.11 cassandra-2.1
mkdir data commitlog log saved_caches
```

启动`bin/cassandra -f`即可，`-f`参数的意思是让 cassandra 服务在前台启动，这样可以各种上日志输出就将直接打印到终端上。

## Cassandra 集群搭建

我们构建一个有 3 个节点的 cassandra 集群，IP 分别为：`192.168.31.101`、`192.168.31.102`、`192.168.31.103`。

编辑 Cassandra 配置文件：`conf/cassandra.yaml`：

**endpoint_snitch**：

```
endpoint_snitch: GossipingPropertyFileSnitch
```

配置 Cassandra 集群支持机架感知，推荐产品使用。

**listen_address**：

```
listen_address: 192.168.31.101
```

**rpc_address**：

```
rpc_address:
```

（注意：`rpc_address:`后面的空格也要删除掉）

**seeds**：

```
     - seeds: "192.168.31.101,192.168.31.102,192.168.31.103"
```

**Store data**：

```
data_file_directories:
     - /usr/app/cassandra/data
```

**Commit log**：

```
commitlog_directory: /usr/app/cassandra/commitlog
```

**Saved caches**：

```
saved_caches_directory: /usr/app/cassandra/saved_caches
```

先配置好一台，再把相同的配置文件和目录复制到其它节点上。在修改相应 IP 地址即可。之后挨个启动集群，可以看到下面这样的节点连结信息：

```
INFO  12:33:35 Handshaking version with /192.168.31.102
INFO  12:33:36 No gossip backlog; proceeding
INFO  12:33:36 Node /192.168.31.102 is now part of the cluster
INFO  12:33:36 Netty using native Epoll event loop
INFO  12:33:36 InetAddress /192.168.31.102 is now UP
INFO  12:33:36 Updating topology for /192.168.31.102
```

## CQLSH

#### 创建 keyspace

```
CREATE KEYSPACE watch_log WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 2 };
```

## cqlsh 支持查询中文

默认的`cqlsh`是可以正常显示中文的，但当你的查询语句里有中文时，就会报错：

```sql
cqlsh:mykeyspace> select * from users where fname = '羊';
'ascii' codec can't decode byte 0xe7 in position 35: ordinal not in range(128)
```

因为`cqlsh`是用`Python`编写的，所以定位是`Python`对中文支持的问题。这个问题也很好解决，加入以下两行代码即可：

```python
reload(sys)
sys.setdefaultencoding("utf-8")
```

加入后代码显示如下：

```python
from glob import glob
from StringIO import StringIO
from uuid import UUID

reload(sys)
sys.setdefaultencoding("utf-8")

description = "CQL Shell for Apache Cassandra"
version = "5.0.1"
```

## 一些重要的 Cassandra 配置

- cluster_name: 限制只能加入相同名字的集群
- num_tokens:

## 系统优化

DataStax 推荐的`ulimit`设置如下（编辑`/etc/security/limits.conf`文件，添加）：

```
scdata memlock unlimited
scdata nofile 100000
scdata nproc 23768
scdata - as unlimited
```

使用以下命令使设置马上生效：

```
$ sudo sysctl -p
```
