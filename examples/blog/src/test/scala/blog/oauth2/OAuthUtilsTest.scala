package blog.oauth2

import org.scalatest.{ FunSuite, Matchers }

class OAuthUtilsTest extends FunSuite with Matchers {
  test("generateToken()") {
    val token = OAuthUtils.generateToken()
    token.length shouldBe 42
    println(token)
    println(token.size)
  }

  test("generateToken(userId)") {
    val token = OAuthUtils.generateToken("yangjing")
    println(token)
    println(token.size)
  }
}
