title: PostgreSQL 高可用-主/热备集群
date: 2017-09-20 12:39:37
categories:

- data
- postgresql

tags:

- postgresql
- 集群
- cluster

---

- 高可用性：数据库服务器可以一起工作， 这样如果主要的服务器失效则允许一个第二服务器快速接手它的任务
- 负载均衡: 允许多个计算机提供相同的数据

本文使用的主要技术有：

- CentOS 7 x86_64
- PostgreSQL 9.6.5

![PG主热备集群架构示意图](/img/PG主热备001-002-003.png)

## 系统安装、配置

### CentOS 7 安装

```
[hldev@centos7-001 ~]$ sudo yum -y install https://download.postgresql.org/pub/repos/yum/9.6/redhat/rhel-7-x86_64/pgdg-centos96-9.6-3.noarch.rpm epel-release vim
[hldev@centos7-001 ~]$ sudo yum -y update
```

### CentOS 系统配置

**Selinux 配置**

编辑 `/etc/sysconfig/selinux`，设置 **SELINUX** 为 **disabled**：

```
# This file controls the state of SELinux on the system.
# SELINUX= can take one of these three values:
#     enforcing - SELinux security policy is enforced.
#     permissive - SELinux prints warnings instead of enforcing.
#     disabled - No SELinux policy is loaded.
SELINUX=disabled
# SELINUXTYPE= can take one of three two values:
#     targeted - Targeted processes are protected,
#     minimum - Modification of targeted policy. Only selected processes are protected.
#     mls - Multi Level Security protection.
SELINUXTYPE=targeted
```

**其它配置**

TODO

**_修改完后请重启系统，以确保所有设置生效。_**

## PostgreSQL 安装

```
[hldev@centos7-001 ~]$ sudo yum -y install postgresql96-server postgresql96-contrib
```

**初始化 PostgreSQL 数据库**

```
[hldev@centos7-001 ~]$ sudo /usr/pgsql-9.6/bin/postgresql96-setup initdb
```

**启动 PostgreSQL**

```
[hldev@centos7-001 ~]$ sudo systemctl start postgresql-9.6
```

使用 `sudo systemctl status postgresql-9.6` 来检测 PG 运行状态。看到如下输出，则代表数据库安装成功。

```
● postgresql-9.6.service - PostgreSQL 9.6 database server
   Loaded: loaded (/usr/lib/systemd/system/postgresql-9.6.service; enabled; vendor preset: disabled)
   Active: active (running) since 二 2017-09-19 22:30:46 CST; 1s ago
  Process: 1466 ExecStartPre=/usr/pgsql-9.6/bin/postgresql96-check-db-dir ${PGDATA} (code=exited, status=0/SUCCESS)
 Main PID: 1471 (postmaster)
```

## PostgreSQL 单机配置

**配置 PostgreSQL**

登录 **postgres** 操作系统账号：

```
[hldev@centos7-001 ~]$ sudo su - postgres
```

编辑 `/var/lib/pgsql/9.6/data/pg_hba.conf` 文件，将 `host    all             all             127.0.0.1/32            ident` 修改为允许所有网络登录，并使用 md5 方式进行认证：

```
host    all             all             0.0.0.0/0               md5
```

编辑 `/var/lib/pgsql/9.6/data/postgresql.conf` 文件，找到并设置如下选项：

```
listen_addresses = '*'
max_connections = 1024
password_encryption = on
```

通过以上配置，我们设置了 PostgreSQL 允许任何 IPv4 地址都可以使用密码认证的方式登录数据库，并设置密码使用加密传输/存储，同时还修改了最大连接数限制为 1000。

**设置 postgres（数据库超级管理员）密码**

首先使用 `psql` 命令登录 PostgreSQL 数据库

```

-bash-4.2$ psql
```

使用 `\password` 命令设置当前用户密码

```
postgres=# \password
输入新的密码：
再次输入：
```

_注意：此处需要在 postgres 操作系统账号下操作_

**打开防火墙端口**

要使本机以外可远程连接数据库，还需要打开操作系统防火墙的对应端口。PostgreSQL 在安装时提供了 **firewalld** 的服务配置文件，我们可以通过服务的方式来打开防火墙：

