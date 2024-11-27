title: Scala实战：使用Actor来控制集成API的并发请求
date: 2016-06-29 13:52:51
categories: scala
tags: scala, akka, actor
---

*本文源码在：[https://github.com/yangjing/scala-applications/tree/master/combine-request](https://github.com/yangjing/scala-applications/tree/master/combine-request)*

## 背景

最近在一些大数据相关工作，除了自身的数据外，我们也会接入很多外部的第3方数据。这些第3方数据提供商都提供了基于HTTP的服务。当然，这些数据是收费的。而且重复调用是需要重复收费的。这就需要我们在调用数据后把它存储下来，这样在一定时间内（因为在超过一定时间后我们会需要再次向第3方数据提供商请求，主要是用于确认数据是否更新。这里不得不吐槽下，对方为什么不提供一个数据是否更新的接口呢？）再次使用这份数据就不需要向第3方数据提供商重复付费了。

这里，若同时有A、B、C三个用户请求同一份数据：Company。若假设我们在调用第3方数据提供商是需要持续1秒钟的时间。虽然我们在成功获取到 Company 后都会把数据缓存到DB里。但因为A、B、C三个用户请求是同时发来的，他们都会先读DB来获得是否有要请求的数据。而这个时间DB里是没有他们需要的数据的，这时我们就会向第3方数据提供商发送3次相同 Company 的数据请求，也就是说 Company 这份数据我们向第3方数据提供商付了3次费。想想，心情就不好了……

![多次请求多次付费](/img/n2n-pay-request.jpg)

这篇文章要介绍的就是怎样基于Akka的Actor模型来解决这一问题。若你还不了解Akka，你可以看看Akka官网：<a target="_blank" href="http://akka.io">Akka</a>。本文会基于Akka for Scala讲解，若你对Scala还不甚了解，推荐学习 <a target="_blank" href="https://www.coursera.org/learn/progfun1">《Scala函数式程序设计原理》</a> 和 <a target="_blank" href="http://twitter.github.io/scala_school/zh_cn/index.html">《Twitter的Scala入门教学》</a>。

## 目的

要解决之前说到的那个问题，我们需要把同时发来的并发请求合并到一起，只向第3方数据提供商请求一次付费API。同时在收到结果后先把数据缓存到本地数据库以后再向并发请求者们返回已缓存过的结果。这样下一次再查询相同数据时就可以从本地数据库中直接获取到。

![多次请求一次付费](/img/n21-pay-request.jpg)

## 代码

**ForwardCompanyActor**

首先我们来看看Actor的定义，我们需要把读本地数据库、向第3方数据提供商发请求、控制多个客户端并发调用这些动作都合并到一个actor中。这里定义了一个`ForwardCompanyActor`接口来实现这些功能，待会具体的实现类将实现从本地数据库读和从第3方数据提供商读两个函数。

```scala
  type ReadFromDB = (String) => Future[Option[JsValue]]
  type ReadFromInfra = (String) => Future[Option[JsValue]]

trait ForwardCompanyActor extends Actor {
  val companyListeners = mutable.Map.empty[String, Set[ActorRef]]

  override def receive = {
    case QueryCompany(companyName, doSender) =>
      val listener = if (doSender == Actor.noSender) sender() else doSender
      registerListener(companyName, listener)

    case ReceiveQueryCompanyResult(companyName, maybeJsValue) =>
      dispatchListeners(companyName, maybeJsValue)
  }

  def performReadTask(companyName: String): Unit = {
    import context.dispatcher
    readFromDB(companyName)
      .flatMap(maybe => if (maybe.isEmpty) readFromInfra(companyName) else Future.successful(maybe))
      .foreach(maybe => self ! ReceiveQueryCompanyResult(companyName, maybe))
  }

  def registerListener(companyName: String, listener: ActorRef): Unit =
    companyListeners.get(companyName) match {
      case Some(actors) =>
        companyListeners.put(companyName, actors + listener)
      case None =>
        companyListeners.put(companyName, Set(listener))
        performReadTask(companyName)
    }

  def dispatchListeners(companyName: String, maybeJsValue: Option[JsValue]): Unit = {
    val maybeListener = companyListeners.get(companyName)
    maybeListener.foreach { listeners =>
      for (listener <- listeners) {
        listener ! maybeJsValue
      }
      companyListeners -= companyName
    }
  }

  val readFromInfra: ReadFromInfra

  val readFromDB: ReadFromDB
}
```

希望你没有被这一大段代码给吓到，接下来我会详解说明每段代码的意思。

`type`为函数签名定义了两个别名。而Trait中有两个函数值：`readFromInfra`和`readFromDB`是需要实现类实现的。它们分别是从第3方数据提供获取数据和从本地数据库中获取数据。

在`ForwardCompanyActor`的开头，定义了一个可变`Map`集合：`companyListeners`，它将保存每一个查询关键词（假定我们要查询一家公司的工商信息，而这家公司的全名就会做为""Key""）在同一时间的所有监听者（同时想查询这家公司工商信息的监听者（就是ActorRef）集合则作为""Value""）。

`receive`是一个`PartialFunction`，是每个Actor都要实现的函数，它将处理收到的每一条消息。在这里，我们只处理了两类消息：`QueryCompany`和`ReceiveQueryCompanyResult`。它们的作用分别是注册请求监听者和将收到的某个公司的工商信息分发给关注此数据的每一个监听者。

现来看`registerListener`函数。在每次收到对某个公司的调用请求时它都会判断actor内现在是否已存在正在进行中的对此公司的数据查询任务。若存在，则它会简单的把监听者（就是ActorRef）加到对应`companyName`的`ActorRef`集合里。否则，则会创建一个新的`Set(listener)`，并把它作为""Value""和`companyName`一起存入`companyListeners`中，并同时调用`performReadTask`函数执行实际的读数据请求。

继续看`performReadTask`函数。它会按着先读本地数据库，若数据库中未找到则再向第3方数据提供商请求，最后再成功获取到数据后向当前actor实例，也就是`self`发送`ReceiveQueryCompanyResult`消息。

这里需要注意的是：`.foreach(maybe => self ! ReceiveQueryCompanyResult(companyName, maybe))`这段代码。这里之所以没有直接调用`dispatchListeners`函数来向所有相关监听者发送结果，而是发送一个收到结果消息给`self`是因为在此直接调用`dispatchListeners`会有并发争用问题。因为`.foreach`是在`Future`的一个事件回调函数，它执行时很有可能是在另一个线程，而这时很有可能`ForwardCompanyActor`同时会收到其它对此公司查询工商信息的请求。这里收到对公司的工商数据请求和执行`dispatchListeners`向监听者发送结果消息很有可能是同时发生的。很有可能造成向第3方数据提供商发送两次相同公司的工商数据查询请求，这样就会对同一家公司付两次费了。

而向当前actor发送一个`ReceiveQueryCompanyResult`消息，则可以解决这个问题。因为Actor在内部对收到的每一个消息是串行处理的（多个Actor相互之间是并行运行的）。在`receive`函数里收到`ReceiveQueryCompanyResult`消息时调用`dispatcherListeners`函数向监听者发送查询结果，若此时有新的相同公司调用请求进来，它会被压入Actor的**MailQueue**中，在`dispatchListeners`函数执行完成后`receive`才会处理新的`QueryCompany`请求。现在我们再看`dispatchListeners`函数的内部实现，它在向关注某一`companyName`的监听方都发送结果后会把此`companyName`比监控列表（companyListeners）中移除。这样新的`QueryCompany`请求将会生成一个新的`Set(listener)`加入监听队列，同时执行`performReadTask`函数，而在`performReadTask`函数会从本地数据库中找到这家公司的工商信息，这样就不会向第3方数据提供商重复调用并重复付费了。

`ForwardCompanyActor`封装了多个并发请求的合并，读本地数据库和读第3方数据提供商的请求，监听都的注册、消息发送通知等功能。

**CorpDetailActor**

完成`ForwardCompanyActor`功能接口的定义后，就需要一个具体的Actor来实现从数据库读和从第3方接入商读两个操作。

```scala
class CorpDetailActor(infraResource: InfraResource,
                      infraMongodbRepo: InfraMongodbRepo) extends ForwardCompanyActor {
  import context.dispatcher

  override val readFromDB: ReadFromDB = (companyName) => {
    infraMongodbRepo.findCorpDetail(companyName)
  }

  override val readFromInfra: ReadFromInfra = (companyName) => {
    infraResource.corpDetail(companyName)
      .flatMap {
        case Some(json) =>
          infraMongodbRepo
            .saveCorpDetail(companyName, json.asInstanceOf[JsObject])
            .map(_ => Some(json))

        case None =>
          Future.successful(None)
      }
  }

}
```

在这里我们实现了`readFromDB`和`readFromInfra`两个函数值，代码很直观。需要注意的地方是通过`infraResource`向第3方数据提供商付费请求数据获得结果后缓存数据到本地数据库这个地方。一定要在本地缓存完成以后再向调用方返回数据，若你直接向调用方返回数据而把缓存操作放到另一个线程中，那这里又会引起一个并发问题。因为在你缓存成功之前很有可能会有另一个请求要求查询相同数据，而这时它在本地数据库中并不能找到，而系统会再次向第3方数据提供收请求你刚刚才付费了的那家公司的工商信息。所以，千万记住！在缓存成功后再向调用方返回数据。

## 测试

**[CombineExample]() 合并请示测试效果：**

```
[2016-07-02T13:30:29.514] 本地数据库未找到公司：科技公司
[2016-07-02T13:30:30.526] 收到查询：科技公司 工商信息付费请求
[2016-07-02T13:30:30.529] 保存公司: Company(科技公司) 成功
Some(Company(科技公司)), Some(Company(科技公司)), Some(Company(科技公司))
```

**[RepetitionExample]() 未合并请示时测试效果：**

```
[2016-07-02T17:30:45.182] 本地数据库未找到公司：科技公司
[2016-07-02T17:30:45.182] 本地数据库未找到公司：科技公司
[2016-07-02T17:30:45.181] 本地数据库未找到公司：科技公司
[2016-07-02T17:30:46.204] 收到查询：科技公司 工商信息付费请求
[2016-07-02T17:30:46.204] 收到查询：科技公司 工商信息付费请求
[2016-07-02T17:30:46.206] 收到查询：科技公司 工商信息付费请求
[2016-07-02T17:30:46.216] 保存公司: Company(科技公司) 成功
[2016-07-02T17:30:46.216] 保存公司: Company(科技公司) 成功
[2016-07-02T17:30:46.216] 保存公司: Company(科技公司) 成功
Some(Company(科技公司)), Some(Company(科技公司)), Some(Company(科技公司))
```
