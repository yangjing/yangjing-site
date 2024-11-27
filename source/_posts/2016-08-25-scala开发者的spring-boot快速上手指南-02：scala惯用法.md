title: Scala开发者的Spring-Boot快速上手指南 02：Scala惯用法
date: 2016-08-25 22:11:21
categories: scala
tags:
  - scala
  - java
  - spring-boot
  - spring
  - gradle
---

*(这是一篇迟来的文章，从3月份计划到成文花了5个月多……以后需要避免这样的低效率。)*

之前写第一篇文章时，只是想试试在Spring中使用Scala。但现在随着工作的需要，已经决定在应用层基于Spring boot进行开发。后面的数据服务和数据整合部分将采用Akka。作者是一个Scala粉，但不脑残。鉴于团队、招人及社区生态多方面考虑，整体使用Scala技术栈还是比较困难的。之前就有考虑过把Spring和Scala结合起来。后来了解到挖财的技术选型，他们就是基于Spring和Scala的，还开源了很多不错的Spring Boot增强插件。这坚定了我之前的想法，也有了我5个月后续写第2篇的能量。

对于Scala还不熟悉的朋友可以先看看[《写给Java程序员的Scala入门教程》](http://www.yangjing.me/2016/07/24/%E5%86%99%E7%BB%99java%E7%A8%8B%E5%BA%8F%E5%91%98%E7%9A%84scala%E5%85%A5%E9%97%A8%E6%95%99%E7%A8%8B/)，好对Scala有个初步映像。

## 从Maven到Gradle

第一篇文章是基于Maven做项目配置的，现在换成了Gradle。原因？Spring官方默认都是基于Gradle了，而且现在很多大型的Java项目都是基于Gradle进行构建了。如：Android、Kafka（Linkdin整体采用Gradle）。再加上我是一个比较爱折腾的人，既然现在有时间，为什么不试试Gradle呢？

代码在这里：[https://github.com/yangjing/spring-boot-scala](https://github.com/yangjing/spring-boot-scala)，这次不但把构建工具换成了Gradle，还一步到位使用了多项目的构建方式，这样更符合真实开发的场景。
 **注意：在build.gradle配置中，需要重新设置Scala和Java源码的搜索路径，把Java源码路径移动Scala的搜索路径来。不然编译时会遇到Java代码找不到Scala代码符号问题
**

```gradle
    sourceSets {
        main {
            scala {
                srcDirs = ['src/main/scala', 'src/main/java']
            }
            java {
                srcDirs = []
            }
        }
        test {
            scala {
                srcDirs = ['src/test/scala', 'src/test/java']
            }
            java {
                srcDirs = []
            }
        }
    }
```

## 支持Scala数据类型

Spring Boot默认可以自动转换JSON数据格式到Java类型或反之，但怎样支持Scala数据类型呢？其实很简单，只需要加入`jackson-module-scala`依赖：

```gradle
compile("com.fasterxml.jackson.module:jackson-module-scala_$scalaLibVersion:2.8.0.rc2")
```

并添加`jacksonModuleScala` Bean 即可：

```java
    @Bean
    public Module jacksonModuleScala() {
        return new DefaultScalaModule();
    }
```

现在，我们就可以在Spring中自由的使用`case class`、`Scala Collection`、`Option`等类型和数据结构，甚至还可以和Java类型混合使用。比如我们把Java类型嵌入到Scala的case class里。

*User.java*

```java
public class User {
    private String name;
    private String nickname;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getNickname() {
        return nickname;
    }
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
```

*Message.scala*

```scala
case class Message(name: String,
                   age: Int,
                   user: User,
                   status: Option[Boolean]) {
  @BeanProperty val createdAt: LocalDateTime = LocalDateTime.now()
}
```

*Scala控制器 (ApiController.scala）：*

```scala
  @RequestMapping(path = Array("message"), method = Array(RequestMethod.POST))
  def message(@RequestBody reqMsg: Message): Seq[Message] = {
    List(
      reqMsg,
      reqMsg.copy(age = reqMsg.age + 1, status = Some(true))
    )
  }
```

使用Scala编写Spring控制器方法，有些和Java不一样的地方和Scala的惯用法：

- 注解属性为数组时，Scala必需显示使用数组形式传参。如`@RequestMapping`注解的`path`发型是一个数组类型，在Scala中需要显示传入一个数组类型的参数：`Array("message")`。
- Scala中，方法返回值类型是可以自动推导的。但在写Spring控制器方法时推荐显示注明返回类型。
- Scala的所有表达式都有值，且代码块最后一个表达式的值就是代码块的值。这样，在Scala的函数里不需要使用`return`显示返回数据，也不推荐使用`return`。

另外，若在Java代码中使用Scala的数据类型。如：case class。在Java中必需使用`new`关键字进行实例化，像Scala那样直接通过类名实例化是不支持的。

*Java控制器（WebController.java）：*

```java
    @RequestMapping(path = "message", method = RequestMethod.POST)
    public Message message(@RequestBody User user) {
        return new Message("Yang Jing", 30, user, new Some(false));
    }
```

测试效果如下：

```
$ curl -XPOST -H 'content-type: application/json;utf8' -d '{"user":"杨景","nickname":"羊八井"}' http://localhost:18080/web/message

{"name":"Yang Jing","age":30,"user":{"name":null,"nickname":"羊八井"},"status":false,"createdAt":"2016-08-25T17:22:50.841"}

$ curl -XPOST -H 'content-type: application/json;utf8' -d '{"name":"yangjing","age":30,"user":{"name":"杨景","nickname":"羊八井"}}' http://localhost:18080/api/message

[{"name":"yangjing","age":30,"user":{"name":"杨景","nickname":"羊八井"},"status":null,"createdAt":"2016-08-25T17:26:03.352"},{"name":"yangjing","age":31,"user":{"name":"杨景","nickname":"羊八井"},"status":true,"createdAt":"2016-08-25T17:26:03.352"}]
```

## Java Function 和 Scala Function[N]

Java 8开始，支持Lambda函数。但是Java的Lambda函数与Scala的函数类型是不兼容的（好消息是，从Scala 2.12开始，将兼容Java Lambda函数）。我们可以使用`scala-java8-compat`这个库来还算优雅的解决这个问题。

首先添加`scala-java8-comat`依赖：

```gradle
    compile("org.scala-lang.modules:scala-java8-compat_$scalaLibVersion:0.7.0")
```

在Scala中访问Java8 Function，可以使用如下方式：

```scala
import scala.compat.java8.FunctionConverters._

def(@RequestParam name: Optional[String], ...

  name.orElseGet(asJavaSupplier(() => reqMsg.name))
```

除了显示的使用`asJavaSupplier`来转换特定的Java8 Function，还可以使用`asJava`隐式转换来自动转换：

```scala
 name.orElseGet((() => reqMsg.name).asJava)
```

## 总结

也许你并不喜欢Scala，也不需要在Spring中使用Scala，Java 8也足够。但我希望能为你打开了一扇门，在JVM平台上还有如此有意思的语言。

**本系列文章**

- [Scala开发者的Spring-Boot快速上手指南 01](/2016/03/03/scala%E5%BC%80%E5%8F%91%E8%80%85%E7%9A%84spring-boot%E5%BF%AB%E9%80%9F%E4%B8%8A%E6%89%8B%E6%8C%87%E5%8D%97_01/)
- [Scala开发者的Spring-Boot快速上手指南 02：Scala惯用法](/2016/08/25/scala%E5%BC%80%E5%8F%91%E8%80%85%E7%9A%84spring-boot%E5%BF%AB%E9%80%9F%E4%B8%8A%E6%89%8B%E6%8C%87%E5%8D%97-02%EF%BC%9Ascala%E6%83%AF%E7%94%A8%E6%B3%95/)