```
sudo firewall-cmd --add-service=postgresql --permanent
sudo firewall-cmd --reload
```

**_以上修改后需要重启 PostgreSQL 数据库_**

```
exit # 退出postgres账号
[hldev@centos7-001 ~]$ sudo systemctl restart postgresql-9.6.service
```

**远程连接 PostgreSQL 数据库**

在远程主机上使用以下命令连接已安装好的 PostgreSQL 数据库，**centos7-001** 为已安装数据库服务器主机 IP 地址对应主机名（通过在 `/etc/hosts` 中设置 `192.168.124.146  centos7-001` 来映射）。

```
[hldev@centos7-001 ~]$ psql -h centos7-001 -U postgres
```

登录成功后，我们看到如下提示则代表 PostgreSQL 的单机安装成功完成：

```
用户 postgres 的口令：
psql (9.6.5)
输入 "help" 来获取帮助信息.

postgres=#
```

## PostgreSQL 集群设置

这里列出官方对各种高可用、负载均衡和复制特性实现方式的比较：

![高可用、负载均衡和复制特性矩阵](/img/高可用、负载均衡和复制特性矩阵.png)

**_本文将基于 PostgreSQL 官方提供的基于流式的 WAL 数据复制功能搭建一个 主/热备 数据库集群。_**

根据 **PostgreSQL 单机配置**，安装 3 台服务器。IP 地址设置分别如下，并加入 `/etc/hosts` 中：

```
192.168.124.161 centos7-001
192.168.124.162 centos7-002
192.168.124.163 centos7-003
```

三台服务器的角色分别如下：

- centos7-001: 主服务器
- centos7-002: 从服务器
- centos7-003: 级联从服务器

### 主节点（centos7-001）

1.. 创建一个传用于复制的账号：

```
CREATE ROLE pgrepuser REPLICATION LOGIN PASSWORD 'pgreppass';
```

2.. 在 `postgresql.conf` 设置以下配置项：

```
listen_addresses = '*'
max_connections = 1024
password_encryption = on
wal_level = hot_standby
archive_mode = on
max_wal_sender = 4
wal_keep_segments = 10
```

3.. 在 `pg_hba.conf` 文件中为 **pgrepuser** 设置权限规则。允许 **pgrepuser** 从 IP 地址范围为 192.168.124.1 到
192.168.124.254 连接到主服务器，并使用基于 MD5 的加密密码。

```
host    replication     pgrepuser       0.0.0.0/0               md5
```

主服务器配置好后需要重启数据库：

```
[hldev@centos7-001 ~]$ sudo systemctl restart postgresql-9.6
```

若在生产环境中没有条件进行数据库重启，也可以使用 `pg_ctl reload` 指令重新加载配置：

```
-bash-4.2$ /usr/pgsql-9.6/bin/pg_ctl reload -D $PGDATA
```

### 从节点

1.. 首先停止从机上的 PostgreSQL 服务。

```
sudo systemctl stop postgresql-9.6
```

2.. 使用 `pg_basebackup` 生成备库

首先清空 $PGDATA 目录。

```
-bash-4.2$ cd /var/lib/pgsql/9.6/data
-bash-4.2$ rm -rf *
```

使用 **pg_basebackup** 命令生成备库：

```
-bash-4.2$ /usr/pgsql-9.6/bin/pg_basebackup -D $PGDATA -Fp -Xs -v -P -h centos7-001 -U pgrepuser
```

我们看到以下的操作输出，代表生成备库成功。

```
pg_basebackup: initiating base backup, waiting for checkpoint to complete
pg_basebackup: checkpoint completed
事务日志起始于时间点: 0/4000028, 基于时间表1
pg_basebackup: 启动后台 WAL 接收进程
22836/22836 kB (100%), 1/1 表空间
transaction log end point: 0/40000F8
pg_basebackup: 等待后台进程结束流操作...
pg_basebackup: base backup completed
```

3.. 将下面的配置设置添加到 postgresql.conf 文件中。

```
hot_standby = on
```

4.. 在 $PGDATA 目录创建 **recovery.conf** 文件，内容如下：

