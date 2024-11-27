title: CRUDer 的自我修养：PostgreSQL、JDBC、MyBatis、R2DBC
date: 2020-08-09 17:02:00
category: java
tags:
  - postgresql
  - jdbc
  - mybatis
  - mybatis-plus
  - r2dbc
  - jackson
  - enum
---

这是一系列文章的目录，对于一个合格的 CRUD 程序猿/媛、码农、IT民工，更高效的进行 CRUD 是我们孜孜不倦的追求！本文是系列文章的序文，首先介绍各技术的亮点，再在之后的单独文章里详细介绍各技术的功能、优势、技巧等……通过对这 4 个主题的介绍，增进我们更好的进行 CRUD 开发。

## PostgreSQL

本系列文章以 PostgreSQL（以下简称：PG）为例讲解 SQL，对于 CRUDer ，SQL 是基础中的基础！我们应逃出 ORM 的束缚，在 SQL 的海洋里乘风破浪。这里首先介绍 PG 的其中 4 个亮点技术：

### 数组

其实 JDBC 标准本身是支持数组的，而且大部分数据库也支持数组类型，但日常开发中这个特性使用得还是比较少。在业务建模中应用数组是很常见的需求，比如一篇文章的 TAG 标签列表、组织的管理员（用户ID）列表等很多对于事实表需要存储少量列表数据的情况。而对于列表数据很多的情况使用数组存储就不大合适，继续使用一张关系表是个有效的解决方案，比如组织内的成员列表，因为一个组织内的成员可能很多，可能成千上万；这么大的一个数组是不必要的，而且通常业务上也不会一次性对这么大的一个数组进行查询或处理。

在了解了数组类型的适用范围后，我们来看看 PG 中具体怎样定义和使用数组。PG 的数组类型是一个完备的类型，支持几乎所有的类型的数组，如：`int[]`、`text[]`、`numeric[]`、`timestamp[]` 等。可以看到，要声明一个列为数组类型，只需要在普通的类型后面加个中括号 `[]` 即可。我们可以 *通过中括号并由 `array` 修饰* 的方式来定义一个数组：

```sql
insert into t_test(topic, tags, user_ids, create_time)
values ('topic-002', array ['Java','Scala','Javascript','SQL'], array [2,3,4], now());
```

### JSON

现在，越来越多的关系型数据库支持 JSON 了，这使关系型数据库在某种程度上具备了 NoSQL 的特性（灵活性），同时还拥有 NoSQL 不具备的关联查询、事物等易用的特性。可以说，在 PG 中使用 JSON（`jsonb`）类型会是一个比使用 MongoDB 更好的一个选择！因为 PG 的 JSON 融合了 NoSQL 数据库的模式灵活性，同时还具备关系型数据库的关系查询、事物、索引等能力，MongoDB 实际上在创建二级索引后性能非常的差，至于 K/V ，有很多比 MongoDB 更好的选择……

PG 中有两种 JSON ：

1. `jsonb`：将解析后的 JSON 结构以二进制的方式存储，类型 MongoDB 的 BSON 那样，有查询优化，同时可以为某些字段创建索引；
2. `json`：按字符串存储，在每次使用时再转换成 JSON，写入速度更快，占用存储空间相对 `jsonb` 更少，但查询速度更慢，且不能创建索引。

```sql
insert into t_test(metadata)
values ('{"title":"CRUDer 的自我修养","author":"杨景"}'::jsonb);
```

### returning

通常情况下，除了 `select` 语句外 `update`、`delete`、`insert` 都只能返回一个代表此操作影响了的记录行数。如果你想在 **修改** 操作后紧接着马上获得操作的那条记录，那你得再进行一次 `select` 查询。使用 `returning` 语句，你可以在 `update`、`delete`、`insert` 语句执行后马上将受影响记录的数据返回，这可以在语句后紧接使用 `returning *` 实现；甚至你还可以指定要返回的例，而不用将所有列都返回 `returning id, create_time` 。

```sql
insert into t_test(metadata)
values ('{"title":"CRUDer 的自我修养","author":"杨景"}'::jsonb)
returning id;
```

此条语句在插入成功后返回插入记录的自增主键值。

### batch insert

PG 在 SQL 语法层面支持批量插入（虽然 `COPY` 更快，但它非 SQL 标准），类似的 MySQL 也支持 SQL 层面的批量插入特性：

