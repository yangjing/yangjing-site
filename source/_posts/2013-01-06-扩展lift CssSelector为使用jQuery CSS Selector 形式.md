title: 扩展lift CssSelector为使用jQuery CSS Selector形式
date: 2013-01-06 23:16:58
categories: scala
tags:
- scala
- liftweb
- jquery
---
***啥也不说了，直接上代码***

``` scala
    import scala.xml.{ Text, NodeSeq }

    import net.liftweb.http.js.{ JsExp, JE }
    import net.liftweb.util.{ CssSel, ComputeTransformRules }
    import net.liftweb.util.Helpers._

    object Imports {

      private val _selRegex = new scala.util.matching.Regex("""(\S)+""")
      private val _selMatch = Set('*', '-', '[', '^')

      implicit final class YStringToCssSel(exp: String) {
        def #>>[T](content: => T)(implicit computer: ComputeTransformRules[T]): CssSel = {
          val selects = _selRegex.findAllMatchIn(exp).map(_.matched).toList

          val (init, last) =
            if (_selMatch.contains(selects.last.head))
              selects.dropRight(2) -> selects.takeRight(2).mkString(" ")
            else
              selects.dropRight(1) -> selects.takeRight(1).mkString

          init.foldRight(last #> content)((s, c) => s #> c)
        }
      }

      implicit val doubleTransform: ComputeTransformRules[Double] = new ComputeTransformRules[Double] {
        def computeTransform(str: => Double, ns: NodeSeq): Seq[NodeSeq] = List(Text(str.toString))
      }

      implicit val shortTransform: ComputeTransformRules[Short] = new ComputeTransformRules[Short] {
        def computeTransform(str: => Short, ns: NodeSeq): Seq[NodeSeq] = List(Text(str.toString))
      }

      implicit val byteTransform: ComputeTransformRules[Byte] = new ComputeTransformRules[Byte] {
        def computeTransform(str: => Byte, ns: NodeSeq): Seq[NodeSeq] = List(Text(str.toString))
      }

      implicit val charTransform: ComputeTransformRules[Char] = new ComputeTransformRules[Char] {
        def computeTransform(str: => Char, ns: NodeSeq): Seq[NodeSeq] = List(Text(str.toString))
      }

      implicit val floatTransform: ComputeTransformRules[Float] = new ComputeTransformRules[Float] {
        def computeTransform(str: => Float, ns: NodeSeq): Seq[NodeSeq] = List(Text(str.toString))
      }

    }
```

还是给个测试吧：

``` scala
    ("#id .class span *+" #>> "羊八井！").apply(<div id="id"><a class="class"><span>您好，</span></a></div>)
```

- <script src="https://gist.github.com/yangjing/5010139.js"></script>

