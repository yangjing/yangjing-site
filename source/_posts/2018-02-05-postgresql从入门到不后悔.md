title: PostgreSQL从入门到不后悔
date: 2018-02-05 18:59:08
categories:
- bigdata
- postgresql
tags:
- postgresql
---

1. [《PostgreSQL从入门到不后悔》](https://www.yangjing.me/2018/02/05/postgresql%E4%BB%8E%E5%85%A5%E9%97%A8%E5%88%B0%E4%B8%8D%E5%90%8E%E6%82%94/)
2. [《PostgreSQL高可用：逻辑复制》](https://www.yangjing.me/2019/07/10/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8%EF%BC%9A%E9%80%BB%E8%BE%91%E5%A4%8D%E5%88%B6/)
3. [《PostgreSQL高可用 - PG 11集群》](https://www.yangjing.me/2019/07/12/postgresql%E9%AB%98%E5%8F%AF%E7%94%A8-PG11%E9%9B%86%E7%BE%A4/)

## 安装 PostgreSQL 10

下载 PostgreSQL 10，postgresql-10.1-3-linux-x64-binaries.tar.gz。下载地址：[https://get.enterprisedb.com/postgresql/postgresql-10.1-3-linux-x64-binaries.tar.gz](https://get.enterprisedb.com/postgresql/postgresql-10.1-3-linux-x64-binaries.tar.gz)。

***（注：安装脚本如下（需要有 `/opt/local` 写权限），可使用如下命令创建 `/opt/local` 目录。）***

```
sudo mkdir /opt/local
sudo chown -R $USER:$USER /opt/local
```

***install_pg.sh***

```
OPT_BASE=/opt
PGVERSION=10.1
PGBASE=$OPT_BASE/local/pgsql
PGHOME=$OPT_BASE/local/pgsql/$PGVERSION
PGDATA=$OPT_BASE/var/pgsql/$PGVERSION
PG_SOFT_￥TAR="postgresql-10.1-3-linux-x64-binaries.tar.gz"

if [ -d $PGHOME ]; then
  rm -rf $PGHOME
elif [ ! -d $PGBASE ]; then
  mkdir -p $PGBASE
fi

if [ ! -d $PGDATA ]; then
  mkdir -p $PGDATA
fi

echo "Install PostgreSQL"
tar zxf $PG_SOFT_TAR -C $PGBASE
mv $PGBASE/pgsql $PGHOME
cp pg-pwfile $PGHOME

echo "Init PostgreSQL"
pushd $PGHOME
./bin/initdb --pgdata="$PGDATA" --auth=ident --auth-host=md5 --encoding=UTF-8 --locale=zh_CN.UTF-8 --username=postgres --pwfile=pg-pwfile
rm -f pg-pwfile
popd

cp pg_hba.conf $PGDATA
cp postgresql.conf $PGDATA
chmod 600 $PGDATA/*.conf

echo "Start PostgreSQL"
$PGHOME/bin/pg_ctl -D $PGDATA -l logfile start
sleep 5
#cp .pgpass ~/
$PGHOME/bin/psql -h localhost -U postgres -d postgres -f pg_init.sql
```

`install_pg.sh` 脚本安装时依赖文件的完整版压缩包在此下载：<a href="https://yangjing.me/files/postgresql10-scripts.tar.gz" target="_blank">https://yangjing.me/files/postgresql10-scripts.tar.gz</a>

- **pg-pwfile**：在初始化数据库时设置默认管理员账户的密码
- **pg_hba.conf**：默认只允许 127.0.0.1/8 访问数据库，这里改成允许所有网段可访问
- **postgresql.conf**：修改数据库监听地址为 `*` ，监听所有本地网络地址
- **pg_init.sql**：创建一个普通账户 **yangjing** 和测试用数据库 **yangjing** ，密码也设置为 `yangjing`

安装后PG数据库管理管理员账号是 `postgres`，密码为 `postgres`。同时，还创建了一个普通账号：`yangjing` 和同名数据库 `yangjing`，密码也是 `yangjing`。

将 `/opt/local/pgsql/10.1/bin` 目录加入系统环境变量。

```
echo 'export PATH="/opt/local/pgsql/10.1/bin:$PATH" >> ~/.bashrc
. ~/.bashrc
```

使用如下命令来启动或停止PostgreSQL 10数据库

**启动数据库**

```
pg_ctl -D /opt/local/var/pgsql/10.1 -l logfile start
```

**停止数据库**

```
pg_ctl -D /opt/local/var/pgsql/10.1 -l logfile stop
```

## 体验 PG

输入以下命令访问PG数据库：

```
psql -h localhost -U yangjing -d yangjing -W
```

根据提示输入密码登录，进入 psql 的 **REPL** 界面。

```
Password for user yangjing:
psql.bin (10.1)
Type "help" for help.

yangjing=>
```

先建立一些测试表：

```sql
CREATE TABLE t_role (
  id         INT PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ
);
CREATE TABLE t_user (
  id         BIGSERIAL PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  roles      INT []       NOT NULL,
  data       JSONB,
  created_at TIMESTAMPTZ
);
INSERT INTO t_role (id, name, created_at) VALUES (1, '超级管理员', now()), (2, '管理员', now()), (3, '用户', now());
INSERT INTO t_user(name, roles, data, created_at) VALUES
  ('root', '{1}', '{"email":"root@yangjing.me"}', now()),
  ('羊八井', '{2,3}', '{"email":"yangjing"}', now()),
  ('哈哈', '{3}', '{"email":"haha@yangjing.me"}', now());
```

先来执行两个简单的 SELECT 查询：

```sql
yangjing=> select * from t_role;
 id |    name    |          created_at
----+------------+-------------------------------
  1 | 超级管理员 | 2018-02-01 22:03:17.168906+08
  2 | 管理员     | 2018-02-01 22:03:17.168906+08
  3 | 用户       | 2018-02-01 22:03:17.168906+08
(3 rows)

yangjing=> select * from t_user;
 id |  name  | roles |                 data                  |          created_at
----+--------+-------+---------------------------------------+-------------------------------
  2 | root   | {1}   | {"email": "root@yangjing.me"}       | 2018-02-01 22:06:21.140465+08
  3 | 哈哈   | {3}   | {"email": "haha@yangjing.me"}       | 2018-02-01 22:06:21.140465+08
  1 | 羊八井 | {2,3}   | {"email": "yangjing@yangjing.me"} | 2018-02-01 22:04:41.580203+08
(3 rows)
```

接下来，尝试一些 PG 特色特性。

### InsertOrUpdate

**插入或更新**，是一个很有用的特性，当在主键冲突时可以选择更新数据。在PG中，是使用 **ON CONFLICT** 来实现这个特性的。

```sql
INSERT INTO t_role (id, name, created_at)
VALUES (3, '普通用户', now())
ON CONFLICT (id)
  DO UPDATE SET name = EXCLUDED.name;
```

在常用的 INSERT 语句后面用 `ON CONFLICT (...) DO ....` 语句来指定在某个/些字段出现冲突时需要执行的语句。在 `on CONFLICT (...)` 里的参数需要是主键或唯一索引（可以为复合字段）。当冲突发生时则会执行 `DO ....` 后面的语句，这里我们选择更新 `name` 字段的值。`EXCLUDED` 是用户引用在 `VALUES ....` 部分我们将插入的数据，`EXCLUDED.name` 在这里就是 `'普通用户'` 。除 `DO UPDATE`，我们还可以使用 `DO NOTHING` 来简单的忽略插入时的主键冲突。

### SERIAL/BIGSERIAL

看看表 `t_user` 的结构：

```
yangjing=> \d t_user
                                       Table "public.t_user"
   Column   |           Type           | Collation | Nullable |              Default
------------+--------------------------+-----------+----------+------------------------------------
 id         | bigint                   |           | not null | nextval('t_user_id_seq'::regclass)
 name       | character varying(255)   |           | not null |
 roles      | integer[]                |           | not null |
 data       | jsonb                    |           |          |
 created_at | timestamp with time zone |           |          |
Indexes:
    "t_user_pkey" PRIMARY KEY, btree (id)
```

在建表时 `id` 字段的类型定义的是 **BIGSERIAL** ，但这里却是显示的 **bigint** 类型；另外，还多了一个默认值：`nextval('t_user_id_seq'::regclass)` 。这是 PG 中的 **序列** ，PG中使用序列来实现 **自增值** 的特性。

*序列：t_user_id_seq*

```
yangjing=> \d t_user_id_seq
                       Sequence "public.t_user_id_seq"
  Type  | Start | Minimum |       Maximum       | Increment | Cycles? | Cache
--------+-------+---------+---------------------+-----------+---------+-------
 bigint |     1 |       1 | 9223372036854775807 |         1 | no      |     1
Owned by: public.t_user.id
```

也可以先创建序列，再设置字段的默认值为该序列的下一个值。

```sql
CREATE SEQUENCE t_user_id2_seq INCREMENT BY 1 MINVALUE 1 START WITH 1;
```

这里创建一个序列，设置最小值为1，从1开始按1进行递增。

### 数组类型

在创建 `t_user` 表的 `roles` 字段时，使用了数组类型 `INT []` 。数组类型对于我们的数据建模来说很有用，使用得好可以大大减少关系表的数量。

**根据索引返回值**

```sql
yangjing=> SELECT id, name, roles[2], created_at FROM t_user;
 id |  name  | roles |          created_at
----+--------+-------+-------------------------------
  2 | root   |       | 2018-02-01 22:06:21.140465+08
  3 | 哈哈   |       | 2018-02-01 22:06:21.140465+08
  1 | 羊八井 |     1 | 2018-02-01 22:04:41.580203+08
(3 rows)
```

*注意：PG 中，索引下标从0开始*

**以行的形式输出数组元素**

```sql
yangjing=> SELECT id, unnest(roles) AS role_id FROM t_user;
 id | role_id
----+---------
  2 |       1
  3 |       3
  1 |       2
  1 |       1
(4 rows)
```

**包含查找**

```sql
yangjing=> SELECT * FROM t_user WHERE roles @> ARRAY[1,2];
 id |  name  | roles |                 data                  |          created_at
----+--------+-------+---------------------------------------+-------------------------------
  1 | 羊八井 | {2,1} | {"email": "yangjing@yangjing.me"} | 2018-02-01 22:04:41.580203+08
(1 row)
```

**重叠查找**

重叠查找和包含查找的不同之处在重叠查找只要匹配数组中的任意一个元素则为 true。

```sql
yangjing=> SELECT * FROM t_user WHERE roles && ARRAY[1,2];
 id |  name  | roles |                 data                  |          created_at
----+--------+-------+---------------------------------------+-------------------------------
  2 | root   | {1}   | {"email": "root@yangjing.me"}       | 2018-02-01 22:06:21.140465+08
  1 | 羊八井 | {2,1} | {"email": "yangjing@yangjing.me"} | 2018-02-01 22:04:41.580203+08
(2 rows)
```

**数组转换成字符串**

`array_to_string` 函数的第二个参数指定转换成字符串后使用的分隔字符。

```sql
yangjing=> SELECT id, name, array_to_string(roles, ',') AS role_ids FROM t_user;
 id |  name  | role_ids
----+--------+----------
  2 | root   | 1
  3 | 哈哈   | 3
  1 | 羊八井 | 2,1
(3 rows)
```

### JSON类型

TODO

### Tooltip

**随机获取一个用户**

使用 `random` 函数来排序，并返回第一条记录。

```sql
yangjing=> SELECT * FROM t_user ORDER BY random() LIMIT 1;
 id | name | roles |              data               |          created_at
----+------+-------+---------------------------------+-------------------------------
  3 | 哈哈 | {3}   | {"email": "haha@yangjing.me"} | 2018-02-01 22:06:21.140465+08
(1 row)

yangjing=> SELECT * FROM t_user ORDER BY random() LIMIT 1;
 id |  name  | roles |                 data                  |          created_at
----+--------+-------+---------------------------------------+-------------------------------
  1 | 羊八井 | {2,1} | {"email": "yangjing@yangjing.me"} | 2018-02-01 22:04:41.580203+08
(1 row)
```

## FDW

在之前创建的默认 PG 数据库之外，接下来将创建一个绑定到端口 `5433` 的另一个 PG 数据库。

***（注：PostgreSQL中，创建和操作 `xxx_fdw` 扩展需要管理员权限）***

### 使用 postgres_fdw 访问其它Postgres数据库

先创建第2个数据库，用于模拟远程访问。以下是创建第2个数据库的命令：

```
mkdir /opt/haishu/var/pgsql/10.1_2
echo "postgres" > pg-pwfile
/opt/haishu/local/pgsql/10.1/bin/initdb --pgdata=/opt/haishu/var/pgsql/10.1_2 --auth=ident --auth-host=md5 --encoding=UTF-8 --locale=zh_CN.UTF-8 --username=postgres --pwfile=pg-pwfile
rm pg-pwfile
```

数据库创建成功后会输入如下提示：

```
....
Success. You can now start the database server using:

    /opt/haishu/local/pgsql/10.1/bin/pg_ctl -D /opt/haishu/var/pgsql/10.1_2 -l logfile start
```

这里我们需要修改第2个数据库 `10.1_2` 监听端口号，以免和已安装数据库冲突。编辑 `/opt/haishu/var/pgsql/10.1_2/postgresql.conf` 文件，修改内容如下：

```
port = 5433
```

再使用 `/opt/haishu/local/pgsql/10.1/bin/pg_ctl -D /opt/haishu/var/pgsql/10.1_2 -l logfile start` 命令启动第2个数据库。

```
/opt/haishu/local/pgsql/10.1/bin/pg_ctl -D /opt/haishu/var/pgsql/10.1_2 -l logfile start
waiting for server to start.... done
server started
```

现在，第2个PG数据库已建好，我们分别登录两个数据库。

**使用账号：yangjing 登录第1个PG**

```
$ psql -h localhost -U yangjing -d yangjing
Password for user yangjing:
psql.bin (10.1)
Type "help" for help.

yangjing=>
```

**使用账号：postgres 登录第2个PG，并创建测试用户 `pg2` 和测试数据库 `pg2`**

```
]$ psql -h localhost -p 5433 -U postgres -d postgres
Password for user postgres:
psql.bin (10.1)
Type "help" for help.

postgres=# create user pg2 encrypted password 'pg2';
CREATE ROLE
postgres=# create database pg2 owner=pg2 template=template1;
CREATE DATABASE
postgres=# \c pg2
You are now connected to database "pg2" as user "postgres".
pg2=#
```

创建 `postgres_fdw` 扩展，以支持使用外部表的形式访问其它数据库。使用 `postgres_fdw` 主要步骤如下：

1. 安装扩展 ，[`CREATE EXTENSION`](https://www.postgresql.org/docs/10/static/sql-createextension.html)
2. 创建外部服务对象：[`CREATE SERVER`](https://www.postgresql.org/docs/10/static/sql-createserver.html)
3. 创建用户映射：[`CREATE USER MAPPING`](https://www.postgresql.org/docs/10/static/sql-createusermapping.html)
4. 创建外部表：[`CREATE FOREIGN TABLE`](https://www.postgresql.org/docs/10/static/sql-createforeigntable.html)或[`IMPORT FOREIGN SCHEMA`](https://www.postgresql.org/docs/10/static/sql-importforeignschema.html)

**操作示例**

*安装扩展*

```
pg2=# create extension postgres_fdw ;
CREATE EXTENSION
```

*创建外部连接数据库*

```
pg2=# CREATE SERVER foreign_server FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host 'localhost', port '5432', dbname 'yangjing');
CREATE SERVER
```

*创建用户映射*

```
pg2=# CREATE USER MAPPING FOR pg2 SERVER foreign_server OPTIONS (user 'yangjing', password 'yangjing');
CREATE USER MAPPING
```

*创建外部表*

```
CREATE FOREIGN TABLE foreign_t_role (
  id INT NOT NULL,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ
) SERVER foreign_server
  OPTIONS (schema_name 'public', table_name 't_role');
```

在创建用户映射时，是将本地的 `pg2` 用户映射到远程服务器用户的，我们需要使用 `pg2` 账号登录来访问外部表。首先给 `pg` 赋于权限：

```
pg2=# grant ALL ON TABLE foreign_t_role to pg2 ;
GRANT
```

使用 `pg2` 账号登录访问外部表

```
psql -h localhost -U pg2 -d pg2
pg2=> select * from foreign_t_role ;
 id |    name    |          created_at
----+------------+-------------------------------
  1 | 超级管理员 | 2018-02-01 22:03:17.168906+08
  2 | 管理员     | 2018-02-01 22:03:17.168906+08
  3 | 普通用户   | 2018-02-01 22:03:17.168906+08
(3 rows)
```

向外部表插入数据：

```
pg2=> INSERT INTO foreign_t_role(id, name, created_at) VALUES(4, '来宾', now());
INSERT 0 1
```

回到第1个数据库，我们可以看到由外部表插入进来的数据：

```
yangjing=> select * from t_role ;
 id |    name    |          created_at
----+------------+-------------------------------
  1 | 超级管理员 | 2018-02-01 22:03:17.168906+08
  2 | 管理员     | 2018-02-01 22:03:17.168906+08
  3 | 普通用户   | 2018-02-01 22:03:17.168906+08
  4 | 来宾       | 2018-02-02 11:47:02.296937+08
(4 rows)
```

### 使用 mysql_fdw 访问MySQL数据库

**mysql_fdw** 由 EnterpriseDB 公司提供，我们需要从源码开始编译它。[https://github.com/EnterpriseDB/mysql_fdw](https://github.com/EnterpriseDB/mysql_fdw)

安装 **mysql_fdw** 步骤如下：

1、下载源码包。
```
git clone https://github.com/EnterpriseDB/mysql_fdw
cd mysql_fdw
```

2、配置 `pg_config` 目录：`export PATH=/opt/local/pgsql/10.1/bin:$PATH`。

3、配置 `mysql_config` 目录。这里使用官方的 YUM 源安装 MySQL 5.7，详细的安装使用说明请查阅官方文档：[https://dev.mysql.com/doc/mysql-yum-repo-quick-guide/en/](https://dev.mysql.com/doc/mysql-yum-repo-quick-guide/en/)。
```
sudo rpm -ivh https://dev.mysql.com/get/mysql57-community-release-fc27-10.noarch.rpm
sudo dnf erase mariadb-*
sudo dnf makecache
sudo dnf install mysql-community-server mysql-community-devel
```

4、编译并安装 **mysql_fdw** 扩展
```
make USE_PGXS=1 install
sudo sudo ldconfig // 重建系统动态链接库缓存
```

**在 MySQL 中创建测试数据**

***（注：MySQL的使用非本文重点，请自行查阅相关文档）***

登录 MySQL 并创建测试表及插入测试数据：

```
SET time_zone = '+08:00';
CREATE TABLE t_book(
  isbn VARCHAR(255) PRIMARY KEY,
  title VARCHAR(255),
  created_at DATETIME);
INSERT INTO t_book(isbn, title, created_at) VALUES
('978-7-121-32529-8', 'Akka应用模式：分布式应用程序设计实践指南', '2017-10-01'),
('978-7-115-46938-0', 'Kafka技术内幕：图文详解Kafka源码设计与实现', '2017-11-01');
```

**在 PG 中访问 MySQL**

类似使用 **postgres_fdw**，使用 **mysql_fdw** 也需要 PG 数据库的管理员权限。

1、创建扩展：
```
CREATE EXTENSION mysql_fdw;
```

2、创建外部服务对象：
```
CREATE SERVER mysql_server FOREIGN DATA WRAPPER mysql_fdw OPTIONS (host '127.0.0.1', port '3306');
```

3、创建用户映射
```
CREATE USER MAPPING FOR yangjing SERVER mysql_server OPTIONS(username 'yangjing', password 'yang.Bajing2018');
```

4、创建外部表
```
CREATE FOREIGN TABLE foreign_t_book(
  isbn VARCHAR(255),
  title VARCHAR(255),
  created_at TIMESTAMPTZ
) SERVER mysql_server OPTIONS(dbname 'yangjing', table_name 't_book');
GRANT ALL ON TABLE foreign_t_book to yangjing ;
```

现在，可以在 PG 中访问并使用在 MySQL 中创建的表和数据了，和 **postgres_fdw** 一样，也可以远程修改原表的内容。

## 接下来

本文简单介绍了 PostgreSQL 10 的安装、使用和一些特性，下一篇文章从应用开发的角度来谈谈怎样使用 PG。介绍怎样使用 JDBC 来访问 PostgreSQL 数据库，使用 Scala 编程语言作示例。

