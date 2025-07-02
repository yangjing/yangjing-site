title: Elasticsearch 初步使用
date: 2015-11-02 15:40:09
categories:

- data
- elasticsearch

tags:

- elasticsearch
- 集群

---

# 集群安装

安装一个两个结节的简单集群，其中一个 Master，一个 Slave。两台机器的网络分别是：

```
192.168.31.101  sc-007
192.168.31.48   scdev-001
```

**Master 配置**

```yaml
cluster:
  name: sc0
node:
  name: sc-007
  master: true
network:
  host: 192.168.31.101
discovery:
  zen.ping.unicast.hosts: ["sc-007"]
```

**Slave 配置**

```yaml
cluster:
  name: sc0
node:
  name: scdev-001
  master: true
network:
  host: 192.168.31.48
discovery:
  zen.ping.unicast.hosts: ["sc-007"]
```

# 集群有用的相关命令

检查集群健康度：

`curl -XGET http://127.0.0.1:9200/_cluster/health?pretty`

获取集群中节点信息：

`curl -XGET http://192.168.31.101:9200/_cluster/state/nodes/?pretty`

关闭集群中所有节点：

`curl -XGET http://192.168.31.101:9200/_cluster/state/nodes/_shutdown`
