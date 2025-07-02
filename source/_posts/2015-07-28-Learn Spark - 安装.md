title: Learn Spark - 安装
date: 2015-07-28 00:23:23
categories:

- data
- spark

tags:

- scala
- spark

---

## 安装

下载 `Spark 1.4.1`

```
wget -c http://www.interior-dsgn.com/apache/spark/spark-1.4.1/spark-1.4.1.tgz
```

编译 Spark，使用 `scala 2.11`

```
./dev/change-version-to-2.11.sh
mvn -Dscala-2.11 -DskipTests clean package
```

运行 `spark-shell`

```
./bin/spark-shell
15/07/23 17:18:48 WARN NativeCodeLoader: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable
Welcome to
      ____              __
     / __/__  ___ _____/ /__
    _\ \/ _ \/ _ `/ __/  '_/
   /___/ .__/\_,_/_/ /_/\_\   version 1.4.1
      /_/

Using Scala version 2.11.6 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0_40)
Type in expressions to have them evaluated.
Type :help for more information.
Spark context available as sc.
SQL context available as sqlContext.

scala>
```

看到以上信息就代表 `Spark` 已经安装好了。

## 简单的配置

修改 `$SPARK_HOME/conf/spark-env.conf` 设置如下参数：

```
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_40.jdk/Contents/Home"
export SPARK_SCALA_VERSION="2.11"
export SPARK_MASTER_IP="192.168.1.102"
export SPARK_LOCAL_IP="192.168.1.102"
export SPARK_WORKER_MEMORY="2G"
export SPARK_WORKER_CORE="2"
```

因为编译的是 `scala 2.11` 版本，所以应在配置文件里指定 `Spark` 以 scala 2.11 进行启动。

接着就可以 Standalone 模式启动 spark 了：`./sbin/start-all.sh`

## spark-submit

`Spark` 使用 `spark-submit` 部署执行程序， `bin/spark-submit` 可以轻松完成 `Spark` 应用程序在`local`、`Standalone`、`YARN`和`Mesos`上的快捷部署。我们提交一个最简单的 `WorldCount` 程序，代码如下：

```
package learnspark.intro

import org.apache.spark.{SparkContext, SparkConf}

object WordCount {
  def main(args: Array[String]): Unit = {
    println(args.length + " " + args.toList)
    if (args.length < 2) {
      println("run params: inputfile outputfile")
      System.exit(1)
    }

    val inputFile = args(0)
    val outputFile = args(1)
    val conf = new SparkConf().setAppName("wordCount")
    val sc = new SparkContext(conf)

    val input = sc.textFile(inputFile)
    val words = input.flatMap(_.split(' '))
    val counts = words.map((_, 1)).reduceByKey { case (x, y) => x + y }
    counts.saveAsTextFile(outputFile)
  }
}
```

使用以下脚本提交程序到 `Spark` 执行：

```
#!/bin/sh

rm -rf /tmp/wordcount

$SPARK_HOME/bin/spark-submit \
  --class learnspark.intro.WordCount \
  --master "spark://192.168.1.102:7077" \
  target/scala-2.11/learn-spark_2.11-0.0.1.jar \
  $SPARK_HOME/README.md /tmp/wordcount
```

- --class 指定要运行的 class
- --master 程序要运行的 master
- target/... 程序提交的 jar 包
- inputAttr [outputAttr ...] 程序执行参数
