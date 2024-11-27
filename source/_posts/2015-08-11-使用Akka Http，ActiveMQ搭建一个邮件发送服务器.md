title: 使用Akka Http，ActiveMQ搭建一个邮件发送服务器
date: 2015-08-11 18:11:27
categories: scala
tags:
- scala
- akka
- activemq
- email
---

代码地址：[https://github.com/yangjing/scala-applications/tree/master/email-server](https://github.com/yangjing/scala-applications/tree/master/email-server)

应用功能是实现一个基于队列的邮件发送服务，每个邮件发送者（使用smtp协议）作为一个`sender`。多个`sender`可以在同一个组`(group)`中，每个组中的`sender`将串行发送邮件。

邮件内容可以通过`REST API`提交，以可以使用`JMS`发布到`ActiveMQ`中，邮件服务器将从中读取邮件内容。


## 快速开始

**配置：**

推荐在启动时使用`-Dapplication.file=/usr/app/etc/emailserver/application.conf`指定配置文件

```json
emailserver {
  server {
    interface = "0.0.0.0"
    port = 9999
  }

  emails {
    email1 {
      userName = "email1@email.com"
      password = ""
      smtp = ""
      smtpPort = 0
      ssl = true

      # 同一个组内的邮件会串行发送，此key可忽略
      group = "1"
    }

    email2 {
      userName = "email2@email.com"
      password = ""
      smtp = ""
      smtpPort = 0
      ssl = true
      group = "2"
    }
  }

  activemq {
    url = "tcp://127.0.0.1:61616"
    emailQueueName = "EmailQueue"
  }
}
```

`emails`定义邮件发送者（可用于`stmp`服务进行邮件发送的邮箱信息）。可以定义多个邮件发送者，但每个邮件发送者的`key`不能相同。比如：`email1`和`email2`

`activemq`定义了`ActiveMQ`服务的连接参数。

**编译与运行：**

```
# 编译
./sbt assembly

# 运行
java -Dapplication.file=/usr/app/etc/emailserver/application.conf -jar target/scala-2.11/email-server.jar
```

**测试REST服务：**

```
# 查询存在的邮箱发送者：
curl http://localhost:9999/email/users

# 发送测试邮件
curl -v -XPOST -H "Content-Type: application/json" \
  -d '{"sender":"Info@email.cn", "subject":"测试邮件","to":["user1@email.cn", "user2@email.cn"],"content":"测试邮件内容咯~"}' \
  http://localhost:9999/email/send
```

**使用JMS发送邮件：**

*安装activemq*

```
Downloads：http://mirrors.hust.edu.cn/apache/activemq/5.11.1/apache-activemq-5.11.1-bin.tar.gz

tar zxf ~/Downloads/apache-activemq-5.11.1-bin.tar.gz
cd apache-activemq-5.11.1/
./bin/activemq-admin start
```

`activemq`管理控制台地址：[http://localhost:8161/admin/](http://localhost:8161/admin/)，账号：`admin`，密码：`admin`。


JMS TCP地址：`tcp://localhost:61616`

*生产测试邮件*

修改[`EmailProducers.scala`](https://github.com/yangjing/scala-applications/blob/master/email-server/src/main/scala/me/yangjing/emailserver/demo/EmailProducers.scala)的`activeMqUrl`及`mapMessage`参数，运行`EmailProducers`生产一个邮件发送请求。


## Akka Http

`Akka Http`是一个完整的`server`和`client`端HTTP开发栈，基于`akka-actor`他`akka-stream`。它不是一个`WEB`框架，而是提供了可以构建`Http`服务的工具包。

`Akka Http`有一套很直观的`DSL`来定义路由，自然的形成了一个树型的路由结构。如[Routes](https://github.com/yangjing/scala-applications/blob/master/email-server/src/main/scala/me/yangjing/emailserver/route/Routes.scala)：

```scala
pathPrefix("email") {
  path("send") {
    post {
      entity(as[JsValue].map(_.as[SendEmail])) { sendEmail =>
        onComplete(emailService.sendEmail(sendEmail)) {
          case Success(value) =>
            value match {
              case Right(msg) => complete(msg)
              case Left(msg) => complete(StatusCodes.Forbidden, msg)
            }

          case Failure(e) => complete(StatusCodes.InternalServerError, "SendEmail an error occurred: " + e.getMessage)
        }
      }
    }
  } ~
    path("users") {
      get {
        onComplete(emailService.getEmailSenders) {
          case Success(emailSenders) => complete(Json.toJson(emailSenders))
          case Failure(e) => complete(StatusCodes.InternalServerError, "An error occurred: " + e.getMessage)
        }
      }
    }
}
```

`path` -> `post` -> `entity` -> `complete` 式的函数嵌套，很直观的定义出了声明式的树型`REST URI`结构，层次分明、逻辑清晰。`entity`函数用于解析`Http Body`，将其映射成希望的数据类型，可自定义映射方法。

`onComplete`函数用在返回类型是一个`Future[T]`时，提供了快捷的方式把一个`Future[T]`类型的响应转换到`complete`。


## 邮件发送

邮件的发送采用了串行发送的方式，这个模式刚好契合`Actor`默认邮箱的`FIFO`处理形式。把收到的邮件发送请求告诉一个`actor`，`actor`再从邮箱里取出，并组装成`XXXXEmail`（邮件发送使用了[commons-email](http://commons.apache.org/proper/commons-email/)）后发送出去。

首先，程序将收到的邮件发送请求交给[`EmailMaster`](https://github.com/yangjing/scala-applications/blob/master/email-server/src/main/scala/me/yangjing/emailserver/service/actors/EmailMaster.scala)，`EmailMaster`再根据邮件发送者（连接SMTP的邮箱用户名）来决定将这个发送请求交给哪一个具体的[`EmailGroupActor`](https://github.com/yangjing/scala-applications/blob/master/email-server/src/main/scala/me/yangjing/emailserver/service/actors/EmailGroupActor.scala)。

这里，程序对邮件发送者（简称：`sender`）做了一个分组。因为对于使用相同`smtp`邮件发送服务提供的`sender`，程序中最后对此类的`sender`做串行发送。而对于不同`smtp`邮件发送服务提供的`sender`，我们可以并发的发送邮件。这个可以通过在定义配置文件的时候指定特定`sender`属于的邮件发送组。

```HOCON
  emails {
    email1 {
      userName = "email1@email.com"
      password = ""
      smtp = ""
      smtpPort = 0
      ssl = true

      # 同一个组内的邮件会串行发送，此key可忽略
      group = "1"
    }
  }
```


## 连接`ActiveMQ`

连接`ActiveMQ`使用了`JMS`协议，这是一个`Java EE`标准实现的消息队列。代码在：[MQConsumerService](https://github.com/yangjing/scala-applications/blob/master/email-server/src/main/scala/me/yangjing/emailserver/service/MQConsumerService.scala)。

在`JMS`里，邮件使用`MapMessage`消息发送，程序使用`case match`来匹配期望的消息格式。

```scala
val listener = new MessageListener {
  override def onMessage(message: Message): Unit = message match {
    case msg: MapMessage => {
      val subject = msg.getString("subject")
      val sender = msg.getString("sender")
      val content = msg.getString("content")
      val to = msg.getString("to").split(";")
      val mimeType = Option(msg.getString("mimeType")).map(MimeType.withName).getOrElse(MimeType.Text)
      val sendEmail = SendEmail(sender, subject, to, content, None, mimeType)
      emailService.sendEmail(sendEmail).onComplete(result => logger.debug(result.toString))
    }

```


## 总结

本文简单的演示了`Akka Http`构建一个`REST`服务，并支持连接`JMS Server`来获取发送邮件消息。

演示了文件邮件和`HTML`格式邮件的发送。


## 接下来

接下来可以添加对邮件附件的支持，这个功能可以留给读者去实现。
