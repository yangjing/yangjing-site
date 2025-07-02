title: Hive 与 Spark
date: 2016-03-31 11:27:39
categories:

- data
- spark

tags:

- hive
- spark
- hadoop
- spark sql

---

- Spark 与 Hadoop 安装见此：[安装 Spark1.5 与 Hadoop2.6](http://www.yangjing.me/2016/02/27/%E5%AE%89%E8%A3%85spark1-5-2%E4%B8%8Ehadoop2-6-2/)

**注意：Spark 官方提供的二进制发行版是不支持 hive 的，需要自行编译。**

# 安装 hive

```
wget -c http://mirrors.aliyun.com/apache/hive/hive-1.1.1/apache-hive-1.1.1-bin.tar.gz
tar apache-hive-1.1.1-bin.tar.gz -C /opt/local
```

设置 hive 环境变量：

```
export HIVE_HOME="/opt/local/apache-hive-1.1.1-bin"
```

编辑 `$HIVE_HOME/conf/hive-site.xml`：

```xml
<configuration>
	<property>
		<name>javax.jdo.option.ConnectionURL</name>
		<value>jdbc:mysql://192.168.31.101/metastore_db?createDatabaseIfNotExist=true</value>
		<description>metadata is stored in a MySQL server</description>
	</property>
	<property>
		<name>javax.jdo.option.ConnectionDriverName</name>
		<value>com.mysql.jdbc.Driver</value>
		<description>MySQL JDBC driver class</description>
	</property>
	<property>
		<name>javax.jdo.option.ConnectionUserName</name>
		<value>hiveuser</value>
		<description>user name for connecting to mysql server</description>
	</property>
	<property>
		<name>javax.jdo.option.ConnectionPassword</name>
		<value>hive123</value>
		<description>password for connecting to mysql server</description>
	</property>
</configuration>
```

设置 MySQL 的 Hive 元数据库。

```
mysql -u root -p

mysql> CREATE DATABASE metastore_db;
Query OK, 1 row affected (0.00 sec)

mysql> CREATE USER 'hiveuser'@'%' IDENTIFIED BY 'hive123';
Query OK, 0 rows affected (0.00 sec)

mysql> GRANT all on *.* to 'hiveuser'@localhost identified by 'hive123';
Query OK, 0 rows affected (0.00 sec)

mysql> flush privileges;
Query OK, 0 rows affected (0.00 sec)
```

执行 `$HIVE_HOME/bin/hive` 即可进入 hive cli 控制台了。

# 一些注意事项

## hive.optimize.ppd BUG

在执行一些 hiveQL 操作时，

```
Exception in thread "main" java.lang.NoSuchMethodError: org.apache.hadoop.hive.ql.ppd.ExprWalkerInfo.getConvertedNode(Lorg/apache/hadoop/hive/ql/lib/Node;)Lorg/apache/hadoop/hive/ql/plan/ExprNodeDesc;
```

```
hive> set hive.optimize.ppd=false;
```

## **注意** hive jline 与 hadoop jline 起冲突

[https://cwiki.apache.org/confluence/display/Hive/Hive+on+Spark%3A+Getting+Started](https://cwiki.apache.org/confluence/display/Hive/Hive+on+Spark%3A+Getting+Started)

Hive 已经更新 Jlive2 版本，而 hadoop 还是使用的 0.9x 系列。可以这样设置来解决这个问题：优先使用用户提供的 jar 包。

```
export HADOOP_USER_CLASSPATH_FIRST=true
```

## 为 Spark 添加 mysql 驱动

编辑 `$SPARK_HOME/conf/spark-defaults.conf` 文件，设置以下依赖。当使用 `spark-submit`
提交任务时需要 `spark.executor.extraClassPath` 配置，而使用 `spark-shell`和`spark-sql` 等方式时需要 `spark.driver.extraClassPath` 配置。

```
spark.executor.extraClassPath	   /opt/local/libs/mysql-connector-java-5.1.38.jar
spark.driver.extraClassPath	       /opt/local/libs/mysql-connector-java-5.1.38.jar
```

也可以在使用 `spark-sql` 时添加命令行参数来设置 mysql 驱动：`--driver-class-path /opt/local/libs/mysql-connector-java-5.1.38.jar`