```
standby_mode = 'on'
primary_conninfo = 'host=centos7-001 port=5432 user=pgrepuser password=pgreppass'
trigger_file = 'failover.now'
recovery_target_timeline = 'latest'
```

5.. 如果发现从属服务器处理事务日志的速度较慢，跟不上主服务器产生日志的速度，为避免主服务器产生积压，你可以在从属服务器上指定一个路径用于缓存暂未处理的日志。请在 recovery.conf 中添加如下一个代码行，该代码行在不同操作系统下会有所不同。

```
restore_command = 'cp %p ../archive/%f'
```

6.. 启动从数据库

```
[hldev@centos7-002 ~]$ sudo systemctl stop postgresql-9.6
```

**启动复制进程的注意事项**

_一般情况下，我们建议先启动所有从属服务器再启动主服务器，如果顺序反过来，会导致主服务器已经开始修改数据并生成事务日志了，但从属服务器却还无法进行复制处理，这会导致主服务器的日志积压。如果在未启动主服务器的情况下先启动从属服务器，那么从属服务器日志中会报错，说无法连接到主服务器，但这没有关系，忽略即可。等所有从属服务器都启动完毕后，就可以启动主服务器了。_

_此时所有主从属服务器应该都是能访问的。主服务器的任何修改，包括安装一个扩展包或者是新建表这种对系统元数据的修改，都会被同步到从属服务器。从属服务器可对外提供查询服务。_

_如果希望某个从属服务器脱离当前的主从复制环境，即此后以一台独立的 PostgreSQL 服务器身份而存在，请直接在其 data 文件夹下创建一个名为 failover.now 的空文件。从属服务器会在处理完当前接收到的最后一条事务日志后停止接收新的日志，然后将 recovery.conf 改名为 recovery.done。此时从属服务器已与主服务器彻底解除了复制关系，此后这台 PostgreSQL 服务器会作为一台独立的数据库服务器存在，其数据的初始状态就是它作为从属服务器时处理完最后一条事务日志后的状态。一旦从属服务器脱离了主从复制环境，就不可能再切换回主从复制状态了，要想切回去，必须按照前述步骤一切从零开始。_

### 测试主/备服务

分别登录 centos7-001 和 centos7-002 两台数据库使用 `\l` 命令查看数据库列表：

**PostgreSQL: centos7-001**

```
[hldev@centos7-001 ~]$ psql -h centos7-001 -U postgres
用户 postgres 的口令：
psql (9.6.5)
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

**PostgreSQL: centos7-002**

```
[hldev@centos7-002 ~]$ psql -h centos7-002 -U postgres
用户 postgres 的口令：
psql (9.6.5)
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

我们在 centos7-001 上创建一个测试数据库：**test**

```
postgres=# create database test template=template1;
```

**test** 数据库创建成功后，我们可以在 centos7-002 从服务器上看到 **test** 数据库已经同步过来。

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

### 级联从节点

1.. 按从节点的方式，停止数据库并使用 **pg_basebackup** 从 **从节点** 在线备份数据库到 $PGDATA 目录。

```
[hldev@centos7-003 ~]$ sudo systemctl stop postgresql-9.6
[hldev@centos7-003 ~]$ sudo su - postgres
-bash-4.2$ cd $PGDATA
-bash-4.2$ rm -rf *
-bash-4.2$ /usr/pgsql-9.6/bin/pg_basebackup -D $PGDATA -Fp -Xs -v -P -h centos7-002 -U pgrepuser
口令:
pg_basebackup: initiating base backup, waiting for checkpoint to complete
pg_basebackup: checkpoint completed
事务日志起始于时间点: 0/501E7A0, 基于时间表1
pg_basebackup: 启动后台 WAL 接收进程
30375/30375 kB (100%), 1/1 表空间
transaction log end point: 0/501E848
pg_basebackup: 等待后台进程结束流操作...
pg_basebackup: base backup completed
```

2.. 配置 `recovery.conf` 运行参数。将 `primary_conninfo` 的 **host** 指定为从节点：centos7-002，同时添加恢复目标时间线 `recovery_target_timeline` 选项：

