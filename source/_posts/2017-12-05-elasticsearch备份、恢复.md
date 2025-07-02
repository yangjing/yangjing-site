title: Elasticsearch 备份、恢复
date: 2017-12-05 14:27:33
categories:

- data
- elasticsearch

tags:

---

## 迁移注意事项

- 保证 ES 集群不再接受新的数据(如果是备份的话，这一点可以不考虑，但是做数据迁移的话，建议这样做）。同一个 repository 只应有一个集群可写，其它集群都应以 readonly 模式连接。
- 不建议直接在生产环境做这些操作，最好是先在本地搭建一个和生产环境一样的集群环境，创建一些测试数据，把整个过程先跑一遍，然后再到生产环境操作。

`dn126` 为要备份的源数据节点，而 `localhost` 为待恢复的目标数据节点

## 备份

**本文使用文件系统作为快照仓库的存储，选择一个节点执行命令**

```
curl -X PUT http://dn126:9200/_snapshot/backups \
  -d '{
	"type": "fs",
	"settings": {
		"location": "/home/app/var/elasticsearch/backups",
		"compress": true
	}
  }'
```

**生成备份**

```
curl -X PUT 'http://dn126:9200/_snapshot/backups/snapshot_1?wait_for_completion=true' \
  -d '{
  "indices": "fgw_search,fgw_search_2",
  "ignore_unavailable": true,
  "include_global_state": false
}'
```

## 恢复

**删除已有的备份数据**

```
curl -X DELETE http://localhost:9200/_snapshot/backups
```

**拷贝备份数据**

```
tar zxf elasticsearch-backups-2017.12.07.tar.gz
mv elasticsearch-backups/* /opt/haishu/var/elasticsearch/backups/
```

**恢复数据**

```
curl -i -X POST http://localhost:9200/_snapshot/backups/snapshot_1/_restore
```
