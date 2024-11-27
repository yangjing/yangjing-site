title: Scala实战：求解 Top K 问题
date: 2020-02-06 17:06:12
category: scala
tags:
  - scala
  - akka
  - akka-streams
  - top-k
---

## 问题描述

服务器上有一个 movies.csv 文件，里面保存了每部电影的评分（为了简化和专注问题，CSV文件每一行只有两个字段：movieId和rating）。文件通过HTTP服务器发布。要求从文件内找出排名最高的10部电影。

## 解法1：全量排序求Top 10

通过 `wget`、`curl` 等工具先将文件下载到本地，再读出文件内所有行并解析出 `movieId`和`rating` 字段，按 `rating` 字段排序并求得分最高的 10 部电影。这种方法逻辑很简单，实现代码如下：

```scala
final case class Movie(id: String, rating: Double)

val top10 = scala.io.Source.fromFile("/tmp/movies.csv").getLines()
  //.drop(1) // if csv header exists.
  .flatMap { line =>
    line.split(',') match {
      case Array(movieId, rating) => Try(Movie(movieId, rating.toDouble)).toOption
      case _ => None
    }
  }
  .toVector
  .sortWith(_.rating > _.rating)
  .take(10)
```

## 解法2：遍历一次文件求出Top 10

因为我们只是找出得分最高的10部电影，可以预先定义一个有序 `top10` 集合，在遍历 `movies.csv` 的每一部电影时将其与 `top10` 集合里得分最低的一部电影比较。若得分大于集合里最低的那部电影，则将集合里得分最低的电影去掉，将将当前电影加入集合。这样，我们只需要遍历一次文件即可获得得分最高的10部电影。

```scala
final case class Movie(id: String, rating: Double)

var top10 = Vector[Movie]()
scala.io.Source.fromFile("/tmp/movies.csv").getLines()
  //.drop(1) // if csv header exists.
  .flatMap { line =>
    line.split(',') match {
      case Array(movieId, rating) => Some(Movie(movieId, rating.toDouble))
      case _ => None
    }
  }
  .foreach { movie =>
    top10 = if (top10.size < 10) (movie +: top10).sortWith(_.rating < _.rating)
      else if (top10.head.rating > movie.rating) top10
      else (movie +: top10.tail).sortWith(_.rating < _.rating)
  }
```

## 解法3：使用Akka Streams异步求出Top K个得分最高的电影

`FileIO` 是Akka Streams自带的一个文件读、写工具类，可以从一个文件生成 `Source[ByteString, Future[IOResult]]` 或将 `Source[ByteString, Future[IOResult]]` 写入文件。`Framing.delimiter` 可以从Akka Streams 的 `ByteString` 流以指定分隔符（`\n`）按行提取内容，并将每一行数据发送到流程的下一步骤。

-------------------------------------------------
***注意***

这里 `Framing.delimiter` 的第3个参数 `allowTruncation` 需要设置为 **true** ，否则文件在不以 `\n` 结尾的情况下将抛出以下异常：`akka.stream.scaladsl.Framing$FramingException: Stream finished but there was a truncated final frame in the buffer` 。

-------------------------------------------------

如果设置为 **false**，则当正在解码的最后一个帧不包含有效的分隔符时，此流将失败，而不是返回截断的帧数据。*

```scala
implicit val system = ActorSystem(Behaviors.ignore, "topK")
val res = Paths.get(Thread.currentThread().getContextClassLoader.getResource("movies.csv").toURI)

val topKF = FileIO
  .fromPath(Paths.get(res.toUri))
  .via(CsvParsing.lineScanner())
  .drop(1) // Drop CSV Header
  .mapConcat {
    case name :: AsDouble(rating) :: _ => Movie(name.utf8String, rating) :: Nil
    case _                             => Nil
  }
  .runWith(new TopKSink(10))

val topN = Await.result(topKF, 5.minutes)

topN.foreach(println)
println(topN.size)

system.terminate()
```

