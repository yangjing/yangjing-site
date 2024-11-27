title: JDBC 批量插入：MyBatis、PostgreSQL
date: 2020-06-27 17:07:20
category: java
tags:
  - jdbc
  - mybatis
  - batch-insert
  - postgresql

---

当一次插入数据很多时，使用批量插入可以显著提升性能，在此以 PostgreSQL 为例介绍几种批量插入的方式。

## JDBC batch execute

使用 JDBC 时，可以使用 `Statement#addBatch(String sql)` 或 `PreparedStatement#addBatch` 方法来将SQL语句加入批量列表，然后再通过 `executeBatch` 方法来批量执行。

### reWriteBatchedInserts=true

PostgreSQL JDBC 驱动支持 `reWriteBatchedInserts=true` 连接参数，可以将多条插入/更新语句修改成单条语句执行，如：`insert into test(name) values ('n'); insert into test(name) values ('m');` 修改为 `insert into test(name) values ('n'), ('m');` 。这可提供2到3倍的性能提升。

### 注意：executeBatch 返回值

***使用 `reWriteBatchedInserts=true` 参数后， `executeBatch` 执行后返回的 `int[]` 元素值将为 `-2。这是因为` `executeBatch` 的返回值将被重写为 `Statement#SUCCESS_NO_INFO`，这个参数值表示 JDBC 批量语句执行成功，但受其影响的行数计数不可用。

```java
    @Test
    public void batchInsert() {
        int[] rets = jdbcTemplate.batchUpdate("insert into test(id, name) values (?, ?)", Arrays.asList(
                new Object[]{1, "羊八井"},
                new Object[]{2, "杨景"},
                new Object[]{3, "yangjing"}
        ));
        System.out.println(Arrays.toString(rets));
    }
```

## Mybatis

### 使用 <foreach>

```xml
<insert id="batchInsert">
    INSERT INTO test (name, content) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.name}, ${item.content})
    </foreach>
</insert>
```



### 使用 mybatis-plus 的 IService

通过 `IService` 的 `saveBatch` 方法可实现批量插入功能，默认将按每 1000 条记录进行提交执行（非事物提交，如：3700 条记录将分 4 次执行 `executeBatch`，但仍在一个事物里）。

### 自定义 `insertBatch`，获得批处理影响的行数

mybatis-plus 的 `IService#saveBatch` 默认返回 `boolean` ，可以自定义实现一个 `insertBatch` 函数返回批量执行影响的行数*（注：实际上因为 `saveBatch` 函数使用了事物，根据参数是否执行成功，批量数据要么全部执行成功，要么全部执行失败，事实上并不需要一个返回影响行数的方法。此处可是演示下怎样自定义批量执行函数）*。

**DataIService**

```java

import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface DataIService<T> extends IService<T> {
    int insertBatch(List<T> entityList, int batchSize);

    default boolean insert(T entity) {
        return save(entity);
    }
}
```

**DataIServiceImpl**

```java

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.SqlSession;

import java.sql.Statement;
import java.util.*;
import java.util.function.BiConsumer;

public class DataIServiceImpl<M extends BaseMapper<T>, T>
        extends ServiceImpl<M, T>
        implements DataIService<T> {

    @Override
    public int insertBatch(List<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList)) {
            return 0;
        }
        String sqlStatement = sqlStatement(SqlMethod.INSERT_ONE);
        List<BatchResult> rets =
            batchExecute(entityList,
                         batchSize,
                         (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
        return rets.stream()
                .mapToInt(result -> Arrays.stream(result.getUpdateCounts())
                .map(n -> n == Statement.SUCCESS_NO_INFO ? 1 : n).sum())
                .sum();
    }

    protected <E> List<BatchResult> batchExecute(Collection<E> list,
                                                 int batchSize,
                                                 BiConsumer<SqlSession, E> consumer) {
        Assert.isFalse(batchSize < 1, "batchSize must not be less than one");
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        final List<BatchResult> results = new LinkedList<>();
        executeBatch(sqlSession -> {
            int size = list.size();
            int i = 1;
            for (E element : list) {
                consumer.accept(sqlSession, element);
                if ((i % batchSize == 0) || i == size) {
                    List<BatchResult> rets = sqlSession.flushStatements();
                    results.addAll(rets);
                }
                i++;
            }
        });
        return results;
    }
}
```

对 `List<BatchResult> rets ` 进行聚合计数获得受影响的行数时需要注意判断 `BatchResult#getUpdateCounts` 返回的 `int[]` 元素值是否为 `Statement.SUCCESS_NO_INFO` 。