```sql
insert into t(name) values
('羊八井'),
('杨景');
```

### Insert on conflict

`insert on conflict` 就是 PG 中的一个 **杀手级** 功能，真的是太好用了。它可以在你插入数据起冲突时（主键、唯一键、唯一索引）由你选择是原地更新数据还是什么也不做（忽略）。这可以简化 ***查询-判断-写入/更新*** 逻辑，而且因为少一次数据库访问，在性能上也会有提升。另外，若你选择了 `do update set`，在更新时你还可以精确的控制更新方式，如：更新某几个字符，甚至在更新语句时执行各种计算（`update_count = update_count + 1`）…… 这里有一个示例：

```sql
insert into t_test(topic, tags, user_ids, create_time, update_time)
values (?, ?, ?, ?, ?)
on conflict (topic) do update set tags        = excluded.tags,
                                  user_ids    = excluded.user_ids,
                                  update_time = coalesce(excluded.update_time, excluded.create_time);
```

这里有一个技巧 `coalesce(excluded.update_time, excluded.create_time)`，`coalesce` 函数在第一个参数为 `null` 的时候取第二个参数值，并依此类推直到最后个参数。这样，当 `update_time` 未设置值的时候就使用 `create_time` 的值，理论上说，`create_time` 应该是有设置值的。

*MySQL 也有一个类似的语法 `insert on duplicate key`，从语法上就能看出它的功能比 PG 要弱一点，它限制为 **主键** 冲突时更新，而且更新时还不能选择更新的字段。*

### 递归查询

递归查询应用了 PG 的 **CTE** （common table expressions，通用表表达式）特性。在很多业务场景中很有用，比如：生成树形菜单、组织结构……

```sql
with recursive org_tree as (
    select id, name, parent from ig_org where id = ?
    union all
    select ig_org.id, ig_org.name, ig_org.parent from ig_org, org_tree where org_tree.parent is not null and ig_org.id = org_tree.parent
) select id, name from org_tree
```

这是一个通过组织 ID 返回组织的完整路径的 SQL 查询语句，通过 PG 的递归查询，查找它的父级组织（父级的低级，一直递归向上找）。

`with org_tree as (....)` 就是通用表表达式，小括号内的查询结果将缓存在 `org_tree` 临时表里。`recursive` 关键字表明这个通用表查询允许递归查询，`union all` 前面是非递归部分，后面是递归部分。首先通过组织 ID 找到指定的组织，再由找到的组织的 `parent`（父组织 ID）与 `ig_org` 表关联查询更新 `org_tree`，并递归查找直到 `org_tree.parent` 为 `null` 为止。

使用 `union` 将对整个结果集进行去重，`union all` 不进行去重返回所有记录。

## JDBC

Java 提供了标准的数据库访问接口： **JDBC**，我们通过它可以操作各种数据库。PG 的 JDBC 驱动实现了 JDBC 4.1 规范，同时还提供了一些很有用的特性。

### batch insert

PG 有一个连接参数可以对 batch insert 进行优化，将多条 SQL 语句转换成单条语句 `insert into test(name), values ('..'), ('...')` 的形式，这可以有 2到3 倍的性能提升。只需要将以下参数加到连接字符串上即可：

```
?reWriteBatchedInserts=true
```

### 时区、字符集

有时候可能需要指定 JDBC 连接的时区和字符集，可以在 connection 连接时设置。在 Spring Boot 可以通过如下配置进行设置：

```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: "SET TIME ZONE 'Asia/Chongqing';SET CLIENT_ENCODING TO 'UTF-8';"
```

## Mybatis

Java 生态下 ORM 工具有很多，常用的有：JPA（Hibernate、Eclipse TopLink）、Mybatis、JOOQ等。国内 Mybatis 用得比较多，是因为它灵活、高效，在搭配 `mybatis-plus` 类似的增强工具后，以能实现类似 JPA 的 **自动** 查询能力，同时还不失其强大的自定义和控制能力。若你受过 `JPQL` 的折磨，你能够理解到这一点的……

### `<select>` 不只查询

PG 支持 `returning` 语句，但是 Mybatis 的 `<insert>`、`<update>`、`<delete>` 标签只能返回 `int` 值，这时你可以在 `<select>` 标签里执行 **修改** 语句。如：

