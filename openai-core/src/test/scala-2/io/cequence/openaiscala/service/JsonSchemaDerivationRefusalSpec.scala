package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object JsonSchemaRefusalTypes {
  // case objects declared in a class - Scala 2's reflection cannot reach them, so they are named
  // by their names
  class Kennel {
    sealed trait Size
    case object Small extends Size
    case object Large extends Size

    case class Dog(size: Size)
  }

  sealed trait Shape
  case class Circle(radius: Double) extends Shape

  case class Tree(children: Seq[Tree])
  case class WithTuple(pair: (String, Int))
  case class WithEither(value: Either[String, Int])
  case class WithShape(shape: Shape)
}

// Scala 2 refuses an unsupported type when called; Scala 3 at compile time (see the scala-3
// sibling)
class JsonSchemaDerivationRefusalSpec
    extends AnyWordSpec
    with Matchers
    with JsonSchemaReflectionHelper {

  import JsonSchemaRefusalTypes._

  private def refusal(schema: => Any): String =
    intercept[OpenAIScalaClientException](schema).getMessage

  "jsonSchemaFor (Scala 2)" should {

    "refuse an Either" in {
      refusal(jsonSchemaFor[WithEither]()) should include("an Either")
    }

    "refuse a recursive case class" in {
      refusal(jsonSchemaFor[Tree]()) should include("a recursive type")
    }

    "refuse a tuple" in {
      refusal(jsonSchemaFor[WithTuple]()) should include("a tuple")
    }

    "refuse a sealed hierarchy with case classes" in {
      refusal(jsonSchemaFor[WithShape]()) should include("a sealed hierarchy")
    }

    "name the case objects it cannot reach" in {
      val kennel = new Kennel
      jsonSchemaFor[kennel.Dog]()
        .asInstanceOf[io.cequence.openaiscala.domain.JsonSchema.Object]
        .properties
        .toMap
        .apply("size") shouldBe
        io.cequence.openaiscala.domain.JsonSchema.String(None, Seq("Large", "Small"))
    }
  }
}
