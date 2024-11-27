title: Scala实战：并发-Future和Promise
date: 2015-11-28 15:23:32
categories:
- scala
- scala实战
tags:
- scala
- future
- promise
- 并发
- scala实战
---

并发编程是很困难的，特别是在你没有很好的设计与抽像你的功能层次时。传统的并发解决方案是采用多线程和共享变量，这使得随着代码的增加你很难找到错误根源。

Scala中采用了更好的方案，它不是只基于更低层次的线程的。Scala为用户提供了更高级的抽象：`Futures`和`Promises`（[Akka](https://akka.io)还提供了基于`actor`模式的编程范式，是一种更高层次的并发编程抽象。本文主要讲解`Futures`和`Promises`，这里提供一些进一步学习的参考）。

## Future

`Future`是持有某个值的对象，它完成一些计算并允许在“将来”的某一个时刻获取结果。它也可以是其它计算结果的结果（简单点说就是多个`Future`可以嵌套）。创建一个`Future`最简单的方式就调用它的`apply`方法，它将直接开始一个异步计算，并返回一个`Future`对象，它将包含计算结果。

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Failure}

def computation(): Int = { 25 + 50 }
val theFuture = Future { computation() }
```

第一行导入了`ExecutionContext.Implicits.global`作为当前环境的一个默认执行上下文。现在先暂时不管它的具体含意，只要知道它会提供一个线程池，所有任务最终都会被提交给它来异步执行就可以了。在这个示例中先定义`computation`函数，并在`Future { ... }`代码块中调用。程序会使用上下文中的找到的线程池（由`ExecutionContext.Implicits.global`导入）并马上开始异步执行。

## 访问Future的结果

前面说了，`Future`返回值有两种类型：`Success`和`Failure`。而`Future`在执行后提供了3个回调函数来让你访问结果，它们分别是：

- def onSuccess[U](pf: PartialFunction[T, U]): 传入一个偏函数，可以使用模式匹配来处理你想要的结果
- def onFailure[U](pf: PartialFunction[Throwable, U]): 传入一个偏函数，可以使用模式匹配来处理你想要的异常
- def onComplete[U](f: Try[T] => U): 传一个接受`Try[T]`（[http://www.yangjing.me/2013/02/16/Option-Either-Try/](http://www.yangjing.me/2013/02/16/Option-Either-Try/)）类型的函数。

上面3个回调函数都要求返回一个类型为`U`的返回值，这也得益于Scala的类型自动推导功能，你可以减少很多的样版代码。

当Future完成后，我们注册的回调函数将收到值。一个常用的注册回调函数是`onComplete`，它期待传入一个偏函数，并处理`Success[T]`和`Failure[E]`两种情况。（编函数将另文介绍）

```scala
theFuture.onComplate {
  case Success(result) => println(result)
  case Fialure(t) => println(s"Error: %{t.getMessage}")
}
```

可以看到，在Scala中写多线程代码是非常轻松惬意的。但是，你以为使用`Future`只是简化了`new Thead`或`new Runnable`的代码量而以，那就大错特错了。Scala的Future不只这些功能……

## 合并多个Future的结果

实际工作中，我们经常遇到需要向多个来源同时异步请求数据的时候。这时我们就需要等所以来源数据都返回后将结果集处理后再返回。使用`Future.sequence`方法，接收一个包含`Future`的列表来将一系列Future的结果汇总到一个`List`单一结果里输出。完整代码在：[http://git.oschina.net/yangjing/codes/08pacy2lubgqnkmv1xojd](http://git.oschina.net/yangjing/codes/08pacy2lubgqnkmv1xojd)

```scala
    val f1 = Future {
      TimeUnit.SECONDS.sleep(1)
      "f1"
    }

    val f2 = Future {
      TimeUnit.SECONDS.sleep(2)
      "f2"
    }

    val f3 = Future {
      TimeUnit.SECONDS.sleep(3)
      2342
    }

    val f4 = Future.sequence(Seq(f1, f2, f3))

    val results: List[Any] = Await.result(f4, 4.seconds)

    println(results) // 输出：List(f1, f2, 2342)
```

代码`f1`、`f2`、`f3`字义了3个异步操作并马上执行。`f4`将3个异步操作的结果合并到一个List里返回，同时`f4`也是一个异步操作。除了采用`Future.sequence`提供的方便函数，我们还可以使用**for comprehension**特性来更灵活的合并多个`Future`的结果。

我们把`f4`的操作改成使用**for推导式**形式：

```scala
    val f4: Future[(String, String, Int)] =
      for {
        r2 <- f2
        r3 <- f3
        r1 <- f1
      } yield (r1.take(1), r2.drop(1), r3 + 1)

    val (f1Str, f2Str, f3Int) = Await.result(f4, 4.seconds)

    println(s"f1: $f1Str, f2: $f2Str, f3: $f3Int") // 输出：f1: f, f2: 2, f3: 2342
```

可以看到**for推导式**也可以使用在`Future`上，`r2 <- f2`代码的含意是在`f2`这个`Future`执行完后将结果赋值给变量`r2`。与`Future.sequence`将多个线程的返回值合并到一个List不同。使用**for推导式**，在`yield`语句部分你可以对每个线程的运算结果做更自由的处理，并返回自己想要的类型（这得益于Scala强大的类型推导功能，不需要你显示的声明变量值类型）。

## 异常处理

前文代码，当`Future`代码块内有异常抛出时使用**Future.sequence**和**for comprehension**也会抛出异常，你将不能正确的获得结果。这时，可以使用**Future**提供的`recover`方法处理异常，并把异常恢复成一个正确值返回。

```scala
def recover[U >: T](pf: PartialFunction[Throwable, U])
```

`recover`常用使用方式如下：

```scala
Future (6 / 0) recover { case e: ArithmeticException => 0 } // result: 0
Future (6 / 0) recover { case e: NotFoundException   => 0 } // result: exception
Future (6 / 2) recover { case e: ArithmeticException => 0 } // result: 3
```

接下来看看怎样使用`recover`来将处理异常并可使返回值可正确应用于**Future.sequence**和**for comprehension**中。以之前的`f2`举例，修改代码如下：

```scala
val f2 = Future {
  throw new RuntimeException("throw exception")
}.recover {
  case e: Exception =>
    "handled exception"
}
```

采用上面的步骤将异常转换成一个值返回，`f2`就可以正确的应用到合并代码里了。

## Promise

**Promise**是一个承若，它是一个可修改的对象。一个**Promise**可以在未来成功的完成一个任务（使用`p.success`来完成），也可能用来完成一个失败（通过返回一个异常，使用`p.failure`）。失败了的**Promise**，可以通过`f.recover`来处理故障。考虑一个把`com.google.common.util.concurrent.FutureCallback<V>`封装成Scala的`Future`的例子，看看`Promise`是怎样使用的。

```scala
    val promise = Promise[R]()
    Futures.addCallback(
      resultSetFuture,
      new FutureCallback[ResultSet] {
        override def onFailure(t: Throwable): Unit = promise.failure(t)

        override def onSuccess(rs: ResultSet): Unit = promise.complete(Try(func(rs)))
      },
      ec)
    promise.future
```

## 总结

Scala使用`Future`和`Promise`对并发编程提供了快捷的支持，同时对多个`Future`结果的合并和`Future`的异常恢复也提供了优雅的解决方案。
