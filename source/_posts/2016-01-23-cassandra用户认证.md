title: Cassandra 用户认证
date: 2016-01-23 11:55:37
categories:

- data
- cassandra

tags:

- cassandra

---

Cassandra 默认是不需要用户名和密码登录的，这样其实并不安全。

修改配置文件：conf/cassandra.yaml 启动用户名密码登录：

```yaml
authenticator: PasswordAuthenticator
authorizer: CassandraAuthorizer
```

重新启动 Cassandra，再次使用 bin/cqlsh 登录会提示 **AuthenticationFailed('Remote end requires authentication.',)**。这时使用用户名和密码登录即可。

```
./cqlsh 192.168.0.101 -u cassandra -p cassandra
```

使用 PasswordAuthenticator 后，cassandra 会默认创建 super user，用户名和密码均为：cassandra。那么，如何修改该 super user 的密码呢？

```
alter user cassandra with password 'cassandra1';
```

执行语句后密码立即生效。

**创建用户**

```
create user user1 with password 'password1';
```

**分配权限**

```
GRANT ALL PERMISSIONS ON KEYSPACE data TO data;
```

_建议分配可访问表_

```
GRANT select PERMISSION ON system.size_estimates TO devser;
GRANT select PERMISSION ON system_auth.roles TO devser;
```

分配权限语句如下：

```
GRANT permission_name PERMISSION
| ( GRANT ALL PERMISSIONS ) ON resource TO user_name | role_name
```

其中 permission_name 为权限列表，有如下：

- ALL
- ALTER
- AUTHORIZE
- CREATE
- DROP
- MODIFY
- SELECT

resource 为被分配的资源，如下几种：

- ALL KEYSPACES
- KEYSPACE keyspace_name
- TABLE keyspace_name.table_name

## 集群安全

- authenticator: org.apache.cassandra.auth.PasswordAuthenticator: 用户密码将保存在 system_auth.credentials 表
- authorizer: org.apache.cassandra.auth.CassandraAuthorizer: 用户权限将保存在 system_auth.permissions 表。

system_auth 的默认 replication 因子为 1，这在集群中是非常危险的。若其中一个节点挂掉，而正好账号相关数据只保存在挂掉的那一台上会造成登录时不能通过授权认证。最好通过 `ALTER KEYSPACE` 命令修改默认设置。

**SimpleStrategy**

```
ALTER KEYSPACE "system_auth" WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };
```

**NetworkTopology**

```
ALTER KEYSPACE "system_auth" WITH REPLICATION = {'class' : 'NetworkTopologyStrategy', 'dc1' : 2, 'dc2' : 3};
```

_注：使用`NetworkTopology`需要设置`cassandra.yaml`的`endpoint_snitch: GossipingPropertyFileSnitch`等具有机架感知功能的 snitch_
