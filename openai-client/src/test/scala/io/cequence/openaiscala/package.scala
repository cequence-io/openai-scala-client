package io.cequence

import io.cequence.openaiscala.JsonPrintMode.{Compact, Pretty}
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Format, Json}

package object openaiscala extends Matchers {

  sealed trait JsonPrintMode

  object JsonPrintMode {
    case object Compact extends JsonPrintMode
    case object Pretty extends JsonPrintMode
  }

  def testCodec[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact
  )(
    implicit format: Format[A]
  ): Unit = {
    val jsValue = Json.toJson(value)
    val serialized = printMode match {
      case Compact => jsValue.toString()
      case Pretty  => Json.prettyPrint(jsValue)
    }
    serialized shouldBe json

    Json.parse(json).as[A] shouldBe value
  }

}
