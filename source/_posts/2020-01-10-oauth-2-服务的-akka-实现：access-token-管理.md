title: OAuth 2 服务的 Akka 实现：access_token 管理
tags:
  - oauth-2
  - akka
  - scala
  - oauth2
  - access_token
  - akka-cluster
  - akka-cluster-sharding
  - akka-persistence
date: 2020-01-10 15:20:30
categories:
  - scala
  - akka
---

实现一个 OAuth 2 服务有几个核心点：

1. OAuth 2 协议解析
2. 连接的用户可能很多，系统需支持横向扩展
3. 每个连接用户的 `access_token` 的状态控制：有效期控制
4. 服务要支持容错、可恢复、可扩展、高并发等特性

使用 Akka 来实现 OAuth 2 服务会发现逻辑非常的清晰，且能很好的实现以上几个核心点。

每个连接用户或 `access_token` 可抽像为一个 **Actor**，这样多个连接用户或 `access_token` 即可并发访问。在 Actor 内部可以管理过期时间等状态。

使用 **akka-cluster-sharding** 我们可以实现连接用户的集群部署、横向扩展。而 **akka-persistence** 提供 `EventSourcedBehavior` 为 **Actor** 添加了持久化能力，这实现了可恢复特性。通过使用 Akka Cluster 机制，可以减少对外部缓存系统的依赖。

Akka Actor提供了监管机制，这样我们可对错误快速响应，实现了容错性。

## access_token

在 Akka 中通过 Actor 模型来设计 access_token 有两种主要方案：

1. 每个 `access_token` 一个 Actor，通过 ClusterSharding 来水平扩展，将 Akka Actor 做为一种有状态的缓存来使用。
2. 每个用户（**User**）一个 Actor，在用户 Actor 内部通过状态来保存多个 `access_token` 。

## 每个 access_token 一个 Actor

每个 `access_token` 一个 Actor 在设计上比较简单，只需要注意在过期时间到时停止此 Actor。示例代码如下：

```scala
object AccessTokenEntity {
  final case class State(accessToken: String, expiresEpochMillis: Long = 0L, refreshToken: String = "")
      extends CborSerializable

  sealed trait Command extends CborSerializable
  case object StopSelf extends Command
  final case class Check(replyTo: ActorRef[Int]) extends Command
  final case class Create(refreshToken: String, replTo: ActorRef[AccessToken]) extends Command

  sealed trait Event extends CborSerializable
  final case class Created(expiresIn: FiniteDuration, refreshToken: String) extends Event

  private val DEVIATION = 5000L // 过期时间比实际时间多5秒，保证客户端在过期时间点时刷新时新、旧 access_token 在一定时间内都有效
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey("AccessTokenEntity")

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] =
    ClusterSharding(system).init(
      Entity(TypeKey)(ec => apply(ec.entityId))
        .withSettings(ClusterShardingSettings(system).withPassivateIdleEntityAfter(Duration.Zero)))

  private def apply(accessToken: String): Behavior[Command] =
    Behaviors.setup(context =>
      Behaviors.withTimers { timers =>
        val behavior = EventSourcedBehavior[Command, Event, State](
          PersistenceId.of(TypeKey.name, accessToken),
          State(accessToken),
          (state, command) => commandHandler(timers, state, command),
          (state, event) => eventHandler(state, event))
        behavior.receiveSignal {
          case (state, RecoveryCompleted) =>
            val now = System.currentTimeMillis()
            if (state.expiresEpochMillis < now) context.self ! StopSelf
            else timers.startSingleTimer(StopSelf, (state.expiresEpochMillis - now).millis)
        }
      })

  private def commandHandler(timers: TimerScheduler[Command], state: State, command: Command): Effect[Event, State] =
    command match {
      case Check(replyTo) =>
        if (state.expiresEpochMillis == 0L) {
          Effect.stop().thenReply(replyTo)(_ => 401)
        } else {
          val status = if (System.currentTimeMillis() < state.expiresEpochMillis) 200 else 401
          Effect.reply(replyTo)(status)
        }

      case Create(refreshToken, replTo) =>
        if (state.expiresEpochMillis > 0L) // 返回已存在 AccessToken
          Effect.reply(replTo)(createAccessToken(state))
        else
          Effect.persist(Created(2.hours, refreshToken)).thenReply(replTo) { st =>
            timers.startSingleTimer(StopSelf, (st.expiresEpochMillis - System.currentTimeMillis()).millis)
            createAccessToken(st)
          }

      case StopSelf => Effect.stop()
    }

  private def eventHandler(state: State, event: Event): State = event match {
    case Created(expiresIn, refreshToken) =>
      state.copy(
        expiresEpochMillis = System.currentTimeMillis() + expiresIn.toMillis + DEVIATION,
        refreshToken = refreshToken)
  }
}
```