```
standby_mode = 'on'
primary_conninfo = 'host=centos7-002 port=5432 user=pgrepuser password=pgreppass'
trigger_file = 'failover.now'
recovery_target_timeline = 'latest'
```

并设置 `recovery.conf` 文件其它用户不能读取

```
-bash-4.2$ chmod 0600 recovery.conf
```

3.. 启动 **级联从节点** ，并测试数据是否已同步。

```
[hldev@centos7-003 ~]$ sudo systemctl start postgresql-9.6
[hldev@centos7-003 ~]$ psql -h centos7-003 -U postgres -d test
用户 postgres 的口令：
psql (9.6.5)
输入 "help" 来获取帮助信息.

test=# SELECT * FROM test;
 id |  name  | age
----+--------+-----
  1 | 羊八井 |  31
  2 | 杨景   |  31
(2 行记录)

test=# select pg_is_in_recovery();
 pg_is_in_recovery
-------------------
 t
(1 行记录)
```

可以看到，级联从节点已正常的从 **从节点** 将数据同步过来。同时，我们还可以看到当前数据库处于 **恢复（recovery）** 模式。

### 数据库复制状态

**centos7-001**

在主节点上，我们可以看到有一个复制节点连接上来，客户端地址（`client_addr`）为：192.168.124.162（centos7-002），使用流式复制（`state`），同步模式（`sync_state`）为异步复制。

```
test=# \x
扩展显示已打开。
test=# select * from pg_stat_replication;
-[ RECORD 1 ]----+------------------------------
pid              | 1287
usesysid         | 16384
usename          | pgrepuser
application_name | walreceiver
client_addr      | 192.168.124.162
client_hostname  |
client_port      | 42338
backend_start    | 2017-09-20 10:08:37.842367+08
backend_xmin     |
state            | streaming
sent_location    | 0/501EB20
write_location   | 0/501EB20
flush_location   | 0/501EB20
replay_location  | 0/501EB20
sync_priority    | 0
sync_state       | async
```

**centos7-002**

而在从节点上看到也有一个复制节点连接上来，客户端地址（`client_addr`）为：192.168.124.163（centos7-003），使用流式复制（`state`），同步模式（`sync_state`）为异步复制。

```
test=# \x
扩展显示已打开。
test=# select * from pg_stat_replication;
-[ RECORD 1 ]----+------------------------------
pid              | 1224
usesysid         | 16384
usename          | pgrepuser
application_name | walreceiver
client_addr      | 192.168.124.163
client_hostname  |
client_port      | 53276
backend_start    | 2017-09-20 11:12:40.906493+08
backend_xmin     |
state            | streaming
sent_location    | 0/501EC00
write_location   | 0/501EC00
flush_location   | 0/501EC00
replay_location  | 0/501EC00
sync_priority    | 0
sync_state       | async
```

### 主/备切换

1.. 关闭主节点数据库服务：

```
[hldev@centos7-001 ~]$ sudo systemctl stop postgresql-9.6
```

2.. 将从节点（centos7-002）变为主节点

```
-bash-4.2$ /usr/pgsql-9.6/bin/pg_ctl promote -D $PGDATA
```

此时在节点 centos7-002 上，PostgreSQL 数据库已经从备节点转换成了主节点。同时，`recovery.conf` 文件也变为了 `recovery.done` 文件，表示此节点不再做为从节点进行数据复制。

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

3.. 将原主节点（**centos7-001**）变为级联从节点（当前主节点已改为 centos7-002）。

在 centos7-001 节点上编辑 `postgresql.conf`，并开启热备模式：

```
hot_standby = on
```

添加并编辑 `recovery.conf` 文件：

```
standby_mode = 'on'
primary_conninfo = 'host=centos7-003 port=5432 user=pgrepuser password=pgreppass'
trigger_file = 'failover.now'
recovery_target_timeline = 'latest'
```

（重）启动 PostgreSQL 数据库，节点 centos7-001 现在成为了一个 **级联从节点** 。

![PG主备切换以后集群架构示意图](/img/PG主热备002-003-001.png)

## 总结

PostgreSQL 官方支持基于流式复制的 WAL 实现的主/热备高可用集群机制，同时我们还可以搭配 PgPool-II 在应用层实现