```xml
<select id="insert" resultMap="TestDO">
insert into t_test(metadata)
values ('{"title":"CRUDer 的自我修养","author":"杨景"}'::jsonb)
returning id;
</select>
```

### Mybatis-plus

使用原生 Mybatis，你所有的语句都得自己手工写 SQL（使用 XML 或 注解）；然后要执行分页查询还得自己实现一个 `Intercepter` ，对于一个懒人，能否靠在别人的肩膀上呢？[mybatis-plus](https://mp.baomidou.com/) 就是可以依靠的肩膀（**NB**）。

### 枚举

枚举是一个很好的工具，但 JDBC 默认不支持，或者大部分 ORM 库都将枚举序列化成字符串，可实际上我们希望它能够被当成 **Integer** 来进行序列化。（对于枚举应用还比较少的同学，可以先看看我的另一篇文章：[Java 枚举：有效应用](https://yangjing.gitee.io/2020/08/08/java-%E6%9E%9A%E4%B8%BE%EF%BC%9A%E6%9C%89%E6%95%88%E5%BA%94%E7%94%A8/)）。

Mybatis-plus 提供了对枚举的更好的支持，文档在此：[https://mp.baomidou.com/guide/enum.html](https://mp.baomidou.com/guide/enum.html)  。

### jsonb

在 Mybatis-plus 里使用 JSONB 类型，可以使用 Mybatis-plus 提供的 **字段类型处理器** [https://mp.baomidou.com/guide/typehandler.html](https://mp.baomidou.com/guide/typehandler.html) 。

而对于直接使用 mybatis 的用户，也可以直接自定义 `TypeHandler`。在此可以找到自定义 `TypeHandler` 的代码：[https://github.com/yangjing/spring-example/tree/develop/example-common/src/main/java/me/yangjing/springreactive/mybatis/handlers](https://github.com/yangjing/spring-example/tree/develop/example-common/src/main/java/me/yangjing/springreactive/mybatis/handlers) 。

实现自定义 `JsonNodeTypeHandler` 后，可通过 mybatis-plus 的配置参数全局加载：

```yaml
mybatis-plus:
  type-handlers-package: me.yangjing.springreactive.mybatis.handlers
```

这样，就可以像使用 `String`、`LocalDateTime` 等类型一样在 Mybatis 里使用 Jackson（支持`JsonNode`、`ObjectNode`、`ArrayNode`）了。

### 数组

Mybatis 默认有提供数组类型的 `TypeHandler`，但并未启用，需要在使用时使用 `typehandler=org.apache.ibatis.type.ArrayTypeHandler` 手工指定。但是，可以像前面的 `JsonNodeTypeHandler` 一样，定义自己的各类数组 `TypeHandler`，并通过 `type-handlers-package` 配置全局启用：

```java
@MappedTypes(Double[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class DoubleArrayTypeHandler extends ArrayTypeHandler {
}

@MappedTypes(Integer[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class IntegerArrayTypeHandler extends org.apache.ibatis.type.ArrayTypeHandler {
}

@MappedTypes(String[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends ArrayTypeHandler {
}

// ....
```

## R2DBC

**拥抱反应式**

[R2DBC](https://spring.io/projects/spring-data-r2dbc) 是由 Spring 社区推动的，为 [reactor](https://projectreactor.io/) 提供了一套异步、反应式流的关系数据库访问驱动。它可以更好的适配 Spring Webflux 编程模型。对于 **反应式**，也许你可以读一读 [《反应式宣言》](https://www.reactivemanifesto.org/zh-CN) 。

### 遍历全表数据

R2DBC 返回结果是 `Publisher<T>` ，spring-data-r2dbc 将其包装成了 `Flux<T>`。它在需要遍历数据的时候非常好用，你不在需要自己手动分页来查询，也不需要通过设置 JDBC 的游标来通过游标查询，就按照普通的数据流一条一条的读取记录就可，非常简单。

```java
dataSchemaRepository
        .findAll()
        .subscribe(
                data -> log.info("处理数据为：{}", data),
                error -> log.error("处理异常：{}", error.getMessage(), error),
                () -> log.info("数据流已完成！")
        );
```

## 总结

本文简略的介绍了 SQL（PostgreSQL）、JDBC、Mybatis 和 R2DBC 的使用和一些技巧，灵活运行这些特性和功能可以显著提升我们的开发效率，让我们成为一个更有价值的 CRUDer。远离 996，拥抱 965 ！