这里使用 alpakka-csv 来将 `ByteString` 数据流转换成 CSV 数据格式，可以在 [https://doc.akka.io/docs/alpakka/current/data-transformations/csv.html](https://doc.akka.io/docs/alpakka/current/data-transformations/csv.html) 找到这个库的详细使用说明。

```scala
def toMovie(bs: ByteString): Either[Throwable, Movie] =
  try {
    val arr = bs.utf8String.split(',')
    Right(Movie(arr(0), arr(1).toDouble))
  } catch {
    case e: Throwable => Left(e)
  }
```

### 自定义Sink：`TopKSink`

`.runWith(new TopKSink(10))` 调用自定义的 Akka Streams `Sink` 来获得得分最高的10部电影。让我们先来看看 `TopKSink` 的实现：

```scala
class TopKSink(TOP_K: Int) extends GraphStageWithMaterializedValue[SinkShape[Movie], Future[List[Movie]]] {
  val in: Inlet[Movie] = Inlet("TopKSink.in")

  override def shape: SinkShape[Movie] = SinkShape(in)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Future[List[Movie]]) = {
    val p: Promise[List[Movie]] = Promise()

    val logic = new GraphStageLogic(shape) with InHandler {
      var buf = List[Movie]()
      var bufSize = 0

      def insertMovie(list: List[Movie], movie: Movie): List[Movie] = {
        list match {
          case Nil => movie :: Nil
          case list =>
            var buf = List[Movie]()
            var use = false
            for (item <- list.reverse) {
              if (!use && item.rating < movie.rating) {
                buf ::= movie
                use = true
              }
              buf ::= item
            }
            if (!use) {
              buf ::= movie
            }
            buf
        }
      }
      override def preStart(): Unit = pull(in)

      override def onPush(): Unit = {
        val movie = grab(in)
        buf = if (bufSize < TOP_K) {
          bufSize += 1
          insertMovie(buf, movie)
        } else {
          if (buf.head.rating < movie.rating) insertMovie(buf.slice(1, TOP_K), movie) else buf
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        p.trySuccess(buf)
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        p.tryFailure(ex)
        failStage(ex)
      }

      override def postStop(): Unit = {
        if (!p.isCompleted) p.failure(new AbruptStageTerminationException(this))
      }

      setHandler(in, this)
    }

    (logic, p.future)
  }
}
```

## 解法4：通过 Akka HTTP 在下载文件的同时求出Top K个得分最高的电影

Akka HTTP提供了 HTTP Client/Server 实现，同时它也是基于 Akka Streams 实现的。上一步我们已经定义了 `TopKSink` 来消费流数据，而通过 Akka HTTP Client 获得的响应数据也是一个流（`Source[ByteString, Any]`）。我们可以将获取 `movies.csv` 文件的 HTTP 请求与取得分最高的K部电影两个任务结合到一起，**实现内存固定、处理数据无限的 Top K 程序（假设网络稳定不会断开）**。

```scala
implicit val system = ActorSystem(Behaviors.ignore, "topK")
implicit val ec = system.executionContext
val TOP_K = 10
val URL =
  "https://gitee.com/yangjing/akka-cookbook/raw/master/cookbook-streams/src/main/resources/movies.csv"

val topKF = Http(system).singleRequest(HttpRequest(uri = URL)).flatMap { response =>
  response.entity.dataBytes
    .via(CsvParsing.lineScanner())
    .drop(1) // Drop CSV Header
    .mapConcat {
      case name :: AsDouble(rating) :: _ => Movie(name.utf8String, rating) :: Nil
      case _                             => Nil
    }
    .runWith(new TopKSink(TOP_K))
}

val topN = Await.result(topKF, 5.minutes)

topN.foreach(println)
println(topN.size)

system.terminate()
```

通过继承 `GraphStageWithMaterializedValue` 抽像类，可以定义一个返回特定结果的自定义 `Sink`，否则流处理结果默认为 `NotUsed`。

```scala
  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Future[List[Movie]])
```

函数 `createLogicAndMaterializedValue` 实现 `Sink` 处理逻辑并返回 `Sink` 阶段的处理逻辑 `GraphStageLogic` 和获得的 Top K 结果 `Future[List[Movie]]`，流执行后的结果（通过调用 `.runWith`）是一个异步结果（`Future`）。这样将不会阻塞调用线程。

`buf` 用于缓存 Top K 个得分最高的电影，使用 `List` 模拟了一个堆结构，Top K 里评分最低的电影在链表头且按评分升序排序。

`onPush` 函数上游有数据传入时调用 `grab` 函数获取一个元素（`movie`）。`bufSize` 保存了当前 `buf` 的数量，当 `bufSize < TOP_K` 时，调用 `insertMovie` 函数将 `movie` 直接插入到匹配顺序的 `buf` 里并将 `bufSize` 加1。否则通过 `buf.head.rating < movie.rating` 比较，若为 true 则将 `movie` 加入缓存，否则 `buf` 保持不变。

`insertMovie` 函数实现了将新电影插入 `buf` 的逻辑，并保持 `buf` 按评分升序排序。

## 小结

本文使用4种方式来求解 Top K 问题，从简单粗暴的全量读入内存并排序；到不使用排序通过一次遍历获得 Top K；再使用 Akka Streams 以流式方式异步获得；最后，通过结合 Akka HTTP 和 Akka Streams，可以HTTP请求的同时计算 Top K。

有关 Akka HTTP 更多内容可阅读 [《Scala Web 开发——基于Akka HTTP》](https://www.yangjing.me/scala-web-development/) 。

