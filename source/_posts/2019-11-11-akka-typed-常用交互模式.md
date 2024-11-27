title: Akka Typed 常用交互模式
date: 2019-11-11 22:29:25
categories:
  - scala
  - akka
tags:
  - akka
  - akka-typed
  - scala
  - design-pattern
---

本文将探讨Akka Typed下actor的常用交互模式，相对经典的untyped actor，typed actor在交互与使用方式上有着显著的区别。对Akka Typed还不太了解的读者可以先参阅我的上一篇文章：[《Akka Typed新特性一览》](https://www.yangjing.me/2019/11/06/akka-typed%E6%96%B0%E7%89%B9%E6%80%A7%E4%B8%80%E8%A7%88/)。

*本文大量参译了Akka官方文档《Interaction Patterns》一文（原文链接：[https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html](https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html)）。在巨人的基础之上加入了作者自身的理解和解读，希望能给读者带来 1+1>=2 的感受。*

- 发送并忘记 (Fire and Forget)
- 请求/响应 (Request-Response)
- 适配响应、消息适配器 (Adapted Response)
- 两个actor之间的请求/响应 (Request-Response with ask between two actors)
- 来自actor外部的请求/响应 (Request-Response with ask from outside an Actor)
- `Future`结果发送给（actor）自己 (Send Future result to self)
- 每会话一个子actor (Per session child Actor)
- 通用响应聚合器 (General purpose response aggregator)
- 尾部延迟截断 (Latency tail chopping)
- 调度（定时）消息给（actor）自己 (Scheduling messages to self)
- 分片actor的回复 (Responding to a sharded actor)

## 发送并忘记 (Fire and Forget)

使用`tell`或`!`函数向actor发送消息，并且在消息内没有可回复的actor引用字段（如：`replyTo: ActoRef[T]`）既是典型的发送并忘记模式。这个模式非常简单，和经典的untyped actor没有区别，在此就不提供代码示例了。

#### 适用范围

1. 消息是否被处理不重要
2. 不需要确保消息被成功交付或处理
3. 吞吐量高（若发送确认回复至少需要创建两位的消息数量）

#### 问题

1. 若消息流入速度高于actor的处理能力，则很可能会将接收者的消息邮箱填满，并有可能导致JVM崩溃
2. 如果消息丢失将无法知道

## 请求/响应 (Request-Response)

actor之间的许多交互需要从接收方返回一个或多个响应消息，这可以是查询的结果、请求或处理的确认ACK或请求订阅的事件等……在Akka Typed，响应的接收者（发起请求的actor，请求方）必须被编码为消息本身的一个字段，这样接收者才能使用此字段来发送（`tell`或`!`）响应给请求方。

![request-response](/img/typed/images/request-response.png)

```scala
case class Request(query: String, replyTo: ActorRef[Response])
case class Response(result: String)

// 向接收者发送消息
receiver ! Request("give me cookies", context.self)

// 接收请求并返回响应数据
Behaviors.receiveMessage[Request] {
  case Request(query, replyTo) =>
    replyTo ! Response(s"Here are the cookies for [$query]!")
    Behaviors.same
}
```

#### 适用范围

1. 订阅actor并希望收到被订阅actor响应的多个消息

#### 问题

1. 响应消息也许不匹配请求actor的类型限制，（参阅：**适配响应** 获取解决方案）
2. 很难检测到请求是否送达或已被处理
3. 当请求actor发起多次请求时，不能保存请求上下文信息（可在消息内加上请求id或引入新的独立接收者可解决此问题）

## 适配响应、消息适配器 (Adapted Response)

通常情况下，发送actor的消息类型与接收actor的响应消息类型不匹配（不然就会退化成大部分actor都继承同一个trait，这样就失去了 **Typed** 的意义！）。这种情况下，我们提供一个正确类型的`ActorRef[T]`，并将接收actor返回的响应消息`T`包装成发送actor可以处理的类型。

![adapted-response](/img/typed/images/adapted-response.png)

```scala
  val backend: ActorRef[Backend.Command] = _

  final private case class WrappedBackendResponse(response: Backend.Response) extends Command

  Behaviors.setup[Command] { context =>
    val backendAdapter: ActorRef[Backend.Response] =
      context.messageAdapter(resp => WrappedBackendResponse(resp))

    backend ! Backend.Register(backendAdapter)

    Behaviors.receiveMessage[Command] {
      case WrappendBackendResponse(resp) =>
        resp match {
          case Backend.Registered(...) =>
            // process response message
          case _ =>
        }
        Behaviors.same
    }
  }
```

应该为不同的消息类型注册独立的消息适配器，同一个消息类型多次注册的消息适配器只有最后一个生效。

如果响应的消息类与给定消息适配器匹配或是其消息适配器消息类型的子类型，则使用它。若有多个消息适配器符合条件，则将选用最后注册的那个。

消息适配器（context.messageAdapter返回的`ActorRef[T]`）的生命周期同`context`所在actor。建议在`Behaviors.step`或`AbstractBehavior`构造函数中注册适配器，但也可以在稍后注册它们。

注册适配器时提供的消息映射函数（`resp => WrappedBackendResponse(resp)`）在actor中运行，可安全的访问其（actor）内部状态。 **但注意不能抛出异常，否则actor将被停止！**

#### 适用范围

1. 在不同的actor消息协议间进行转换
2. 订阅响应消息的actor，并将响应转换成发送actor可接收的类型

#### 问题

1. 难以检测消息是否送达或已被处理
2. 每个响应消息只能进行一次自适应，如果注册了新的适配器则旧的将被替换。如果不同的目标actor使用相同的响应类型，则它们自动选择哪个适配器更合适。这需要在消息中编码某种相关性来解决
3. 除非协议已经包含提供上下文的方法，例如在响应中返回发送的请求ID。否则交互就不能绑定到某个上下文中。

## 两个actor之间的请求/响应 (Request-Response with ask between two actors)

当请求与响应之间存在1:1映射时，可以通过调用`ActorContext`上的`ask`函数来与另一个actor进行交互。

1. 构造一个传出消息，它使用`context.ask[Response]`提供的`ActorRef[Response]`作为接收响应的actor放入消息中
2. 将成功/失败（`Try[V]`）转换为发送者actor可接收的消息类型

![request-response-with-ask-between-two-actors](/img/typed/images/ask-from-actor.png)

```scala
  val backend: ActorRef[Hal.Command] = _

  trait Command
  final private case class WrappedQueryResponse(
    reqId: String,
    response: Try[Hal.Response],
    replyTo: ActorRef[Hal.Response]) extends Command

  Behaviors.setup[Command] { context =>
    implicit val timeout: Timeout = 3.seconds

    Behaviors.receiveMessage[Command] {
      case Query(reqId, name, replyTo) =>
        context.ask(backend, ref => Backend.Query(name, ref)) { value =>
          WrappedBackendResponse(reqId, value, replyTo)
        }
        Behaviors.same
      case WrappendQueryResponse(reqId, value, replyTo) =>
        replyTo ! value
          .map(resp => Queried(200, reqId, Some(resp))
          .getOrElse(Queried(500, reqId))
        Behaviors.same
    }
  }
```

`context.ask`的响应映射函数在接收actor中运行，可以安全的访问actor内部状态， **但抛出异常的话actor将会被停止** 。

```scala
def ask[Req, Res](target: RecipientRef[Req], createRequest: ActorRef[Res] => Req)(
      mapResponse: Try[Res] => T)
```

上面是简化的`ask`函数签名（省略了隐式参数）：

- `target`：接收actor引用
- `createRequest`：创建请求消息函数，参数是`ask`创建的临时actor，此临时actor用于适配接收actor的消息类型
- `mapResponse`：将获取的响应消息类型`Res`映射成请求actor可以接收的消息类型

#### 适用范围

1. 单个查询响应的转换
2. 发送actor需要在继续之前知道消息已被处理（通过`context.ask(..., ...)(mapResponse)`的`mapResponse`函数）
3. 如果请求超时，允许actor重新发送消息（通过`mapResponse`函数处理`Failure[TimeoutException]`）
4. 跟踪未完成的请求
5. 保存上下文。发送者actor接收的请求有上下文信息（`context.ask`将生成一个临时actor，这个临时actor即可作为一个确定上下文的载体），如：请求ID `reqId`，而后端协议不支持这个参数时

#### 问题

1. 一个`ask`只能有一个响应（因为`ask`会创建一个临时actor，这个actor在收到响应后就会结束自己）
2. 当请求超时时，接收actor（发回响应的那个）并不知道且仍可能将请求处理并完成，甚至若接收actor很忙的话会在请求超时发生以后再处理它
3. 为超时情况找到一个好的（包装）值，特别是在`ask`函数调用后还会触发链式调用时（一个异步调用完成后进行另一个异步调用）。这时候希望来快速响应超时情况并回复请求者，但同时需要避免误报。

## 来自actor外部的请求/响应 (Request-Response with ask from outside an Actor)

通过`ask`的另一个版本（由`AskPattern._`隐式导入）可以在actor外部（`actorRef.ask`）实现请求/响应式交互。`ask`调用将返回`Future[T]`，若在指定超时内没有响应，则以`Failure[TimeoutException]`作为结果。

![request-response-with-ask-from-outside-an-actor](/img/typed/images/ask-from-outside.png)

```scala
import akka.actor.typed.scaladsl.AskPattern._
implicit val typedSystem: ActorSystem[_] = system
implicit val timeout: Timeout = 3.seconds

val result: Future[CookieFabric.Reply] =
  cookieFabric.ask(ref => CookieFabric.GiveMeCookies(3, ref))
```

*注：`import AskPattern._` 导入的`ask`函数本来需要有一个`Scheduler`的隐式参数，但object `AskPattern`还同时提供了一个`schedulerFromActorSystem`隐式函数从`ActorSystem[_]`获得`Scheduler`，这里建议直接使用`implicit ActorSystem[_]`（在使用Akka Stream时，也提供了从`ActorSystem[_]`获得`Materializer`的隐式转换函数，直接使用`implicit ActorSystem[_]`可以减少样版代码，使代码更清晰）。*

#### 适用范围

1. 从actor系统外部访问时，如Akka HTTP请求访问actor获取响应值

#### 问题

1. 在返回的`Future`回调内很可能意外的捕获了外部状态，因为这些回调将在与`ask`不同的线程上执行
2. 一个`ask`只能有一个响应（`ask`将生成临时actor）
3. 当请求超时时，接收actor并不知道且仍将继续处理请求直至完成，甚至可能会在超时发生后才开始处理它

## `Future`结果发送给（actor）自己 (Send Future result to self)

当在actor内部执行异步操作（返回一个`Future`时）需要小心处理，因为actor与那个异步操作不在同一个线程。`ActorContext`提供了`pipeToSelf`方法来将`Future`的结果安全传给自己。

![send-future-result-to-self](/img/typed/images/pipe-to-self.png)

```scala
case Update(value, replyTo) =>
  if (operationsInProgress == MaxOperationsInProgress) {
    // ....
    Behaviors.same
  } else {
    val futureResult = dataAccess.update(value)
    context.pipeToSelf(futureResult) {
      case Success(_) => WrappedUpdateResult(UpdateSuccess(value.id), replyTo)
      case Failure(e) => WrappedUpdateResult(UpdateFailure(value.id, e.getMessage), replyTo)
    }
    next(dataAccess, operationsInProgress + 1)
  }
case WrappedUpdateResult(result, replyTo) =>
  replyTo ! result
  next(dataAccess, operationsInProgress - 1)
```

在`Future`的`onComplete`回调函数里处理异步结果看起来很诱人，但这样会引发很多潜在的危险，因为从外部线程访问actor内部状态不是线程安全的。例如：无法从类似回调中线程安全的访问示例的`operationsInProgress`计数器，所以，最好将响应映射到消息，并使用actor的消息接收机制来线程安全的执行进一步处理。

#### 适用范围

1. 调用返回`Future`的外部服务时
2. 当`Future`完成，actor需要继续处理时
3. 保留原始请求的上下文，并在`Future`完成时使用它。如：`replyTo: ActorRef[_]`

#### 问题

1. 为`Future`结果添加过多的包装消息

## 每会话一个子actor (Per session child Actor)

在某些情况下，对请求的完整响应只能在从其他actor收集多个响应后再创建并发送回请求方。对于这种交互，最好将工作委托给每 **session** 子actor，还可以包含任意逻辑来实现重试、超时失败、尾部截断、进度检查等。

请注意，这基本上就是`ask`的实现方式，如果只需要一个带超时的响应，那么使用`ask`更好。

子actor是用它需要做工作的上下文创建的，包括它可以响应的`ActorRef[_]`。当完整的结果出现时，子actor会用结果进行响应并停止自身。

由于session actor的协议不是公共API，而是父actor的实现细节，因此使用显式协议并调整session actor与之交互的actor的消息可能并不总是有意义，可以让session actor接收任何消息（`Any`）。

![per-session-child-actor](/img/typed/images/per-session-child.png)

```scala
object Home {
  sealed trait Command
  case class LeaveHome(who: String, replyTo: ActorRef[ReadyToLeaveHome]) extends Command
  case class ReadyToLeaveHome(who: String, keys: Keys, wallet: Wallet)

  def apply(): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      Behaviors.receiveMessage[Command] {
        case LeaveHome(who, replyTo) =>
          context.spawn(prepareToLeaveHome(who, replyTo, keyCabinet, drawer), s"leaving-$who")
          Behaviors.same
      }
    }
  }

  // Per session actor behavior
  def prepareToLeaveHome(
      whoIsLeaving: String,
      replyTo: ActorRef[ReadyToLeaveHome],
      keyCabinet: ActorRef[KeyCabinet.GetKeys],
      drawer: ActorRef[Drawer.GetWallet]): Behavior[NotUsed] = {
    Behaviors.setup[AnyRef] { context =>
      var wallet: Option[Wallet] = None
      var keys: Option[Keys] = None
      keyCabinet ! KeyCabinet.GetKeys(whoIsLeaving, context.self.narrow[Keys])
      drawer ! Drawer.GetWallet(whoIsLeaving, context.self.narrow[Wallet])

      def nextBehavior(): Behavior[AnyRef] = (keys, wallet) match {
        case (Some(w), Some(k)) =>
          replyTo ! ReadyToLeaveHome(whoIsLeaving, w, k)
          Behaviors.stopped
        case _ => Behaviors.same
      }

      Behaviors.receiveMessage {
        case w: Wallet =>
          wallet = Some(w)
          nextBehavior()
        case k: Keys =>
          keys = Some(k)
          nextBehavior()
        case _ =>
          Behaviors.unhandled
      }
    }
    .narrow[NotUsed] // 标记此actor行为不需要接受任务请求消息
  }
}
```

`prepareToLeaveHome`不需要关心actor协议（消息类型），因为除了对查询的响应之外，没有任何地方会向它发送消息。但在交互时会将消息限制为有限的类型。

#### 适用范围

1. 在结果生成前，一个请求会导致与其它actor（或与多个外部服务进行交互）
2. 需要处理请求确认并保证消息至少一次传递时

#### 问题

1. 子actor生命周期必须要小心管理才能避免造成资源泄漏，很容易出现子actor未能停止的情况
2. 增加了复杂性，每个子actor都可与它的父actor并发执行

## 通用响应聚合器 (General purpose response aggregator)

类似上一个 **每会话一个子actor** 模式，这种模式有很多变体，这里抽像出了一种通用的可复用的聚合模式。

![general-purpose-response-aggregator](/img/typed/images/aggregator.png)

```scala
object Aggregator {
  sealed trait Command
  private case object ReceiveTimeout extends Command
  private case class WrappedReply[R](reply: R) extends Command

  def apply[Reply: ClassTag, Aggregate](
      sendRequests: ActorRef[Reply] => Unit,
      expectedReplies: Int,
      replyTo: ActorRef[Aggregate],
      aggregateReplies: immutable.IndexedSeq[Reply] => Aggregate,
      timeout: FiniteDuration): Behavior[Command] = {
    Behaviors.setup { context =>
      context.setReceiveTimeout(timeout, ReceiveTimeout)
      val replyAdapter = context.messageAdapter[Reply](WrappedReply(_))
      sendRequests(replyAdapter)

      def collecting(replies: immutable.IndexedSeq[Reply]): Behavior[Command] = {
        Behaviors.receiveMessage {
          case WrappedReply(reply: Reply) =>
            val newReplies = replies :+ reply
            if (newReplies.size == expectedReplies) {
              val result = aggregateReplies(newReplies)
              replyTo ! result
              Behaviors.stopped
            } else
              collecting(newReplies)

          case ReceiveTimeout =>
            val aggregate = aggregateReplies(replies)
            replyTo ! aggregate
            Behaviors.stopped
        }
      }
      collecting(Vector.empty)
    }
  }
}
```

- `sendRequest: ActorRef[Reply] => Unit`：处理发送请求，参数`ActorRef[Reply]`可作为请求消息的`replyTo`字段发送给接收方用于返回响应结果
- `expectedReplies: Int`：预计期望收到的回复总数
- `replyTo: ActorRef[Aggregate]`：当响应聚合完成或超时达到时，将聚合后的结果回复给指定actor
- `aggregateRepliese: Seq[Reply] => Aggregate`：当响应聚合完成或超时达到时，映射集合为需要的响应消息类型
- `timeout: FiniteDuration`：超时时间

#### 适用范围

1. 通过相同的方式从多个地方的回复中聚合
2. 单个请求需要与多个actor进行多次交互，再生成一个结果返回
3. 需要处理（ACK）确认和至少一次传递的消息时

#### 问题

1. 且有泛型类型的消息协议很困难，因为泛型类型在运行时被删除了
2. 子节点的生命周期必需小心管理
3. 增加了复杂性，因为每一个这样的子actor都可能与其它子actor或父级同时执行

## 尾部延迟截断 (Latency tail chopping)

这个模式类似上一个 **通用响应聚合器** 模式，但它不需要对多个数据来源进行聚合，只需要取第一个收到的数据即可。

该算法的目标是在多个actor可以执行相同工作的情况下减少尾部延迟。这种情况下，会同时向多个后端actor发现请求（后端请求应保证每次请求得到的响应都一样），取最快的响应做为结果返回，其它忽略掉。这在高并发情况下可显著增强响应速度和吞吐量。

像Cassandra这样的NoSQL数据库就运行了类似技术同时对多个副本进行查询，使用最快返回的值做为响应结果。因为通常情况下所有副本节点不会同时负载很高。

![latency-tail-chopping](/img/typed/images/tail-chopping.png)

```scala
  sealed trait Command
  private case object RequestTimeout extends Command
  private case object FinalTimeout extends Command
  private case class WrappedReply[R](reply: R) extends Command

  def apply[Reply: ClassTag](
      sendRequest: (Int, ActorRef[Reply]) => Boolean,
      nextRequestAfter: FiniteDuration,
      replyTo: ActorRef[Reply],
      finalTimeout: FiniteDuration,
      timeoutReply: Reply): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        val replyAdapter = context.messageAdapter[Reply](WrappedReply(_))

        def waiting(requestCount: Int): Behavior[Command] = {
          Behaviors.receiveMessage {
            case WrappedReply(reply: Reply) =>
              replyTo ! reply
              Behaviors.stopped

            case RequestTimeout =>
              sendNextRequest(requestCount + 1)

            case FinalTimeout =>
              replyTo ! timeoutReply
              Behaviors.stopped
          }
        }

        def sendNextRequest(requestCount: Int): Behavior[Command] = {
          if (sendRequest(requestCount, replyAdapter)) {
            timers.startSingleTimer(RequestTimeout, RequestTimeout, nextRequestAfter)
          } else {
            timers.startSingleTimer(FinalTimeout, FinalTimeout, finalTimeout)
          }
          waiting(requestCount)
        }

        sendNextRequest(1)
      }
    }
  }
```

示例首先以参数`1`调用`sendNextRequest`函数开始整个行为，在函数内部使用`sendRequest`执行实际的请求发送动作。`sendRequest`返回true则执行一个计时器调度在`nextRequestAfter`超时后进行另一个发送请求，返回false则执行一个 **FinalTimeout** 计时器调度，若actor收到 **FinalTimeout** 消息则代表整个请求超时结束（失败）。

***注意：***

*这个示例需要注意的地方是`sendRequest`函数需要有一个返回`false`的判断路径，不然整个actor可能会永不停止！还有一种优化就是将`timers.startSingleTimer(FinalTimeout, FinalTimeout, finalTimeout)`提到`Behaviors.setup`代码块开始执行，设置`finalTimeout`为一个比`nextRequestAfter`大的值，这样当`finalTimeout`超时到达时，无论`sendRequest`是否反回**false**，整个任务都将超时结束。*

#### 适用范围

1. 降低系统整体延迟百分比，使系统延迟变化更平稳
2. **工作（任务）** 可以相同的结果多次执行时，例如：请求检索信息

#### 问题

1. 由于相同的任务发送了多次，因此系统整体负载有所增加
2. 任务必须的幂等的，多次执行时能获得相同的结果
3. 子actor有生命周期，必须小心对其进行管理才会不造成资源泄漏。
4. 定义泛型类型的消息协议很困难，因为泛型类型在运行时已被擦除

## 调度（定时）消息给（actor）自己 (Scheduling messages to self)

使用`TimerScheduler`可以定时将一个特定消息发送给actor自身，支持单次或多次定时调度。

![scheduling-messages-to-self](/img/typed/images/timer.png)

```scala
object Buncher {

  sealed trait Command
  final case class ExcitingMessage(message: String) extends Command
  final case class Batch(messages: Vector[Command])
  private case object Timeout extends Command
  private case object TimerKey

  def apply(target: ActorRef[Batch], after: FiniteDuration, maxSize: Int): Behavior[Command] = {
    Behaviors.withTimers(timers => new Buncher(timers, target, after, maxSize).idle())
  }
}

class Buncher(
    timers: TimerScheduler[Buncher.Command],
    target: ActorRef[Buncher.Batch],
    after: FiniteDuration,
    maxSize: Int) {
  import Buncher._

  private def idle(): Behavior[Command] = {
    Behaviors.receiveMessage[Command] { message =>
      timers.startSingleTimer(TimerKey, Timeout, after)
      active(Vector(message))
    }
  }

  def active(buffer: Vector[Command]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case Timeout =>
        target ! Batch(buffer)
        idle()
      case m =>
        val newBuffer = buffer :+ m
        if (newBuffer.size == maxSize) {
          timers.cancel(TimerKey)
          target ! Batch(newBuffer)
          idle()
        } else
          active(newBuffer)
    }
  }
}
```

一开始`idle()`函数将启动一个单次定时计时器，然后返回一个新的行为`active(buffer: Vector[Command])`。`active`函数默认将缓冲每次收到的消息，并将消息附加到`buffer`然后做为`active`函数参数再次返回一个新的行为（这样在整个actor没有可变数据的情况下也可以保存内部状态）。当`Timeout`消息产生时，actor对`buffer`数据进行处理，并返回初始的`idle()`行为，这时将再次进行定时任务调度。

- 当actor退出时，`TimerScheduler`将会保证取消所有已注册的定时调度。
- 每个计时器都需要一个key，若启动了具有相同key的新计时器，则上一个计时器会被取消，并保证不会收到来自上一个计时器的消息，即使那个消息已经在邮箱里排队。
- `Behaviors.withTimers`也可以在`Behaviors.supervise`中使，当actor重启时，它将自动取消已启动的计时器，以保证新的actor实例不会收到前一个实例的计时消息。

***Scheduler选择***

定期执行消息可以有两个不同的选择：

- 固定延迟（**fixed-delay**）：发送后续消息之章的延迟始终（不小于）为给定的值，使用`startTimerWithFixedDelay`函数
- 固定速率（**fixed-rate**）：一段时间内执行的频率满足给定的间隔，使用`startTimerAtFixedRate`函数

如果不确定使用哪一个，建议选择`startTimerWithFixedDelay`。因为 **固定速率** 在长时间的垃圾收集暂停后可能会导致计划消息的突发，这在最坏的情况下可能会导致系统上出现预期外的负载。通常首选具有 **固定延迟** 的调度计划。

当使用固定延迟时，如果由于某种原因，调度延迟超过指定的时间，则它不会补偿消息之间的延迟。发送后续消息之间的延迟总是（至少）给定的延迟。从长远来看，消息的频率通常会略低于指定延迟的倒数。

固定延迟执行适用于需要“平滑度”的重复性活动。换句话说，它适用于短期内比长期内保持频率准确更为重要的活动。

使用固定速率时，如果先前的消息延迟太长，它将补偿后续任务的延迟。在这种情况下，实际的发送间隔将不同于传递给 **固定速率** 方法的间隔。

如果任务延迟超过间隔时间，则在前一个任务之后立即发送后续消息。这还会导致在长时间的垃圾收集暂停或JVM暂停时的其他原因之后，当进程再次唤醒时，将执行所有“错过”的任务。例如，间隔`1`秒的 **固定速率** 和暂停`30`秒的进程将导致连续快速发送30条消息以赶上之前错过的调度。从长远来看，执行频率正好是指定间隔的倒数。

固定速率执行适用于对绝对时间敏感或执行固定数量执行的总时间很重要的重复活动，例如每秒计时一次并持续10秒的倒计时计时器。

## 分片actor的回复 (Responding to a sharded actor)

当在Akka集群里使用分片（shard）actor时，你需要考虑到actor可能会被移动（到其它节点）或被钝化（Passivated）。这时候若还将分片actor自身的引用（`context.self`）包含到消息里转递，若分片actor被移动或钝化，则回复被会被发送到列信actor……

正确的做法是，在使用分片actor时在消息里传递`entityId`，并使用`Sharding`来发送回复。

![responding-to-a-shareded-actor](/img/typed/images/sharded-response.png)

```scala
object CounterConsumer {
  sealed trait Command
  final case class NewCount(count: Long) extends Command
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("example-sharded-response")
}

object Counter {
  trait Command
  case object Increment extends Command
  final case class GetValue(replyToEntityId: String) extends Command
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("example-sharded-counter")

  private def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      counter(ClusterSharding(context.system), 0)
    }

  private def counter(sharding: ClusterSharding, value: Long): Behavior[Command] =
    Behaviors.receiveMessage {
      case Increment =>
        counter(sharding, value + 1)
      case GetValue(replyToEntityId) =>
        val replyToEntityRef = sharding.entityRefFor(CounterConsumer.TypeKey, replyToEntityId)
        replyToEntityRef ! CounterConsumer.NewCount(value)
        Behaviors.same
    }
}
```

#### 问题

1. 这样做缺点是不能使用消息适配器，因为响应必须在被响应的actor的协议中。此外，如果不能确定`EntityTypeKey[T]`的具体类型，则可以将它包含在消息中一起发送。

```scala
final case class CommandSharding(...., replyEntityId: String, replyEntityType: EntityTypeKey[Reply]) extends Command
```

## 小结

本文为对官方文档 [https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html](https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html) 的 **学习**，*不全只是翻译*，一切以官网文档为准。
