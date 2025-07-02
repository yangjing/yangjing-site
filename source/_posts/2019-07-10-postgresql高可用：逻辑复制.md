title: PostgreSQL 高可用：逻辑复制
date: 2019-07-10 19:39:36
categories:

- data
- postgresql

tags:

- 逻辑复制
- logical replication
- replication
- wal

---

1. [《PostgreSQL 从入门到不后悔》](https://www.yangjing.me/2018/02/05/postgresql%E4%BB%8E%E5%85%A5%E9%97%A8%E5%88%B0%E4%B8%8D%E5%90%8E%E6%82%94/)
2. [《PostgreSQL 高可用：逻辑复制》](https://www.yangjing.me/2019/07/10/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8%EF%BC%9A%E9%80%BB%E8%BE%91%E5%A4%8D%E5%88%B6/)
3. [《PostgreSQL 高可用 - PG 11 集群》](http://localhost:4000/2019/07/12/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8-PG11%E9%9B%86%E7%BE%A4/)

从 PostgreSQL 10（以下简称 PG）开始，PG 支持逻辑复制能力，可实现仅复制部分表或 PG 服务器上的部分 database。逻辑复制的一大优点是支持跨版本间复制，也不需要主从节点的操作系统和硬件架构相同。例如，我们可以实现一台 Linux 服务器上的 PG 11 和 Windows 服务器上的 PG 10 之间的复制；通过逻辑复制还可以实现不停服的数据库版本更新。

在 PG 逻辑复制的概念体系中，数据提供方被称为**发布者**（publisher），数据接收方被称为**订阅者**（subscriber）。同一个 PG 即可以作为发布者，同时也可以作为订阅者，这样即可实现级联复制，可以及大的减轻主节点的负担。

**\*注意：**但需要注意的是，逻辑复制是单向的。你在从节点上修改的数据行将不会反向同步的主节点，同时相应行也不会再响应主节点的数据变更。\*

## 设置逻辑复制

本文使用两台主机来演示 PG 的逻辑复制：

1. **10.0.32.37**：主节点，数据发布端
2. **10.0.32.36**：从节点，数据订阅端

### 配置

启用逻辑复制非常简单，设置 PG 的系统参数`wal_level`为`logical`即可。可在 SQL 控制台通过以下命令修改：

```sql
alter system set wal_level = logical;
```

也可逻辑配置文件以使其全局生效（并重启 PG）,编辑`postgresql.conf`并设置如下参数：

```
wal_level = logical			# minimal, replica, or logical

```

### 创建数据发布者

使用具有**superuser**权限的用户登录需要设置为数据发布者的数据库：

```sql
psql -h 10.0.32.35 -U devuser -d watch_log -W
```

创建数据发布者

```sql
watch_log=# create publication custom_watch_log for table l_basic, l_contact; -- 多张表使用英文逗号分隔列出
CREATE PUBLICATION
```

**\*注意：**这里不要使用`for all tables`创建数据发布者，因为这将造成发布者在以后不能增、删表。\*

默认 PG 只对**insert**、**delete**语句进行逻辑复制，若需要对其它**DML**语句也先进逻辑复制需要设置表的`replica identity [option]`属性。

```sql
alter table l_basic replica identity full ;
```

### 创建数据订阅者

使用具有**superuser**权限的用户登录需要设置为数据订阅者的数据库：

```sql
psql -h 10.0.32.36 -U devuser -d watch_log -W
```

PG 的逻辑复制不会同步**DDL**语句，需要在订阅者数据库上提前创建后需要进行逻辑复制的表。

创建数据订阅者

```sql
watch_log=# create subscription full_watch_log connection 'host=10.0.32.37 port=5432 dbname=watch_log user=devuser password=dbuser.password' publication full_watch_log;
NOTICE:  created replication slot "custom_watch_log" on publisher
CREATE SUBSCRIPTION
```

## 演示

#### 1: 初始

_数据发布_

```sql
watch_log=> \conninfo
以用户 "devuser" 的身份, 在主机"10.0.32.37", 端口"5432"连接到数据库 "watch_log"
watch_log=> select * from l_basic ;
 imei | i | p | s | o | u
------+---+---+---+---+---
(0 行记录)
```

_数据订阅_

```sql
watch_log=> \conninfo
以用户 "devuser" 的身份, 在主机"10.0.32.36", 端口"5432"连接到数据库 "watch_log"
watch_log=> select * from l_basic ;
 imei | i | p | s | o | u
------+---+---+---+---+---
(0 行记录)
```

#### 2: 数据发布者插入记录

_数据发布_

```sql
watch_log=> insert into l_basic(imei, i, p, s, o, u) values('imei-1', now(), 11, 11, 11.11, 11.11), ('imei-2', now(), 22, 22, 22.22, 22.22);
INSERT 0 2
watch_log=> select * from l_basic ;
  imei  |             i              | p  | s  |   o   |   u
--------+----------------------------+----+----+-------+-------
 imei-1 | 2019-07-10 19:37:39.283234 | 11 | 11 | 11.11 | 11.11
 imei-2 | 2019-07-10 19:37:39.283234 | 22 | 22 | 22.22 | 22.22
(2 行记录)
```

_数据订阅_

```sql
watch_log=> select * from l_basic ;
  imei  |             i              | p  | s  |   o   |   u
--------+----------------------------+----+----+-------+-------
 imei-1 | 2019-07-10 19:44:47.357619 | 11 | 11 | 11.11 | 11.11
 imei-2 | 2019-07-10 19:44:47.357619 | 22 | 22 | 22.22 | 22.22
(2 行记录)
```

#### 3: 数据发布者编辑记录

_数据发布_

```sql
watch_log=> update l_basic set p = 44, o = 44.44 where imei = 'imei-1';
UPDATE 1
watch_log=> delete from l_basic where imei = 'imei-2';
DELETE 1
watch_log=> select * from l_basic ;
  imei  |             i              | p  | s  |   o   |   u
--------+----------------------------+----+----+-------+-------
 imei-1 | 2019-07-10 19:44:47.357619 | 44 | 11 | 44.44 | 11.11
(1 行记录)
```

_数据订阅_

```sql
watch_log=> select * from l_basic ;
  imei  |             i              | p  | s  |   o   |   u
--------+----------------------------+----+----+-------+-------
 imei-1 | 2019-07-10 19:44:47.357619 | 44 | 11 | 44.44 | 11.11
(1 行记录)
```

## 增加新的数据发布表

#### 数据发布端添加新表

使用之前创建`publication`时的用户登录数据库

```
psql -h 10.0.32.37 -U postgres -d watch_log -W
watch_log=# alter publication custom_watch_log add table test ;
ALTER PUBLICATION
```

#### 数据订阅端添加新表并刷新 subscription

创建数据表`test`：

```sql
psql -h 10.0.32.36 -U devuser -d watch_log -W
watch_log=> create table test(id serial primary key, name varchar(255));
CREATE TABLE
```

使用之前创建`publication`时的用户登录数据库

```
psql -h 10.0.32.36 -U postgres -d watch_log -W
watch_log2=# alter subscription custom_watch_log refresh publication ;
ALTER SUBSCRIPTION
```

## 小结

最后放张图：
![PostgreSQL 11 logical replication](/img/postgresql/PostgreSQL11-logical_replication_example.png)
