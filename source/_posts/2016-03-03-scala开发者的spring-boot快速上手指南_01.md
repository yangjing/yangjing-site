title: Scala开发者的Spring-Boot快速上手指南 01
date: 2016-03-03 21:02:53
categories: scala
tags:
  - scala
  - spring-boot
  - spring
  - sbt
  - java
  - maven
---

做为一个Scala爱好者，是很想基于 [Lightbend](http://www.lightbend.com/) 的一套架构进行软件开发的。[Play](https://playframework.com/)，[Akka](http://akka.io/)，[Scala](http://scala-lang.org/)，[Spark](http://spark.apache.org/)……。不过理想很丰满，现实却很骨感。鉴于那批原教旨主义者，他们对 [Spring](http://spring.io/) 已经疯狂迷恋，我等讲道理、讲实际的人也只好将 Scala 与 Spring Boot 进行整合。这两兄弟是和睦的，是友好的，并不是有你无他，完全可以在能力和现实中实现一个美好的平衡。

（文章查考了：[Scala开发者的SpringBoot快速入门指南
](http://afoo.me/posts/2015-07-21-scala-developers-springboot-guide.html)，谢谢王福强老师的分享。）

（本文示例在：[https://github.com/yangjing/spring-boot-scala/tree/v01](https://github.com/yangjing/spring-boot-scala/tree/v01)）

**创建支持Scala的Spring Boot应用**

Java社区一般使用 [Maven](http://maven.apache.org/)或[Gradle](http://gradle.org/) 管理项目构建，鉴于 Maven 的使用更多，本文将只讲解 Maven 下的配置，Gradle 的配置请读者自行参考网上实现。当然，作为一个 Scalar ，基于 [Sbt](http://www.scala-sbt.org/) 的配置是肯定会讲到的，在 Sbt 下还有一个神器：[sbt-package-native](http://www.scala-sbt.org/sbt-native-packager/) ，敬待下文详解。

## Maven项目

首先来看看配置文件 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>me.yangjing.springscala</groupId>
    <artifactId>springscala</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>springscala</name>
    <description>Demo project for Spring Boot</description>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>1.3.1.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>1.8</java.version>
        <scala.version>2.11.7</scala.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.scala-lang</groupId>
            <artifactId>scala-library</artifactId>
            <version>${scala.version}</version>
        </dependency>
        <dependency>
            <groupId>org.scala-lang</groupId>
            <artifactId>scala-compiler</artifactId>
            <version>${scala.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>net.alchim31.maven</groupId>
                <artifactId>scala-maven-plugin</artifactId>
                <version>3.2.2</version>
                <executions>
                    <execution>
                        <id>compile-scala</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>add-source</goal>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>test-compile-scala</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>add-source</goal>
                            <goal>testCompile</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <recompileMode>incremental</recompileMode>
                    <compileOrder>Mixed</compileOrder>
                    <scalaVersion>${scala.version}</scalaVersion>
                    <args>
                        <arg>-deprecation</arg>
                    </args>
                    <jvmArgs>
                        <jvmArg>-Xms64m</jvmArg>
                        <jvmArg>-Xmx1024m</jvmArg>
                    </jvmArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

对于 Spring 部分的配置，这里就不多说了。重点说下 Scala 方面的配置。

首先你需要加入 Scala 的依赖库，这里加入了 `scala-library` 和 `scala-compiler` 两个包的依赖，这是在Java环境下编译 Scala 代码所必需的。

其次就是要添加 `scala-maven-plugin` 插件，以让 Maven 支持对 Scala 的编译操作。这里需要注意的是 `recompileMode` 指令，推荐使用 **incremental** 配置。

另一个需要注意的配置荐就是 `compileOrder` ，当项目同时使用了Java和Scala两种语言时它决定了两者的编译顺序。默认是 **Mixed** （混合顺序），其它还有两个选项是：**JavaThenScala** 和 **ScalaThanJava**。

## 编写Scala代码

现在我们可以使用 Scala 来编写 spring boot 应用了，先来写一个 POJO 类。

```scala
class Message {

  @BeanProperty
  var value: String = _

}
```

再来写一个 Controller ：

```scala
@RestController
@RequestMapping(Array("/api"))
class ApiController {

  @RequestMapping(value = Array("/hello"), method = Array(RequestMethod.GET))
  @ResponseBody
  def hello(): Message = {
    TimeUnit.SECONDS.sleep(6)
    val message = new Message()
    message.value = "Hello, Scala for Spring!"
    message
  }

}
```

这里需要注意的是注解参数的传递方式，Scala 里没像 Java 一样会自动把字符串转换成注解里定义的数组参数，我们需要显示的定义一个数据传入。而且传入注解的参数值只能是一个常量，比如：`"/api/user"` ，不能像这样：`Constant.API_PATH + "/user"`。

## 运行项目

打开终端，执行以下指令启动 spring boot 应用：

```shell
mvn spring-boot:run
```

再打开一个终端，测试 API 功能：

```shell
time curl -v http://localhost:8080/hello
```

## sbt项目

这里使用了 `.scala` 的方式来配置 **sbt** 项目。sbt 的配置文件在项目根目录下的 `project` 目录：

```
project/
├── Build.scala
├── build.properties
```

在 `build.properties` 文件内指定了 sbt 的版本号，`Build.scala` 文件设置了详细的 Sbt 工程设置及编译选项等。我们先来看看配置文件内容：

```scala
import sbt.Keys._
import sbt._

object Build extends Build {

  override lazy val settings = super.settings :+ {
    shellPrompt := (s => Project.extract(s).currentProject.id + " > ")
  }

  lazy val root = Project("springscala", file("."))
    .settings(
      description := "Spring boot scala",
      version := "0.0.1",
      homepage := Some(new URL("https://github.com/yangjing/spring-boot-scala")),
      organization := "me.yangjing",
      organizationHomepage := Some(new URL("http://www.yangjing.me")),
      startYear := Some(2016),
      scalaVersion := "2.11.7",
      scalacOptions ++= Seq(
        "-encoding", "utf8",
        "-unchecked",
        "-feature",
        "-deprecation"
      ),
      javacOptions ++= Seq(
        "-source", "1.8",
        "-target", "1.8",
        "-encoding", "utf8",
        "-Xlint:unchecked",
        "-Xlint:deprecation"
      ),
      offline := true,
      libraryDependencies ++= Seq(
        _springBootStarterWeb,
        _springBootStarterTest))

  val verSpringBoot = "1.3.3.RELEASE"
  val _springBootStarterWeb = "org.springframework.boot" % "spring-boot-starter-web" % verSpringBoot
  val _springBootStarterTest = "org.springframework.boot" % "spring-boot-starter-test" % verSpringBoot

}
```

没错，sbt 的配置文件就是实打实的 Scala 代码。sbt 也有一套像 Gradle 一样的 DSL 来定义项目配置信息，是以后缀 `.sbt` 结尾的文件。不过个人还是认为直接使用 Scala 代码做配置更直观、清晰。

具体配置含义，我这里就不细讲了。官方有很详细的教程和文档说明：[sbt Reference Manual ](http://www.scala-sbt.org/0.13/docs/zh-cn/index.html)。

## 总结

Scala 从各方面来看，配置和代码，期简洁性都是优于Java。对于一个Scala爱好者，你的选择很多，比如：[Play](https://playframework.com/)。不过，很多时候你需要考虑到各方面的利益。公司、团队、意愿等各方面。现实中，Spring 在 Java 生态圈还是使用最多的技术，Spring 框架的使用本身是未限制 JVM 平台上的各种主义的，它也可以很好的支持：[Groovy](http://www.groovy-lang.org/)、[Kotlin](https://kotlinlang.org/) 甚至 [Clojure](http://clojure.org/)……

本文简单讲解了怎样配置 pom.xml 以在 Spring boot 中支持 Scala，以及 sbt 工程又是怎样支持 Spring 的。这即是 Scala 开发者的 Spring boot 入门指南，亦可是 Java 程序员的 Scala 第一次尝试。希望能打开一座桥梁，让 Java 程序员开眼界，Scala 开发者务实。

下一篇文章我想进一步介绍下使用 Scala 开发 Spring 应用的一些好处和惯用法，接下来的文章还会讲到怎样结合 Akka 基于 Spring 开发一个 WebSocket 应用。

**本系列文章**

- [Scala开发者的Spring-Boot快速上手指南 01](/2016/03/03/scala%E5%BC%80%E5%8F%91%E8%80%85%E7%9A%84spring-boot%E5%BF%AB%E9%80%9F%E4%B8%8A%E6%89%8B%E6%8C%87%E5%8D%97_01/)
- [Scala开发者的Spring-Boot快速上手指南 02：Scala惯用法](/2016/08/25/scala%E5%BC%80%E5%8F%91%E8%80%85%E7%9A%84spring-boot%E5%BF%AB%E9%80%9F%E4%B8%8A%E6%89%8B%E6%8C%87%E5%8D%97-02%EF%BC%9Ascala%E6%83%AF%E7%94%A8%E6%B3%95/)
