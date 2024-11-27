title: PostgreSQL高可用 - PG 11集群
date: 2019-07-12 15:04:31
categories:
- bigdata
- postgresql
tags:
- postgresql
- 集群
- cluster
---

1. [《PostgreSQL从入门到不后悔》](https://www.yangjing.me/2018/02/05/postgresql%E4%BB%8E%E5%85%A5%E9%97%A8%E5%88%B0%E4%B8%8D%E5%90%8E%E6%82%94/)
2. [《PostgreSQL高可用：逻辑复制》](https://www.yangjing.me/2019/07/10/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8%EF%BC%9A%E9%80%BB%E8%BE%91%E5%A4%8D%E5%88%B6/)
3. [《PostgreSQL高可用 - PG 11集群》](http://localhost:4000/2019/07/12/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8-PG11%E9%9B%86%E7%BE%A4/)

- 高可用性：数据库服务器可以一起工作， 这样如果主要的服务器失效则允许一个第二服务器快速接手它的任务
- 负载均衡: 允许多个计算机提供相同的数据

本文使用的主要技术有：

- CentOS 7 x86\_64
- PostgreSQL 11.4

![PG主热备集群架构示意图](/img/PG主热备001-002-003.png)

## 系统安装、配置

```
$ sudo localectl set-locale "LANG=zh_CN.utf8"
$ sudo timedatectl set-timezone Asia/Shanghai
$ sudo yum -y install https://download.postgresql.org/pub/repos/yum/reporpms/EL-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm epel-release vim
$ sudo yum -y update
$ sudo yum -y install postgresql11-server postgresql11-contrib postgresql
```

更多关于PG安装和基础使用方面内容可阅读 [《PostgreSQL从入门到不后悔》](https://www.yangjing.me/2018/02/05/postgresql%E4%BB%8E%E5%85%A5%E9%97%A8%E5%88%B0%E4%B8%8D%E5%90%8E%E6%82%94/) 和 [《PostgreSQL高可用：逻辑复制》](https://www.yangjing.me/2019/07/10/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8%EF%BC%9A%E9%80%BB%E8%BE%91%E5%A4%8D%E5%88%B6/) 。

## PostgreSQL 集群设置

这里列出官方对各种高可用、负载均衡和复制特性实现方式的比较：

![高可用、负载均衡和复制特性矩阵](/img/高可用、负载均衡和复制特性矩阵.png)

***本文将基于PostgreSQL官方提供的基于流式的WAL数据复制功能搭建一个 主/热备 数据库集群。***

根据 **PostgreSQL单机配置**，安装3台服务器。IP地址设置分别如下，并加入 `/etc/hosts` 中：

- 主节点：10.0.32.37
- 热备节点：10.0.32.35
- 逻辑复制节点：10.0.32.36，有关逻辑复制的内容请阅读：[《PostgreSQL高可用：逻辑复制》](https://www.yangjing.me/2019/07/10/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8%EF%BC%9A%E9%80%BB%E8%BE%91%E5%A4%8D%E5%88%B6/)

## 主节点（10.0.32.37）

1.. 创建一个传用于复制的账号：

```
CREATE ROLE pgrepuser REPLICATION LOGIN ENCRYPTED PASSWORD 'pgreppass';
```

2.. 在 `postgresql.conf` 设置以下配置项：

```
listen_addresses = '*'
max_connections = 1024
password_encryption = on
wal_level = logical # logical包含replica的功能，这样可使主节点同时具备流复制来源和逻辑复制发布者
archive_mode = on
max_wal_sender = 4
wal_keep_segments = 10
```

3.. 在 `pg_hba.conf` 文件中为 **pgrepuser** 设置权限规则。允许 **pgrepuser** 从所有地址连接到主节点，并使用基于MD5的密码加密方式。

```
host    replication     pgrepuser       0.0.0.0/0               md5
```

主服务器配置好后需要重启数据库：

```
$ sudo systemctl restart postgresql-11
```

若在生产环境中没有条件进行数据库重启，也可以使用 `pg_ctl reload` 指令重新加载配置：

```
$ sudo systemctl reload postgresql-11
```

主节点的配置到此即可，接下来对从节点进行配置。

## 从节点

1.. 首先停止从机上的PostgreSQL服务（非启动过刚可忽略此步骤）。

```
sudo systemctl stop postgresql-11
```

2.. 使用 `pg_basebackup` 生成备库

首先清空 $PGDATA 目录。

```
-bash-4.2$ cd /var/lib/pgsql/11/data
-bash-4.2$ rm -rf *
```

使用 **`pg_basebackup`** 命令生成备库：

```
-bash-4.2$ /usr/pgsql-11/bin/pg_basebackup -D $PGDATA -Fp -Xstream -R -c fast -v -P -h 10.0.32.37 -U pgrepuser -W
```

我们看到以下的操作输出，代表生成备库成功。

```
Password:
pg_basebackup: initiating base backup, waiting for checkpoint to complete
pg_basebackup: checkpoint completed
pg_basebackup: write-ahead log start point: 0/6000028 on timeline 1
pg_basebackup: starting background WAL receiver
pg_basebackup: created temporary replication slot "pg_basebackup_27275"
pg_basebackup: write-ahead log end point: 0/60000F8
pg_basebackup: waiting for background process to finish streaming ...
pg_basebackup: base backup completed
```

3.. 将下面的配置设置添加到 postgresql.conf 文件中。

```
hot_standby = on
```

4.. 在 $PGDATA 目录创建 **recovery.conf** 文件，内容如下：

```
standby_mode = 'on'
primary_conninfo = 'host=10.0.32.37 port=5432 user=pgrepuser password=pgreppass application_name=replica1'
trigger_file='recovery.fail'
recovery_target_timeline = 'latest'
restore_command = cp %p ../archive/%f'
```

- `restore_command` 如果发现从属服务器处理事务日志的速度较慢，跟不上主服务器产生日志的速度，为避免主服务器产生积压，你可以在从属服务器上指定一个路径用于缓存暂未处理的日志。请在 recovery.conf 中添加如下一个代码行，该代码行在不同操作系统下会有所不同。

5.. 启动从数据库

```
$ sudo systemctl start postgresql-11
```

**启动复制进程的注意事项**

*一般情况下，我们建议先启动所有从属服务器再启动主服务器，如果顺序反过来，会导致主服务器已经开始修改数据并生成事务日志了，但从属服务器却还无法进行复制处理，这会导致主服务器的日志积压。如果在未启动主服务器的情况下先启动从属服务器，那么从属服务器日志中会报错，说无法连接到主服务器，但这没有关系，忽略即可。等所有从属服务器都启动完毕后，就可以启动主服务器了。*

*此时所有主从属服务器应该都是能访问的。主服务器的任何修改，包括安装一个扩展包或者是新建表这种对系统元数据的修改，都会被同步到从属服务器。从属服务器可对外提供查询服务。*

*如果希望某个从属服务器脱离当前的主从复制环境，即此后以一台独立的 PostgreSQL 服务器身份而存在，请直接在其 data 文件夹下创建一个名为 failover.now 的空文件。从属服务器会在处理完当前接收到的最后一条事务日志后停止接收新的日志，然后将 recovery.conf 改名为 recovery.done。此时从属服务器已与主服务器彻底解除了复制关系，此后这台PostgreSQL 服务器会作为一台独立的数据库服务器存在，其数据的初始状态就是它作为从属服务器时处理完最后一条事务日志后的状态。一旦从属服务器脱离了主从复制环境，就不可能再切换回主从复制状态了，要想切回去，必须按照前述步骤一切从零开始。*

### 测试主/备服务

分别登录 10.0.32.37 和 10.0.32.35 两台数据库使用 `\l` 命令查看数据库列表：

**PostgreSQL: Prod-DataHouse-3**

```
$ psql -h 10.0.32.37 -U postgres
用户 postgres 的口令：
psql (11.4)
输入 "help" 来获取帮助信息.

postgres=# \l
                                     数据库列表
   名称    |  拥有者  | 字元编码 |  校对规则   |    Ctype    |       存取权限
-----------+----------+----------+-------------+-------------+-----------------------
 postgres  | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 |
 template0 | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 | =c/postgres          +
           |          |          |             |             | postgres=CTc/postgres
 template1 | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 | =c/postgres          +
           |          |          |             |             | postgres=CTc/postgres
(3 行记录)
```

**PostgreSQL: Prod-DataHouse-1**

```
$ psql -h 10.0.32.35 -U postgres
用户 postgres 的口令：
psql (11.4)
输入 "help" 来获取帮助信息.

postgres=# \l
                                     数据库列表
   名称    |  拥有者  | 字元编码 |  校对规则   |    Ctype    |       存取权限
-----------+----------+----------+-------------+-------------+-----------------------
 postgres  | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 |
 template0 | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 | =c/postgres          +
           |          |          |             |             | postgres=CTc/postgres
 template1 | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 | =c/postgres          +
           |          |          |             |             | postgres=CTc/postgres
(3 行记录)
```

我们在 10.0.32.37 上创建一个测试数据库：**test**

```
postgres=# create database test template=template1;
```

**test** 数据库创建成功后，我们可以在 10.0.32.35 从服务器上看到 **test** 数据库已经同步过来。

```
postgres=# \l
                                     数据库列表
   名称    |  拥有者  | 字元编码 |  校对规则   |    Ctype    |       存取权限
-----------+----------+----------+-------------+-------------+-----------------------
 postgres  | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 |
 template0 | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 | =c/postgres          +
           |          |          |             |             | postgres=CTc/postgres
 template1 | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 | =c/postgres          +
           |          |          |             |             | postgres=CTc/postgres
 test      | postgres | UTF8     | zh_CN.UTF-8 | zh_CN.UTF-8 |
(4 行记录)
```

继续在从库上尝试 DDL 操作，可以发现从库已被正确的设置为只读模式：

```
postgres=# \c test
您现在已经连接到数据库 "test",用户 "postgres".
test=# CREATE TABLE test (id BIGS

test=# CREATE TABLE test (id BIGSERIAL PRIMARY KEY, name VARCHAR(255), age INT);
错误:  不能在一个只读模式的事务中执行CREATE TABLE
```

在主库上，我们可以正常的进行读写操作。同时主节点的 **恢复（recovery）** 模式为 false。

```
test=# CREATE TABLE test (id BIGSERIAL PRIMARY KEY, name VARCHAR(255), age INT);
CREATE TABLE
test=# INSERT INTO test(name, age) VALUES('羊八井', 31), ('杨景', 31);
INSERT 0 2
test=# SELECT * FROM test;
 id |  name  | age
----+--------+-----
  1 | 羊八井 |  31
  2 | 杨景   |  31
(2 行记录)


test=# select pg_is_in_recovery();
 pg_is_in_recovery
-------------------
 f
(1 行记录)
```

让我们再切换到从库，执行 `SELECT * FROM test` 查询语句可以看到之前在主库上写入的两条记录已被成功复制过来。

![pg-从库从主库同步数据示例](/img/pg-从库从主库同步数据示例.png)


### 数据库复制状态

**Prod-DataHouse-3**

在主节点上，我们可以看到有一个复制节点连接上来，客户端地址（`client_addr`）为：10.0.32.37，使用流式复制（`state`），同步模式（`sync_state`）为异步复制。

```
test=# \x
扩展显示已打开。
test=# select * from pg_stat_replication;
-[ RECORD 1 ]----+------------------------------
pid              | 1287
usesysid         | 16384
usename          | pgrepuser
application_name | walreceiver
client_addr      | 10.0.32.35
client_hostname  |
client_port      | 42338
backend_start    | 2019-07-11 10:08:37.842367+08
backend_xmin     |
state            | streaming
sent_location    | 0/501EB20
write_location   | 0/501EB20
flush_location   | 0/501EB20
replay_location  | 0/501EB20
sync_priority    | 0
sync_state       | async
```

## 主/备切换

1.. 关闭主节点数据库服务：

```
[Prod-DataHouse-3 ~]$ sudo systemctl stop postgresql-11
```

2.. 将从节点变为主节点

```
[Prod-DataHouse-1 ~]$ sudo su - postgres
-bash-4.2$ /usr/pgsql-11/bin/pg_ctl promote -D $PGDATA
```

此时在节点 centos7-002 上，PostgreSQL数据库已经从备节点转换成了主节点。同时，`recovery.conf` 文件也变为了 `recovery.done` 文件，表示此节点不再做为从节点进行数据复制。

```
test=# select pg_is_in_recovery();
-[ RECORD 1 ]-----+--
pg_is_in_recovery | f

test=# DELETE FROM test WHERE id = 1;
DELETE 1

test=# SELECT * FROM test;
 id | name | age
----+------+-----
  2 | 杨景 |  31
(1 行记录)

```

3.. 将原主节点（**centos7-001**）变为级联从节点（当前主节点已改为centos7-002）。

在 centos7-001 节点上编辑 `postgresql.conf`，并开启热备模式：

```
hot_standby = on
```

添加并编辑 `recovery.conf` 文件：

```
standby_mode = 'on'
primary_conninfo = 'host=centos7-003 port=5432 user=pgrepuser password=pgreppass application_name=replic1'
trigger_file='recovery.fail'
recovery_target_timeline = 'latest'
restore_command = cp %p ../archive/%f'
```

（重）启动PostgreSQL数据库，节点 centos7-001 现在成为了一个 **级联从节点** 。

![PG主备切换以后集群架构示意图](/img/PG主热备002-003-001.png)

## 总结

PostgreSQL官方支持基于流式复制的WAL实现的主/热备高可用集群机制，同时我们还可以搭配 PgPool-II 在应用层实现