这种方案的好处在于：

1. 通过 ClusterSharding 可以实现理论上无限水平扩展的集群，无论多少个 `access_token` 都可以保存下来
0. 容错、可恢复，节点挂掉后可从其它机器上恢复令牌状态
0. 生成的 `access_token` 令牌不需要含有业务信息，只需要保证唯一性即可
0. 代码逻辑直观

这种方案的缺点有：

1. 每个 `access_token` 一个 Actor，Actor 所做的功能不多，相对具有过期时间（TTL）的缓存数据存储系统来说优势不明显
0. 每个无效 `access_token` 都会在生成一个 Actor 后才可以判断是否有效，这会造成创建很多无效的 Actor

## 每个用户一个 Actor

```scala
object UserEntity {
  final case class State(
      tokens: Map[String, DueEpochMillis] = Map(),
      refreshTokens: Map[String, DueEpochMillis] = Map())
      extends CborSerializable {
    def clear(clearTokens: IterableOnce[String], clearRefreshTokens: IterableOnce[String]): State =
      copy(tokens = tokens -- clearTokens, refreshTokens = refreshTokens -- clearRefreshTokens)

    def addToken(created: TokenCreated): State = {
      val tokenDue = OAuthUtils.expiresInToEpochMillis(created.accessTokenExpiresIn)
      val refreshTokenDue = OAuthUtils.expiresInToEpochMillis(created.refreshTokenExpiresIn)
      State(tokens + (created.accessToken -> tokenDue), refreshTokens + (created.refreshToken -> refreshTokenDue))
    }

    def addToken(accessToken: String, expiresIn: FiniteDuration): State =
      copy(tokens = tokens + (accessToken -> OAuthUtils.expiresInToEpochMillis(expiresIn)))
  }

  sealed trait Command extends CborSerializable

  final case class CreateToken(replyTo: ActorRef[AccessToken]) extends Command
  final case class CheckToken(accessToken: String, replyTo: ActorRef[Int]) extends Command
  final case class RefreshToken(refreshToken: String, replyTo: ActorRef[Option[AccessToken]]) extends Command
  final case object ClearTick extends Command

  sealed trait Event extends CborSerializable
  final case class TokenCreated(
      accessToken: String,
      accessTokenExpiresIn: FiniteDuration,
      refreshToken: String,
      refreshTokenExpiresIn: FiniteDuration)
      extends Event
  final case class TokenRefreshed(accessToken: String, expiresIn: FiniteDuration) extends Event
  final case class ClearEvent(clearTokens: Set[String], clearRefreshTokens: Set[String]) extends Event

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey("UserEntity")

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] =
    ClusterSharding(system).init(
      Entity(TypeKey)(ec => apply(ec))
        .withSettings(ClusterShardingSettings(system).withPassivateIdleEntityAfter(Duration.Zero)))

  private def apply(ec: EntityContext[Command]): Behavior[Command] = {
    val userId = ec.entityId
    Behaviors.setup(
      context =>
        Behaviors.withTimers(timers =>
          new UserEntity(PersistenceId.of(ec.entityTypeKey.name, ec.entityId), userId, timers, context)
            .eventSourcedBehavior()))
  }
}

import blog.oauth2.peruser.UserEntity._
class UserEntity private (
    persistenceId: PersistenceId,
    userId: String,
    timers: TimerScheduler[Command],
    context: ActorContext[Command]) {
  timers.startTimerWithFixedDelay(ClearTick, 2.hours)

  def eventSourcedBehavior(): EventSourcedBehavior[Command, Event, State] =
    EventSourcedBehavior(
      persistenceId,
      State(),
      (state, command) =>
        command match {
          case CheckToken(accessToken, replyTo)    => processCheckToken(state, accessToken, replyTo)
          case RefreshToken(refreshToken, replyTo) => processRefreshToken(state, refreshToken, replyTo)
          case CreateToken(replyTo)                => processCreateToken(replyTo)
          case ClearTick                           => processClear(state)
        },
      (state, event) =>
        event match {
          case TokenRefreshed(accessToken, expiresIn)      => state.addToken(accessToken, expiresIn)
          case created: TokenCreated                       => state.addToken(created)
          case ClearEvent(clearTokens, clearRefreshTokens) => state.clear(clearTokens, clearRefreshTokens)
        })

  private def processRefreshToken(
      state: State,
      refreshToken: String,
      replyTo: ActorRef[Option[AccessToken]]): Effect[Event, State] = {
    if (state.refreshTokens.get(refreshToken).exists(due => System.currentTimeMillis() < due)) {
      val refreshed = TokenRefreshed(OAuthUtils.generateToken(userId), 2.hours)
      Effect
        .persist(refreshed)
        .thenReply(replyTo)(_ => Some(AccessToken(refreshed.accessToken, refreshed.expiresIn.toSeconds, refreshToken)))
    } else {
      Effect.reply(replyTo)(None)
    }
  }

  private def processCheckToken(state: State, accessToken: String, replyTo: ActorRef[Int]): Effect[Event, State] = {
    val status = state.tokens.get(accessToken) match {
      case Some(dueTimestamp) => if (System.currentTimeMillis() < dueTimestamp) 200 else 401
      case None               => 401
    }
    Effect.reply(replyTo)(status)
  }

  private def processCreateToken(replyTo: ActorRef[AccessToken]): Effect[Event, State] = {
    val createdEvent =
      TokenCreated(OAuthUtils.generateToken(userId), 2.hours, OAuthUtils.generateToken(userId), 30.days)
    Effect.persist(createdEvent).thenReply(replyTo) { _ =>
      AccessToken(createdEvent.accessToken, createdEvent.accessTokenExpiresIn.toSeconds, createdEvent.refreshToken)
    }
  }

  private def processClear(state: State): Effect[Event, State] = {
    if (state.tokens.isEmpty && state.refreshTokens.isEmpty) {
      Effect.stop()
    } else {
      val now = System.currentTimeMillis()
      val clearTokens = state.tokens.view.filterNot { case (_, due)               => now < due }.keys.toSet
      val clearRefreshTokens = state.refreshTokens.view.filterNot { case (_, due) => now < due }.keys.toSet
      Effect.persist(ClearEvent(clearTokens, clearRefreshTokens))
    }
  }
}
```

每个用户一个 Actor 的优势有：

1. 通过 ClusterSharding 可以实现理论上无限水平扩展的集群，无论多少个 `access_token` 都可以保存下来
0. 容错、可恢复，节点挂掉后可从其它机器上恢复令牌状态
0. 相比每 `access_token` 一个 Actor，此方案可以显著减少系统 Actor 的数量
0. 相比拥有过期时间（TTL）的缓存数据存储系统，使用 Actor 更灵活，且可于业务（用户）系统在同一集群
0. 无效 `access_token` 不会生成多于的 Actor

每个用户一个 Actor 的缺点有：

1. 生成的 `access_token` 令牌不需要含有业务信息，如：用户ID
0. 从代码行数上可看出，相对每 `access_token` 一个 Actor ，此方案代码逻辑相对复杂，但功能更加强大！

## 小结

完整源码在Github可以找到 [https://github.com/yangjing/yangjing-site/tree/main/src/main/scala/blog/oauth2](https://github.com/yangjing/yangjing-site/tree/main/src/main/scala/blog/oauth2) 。
