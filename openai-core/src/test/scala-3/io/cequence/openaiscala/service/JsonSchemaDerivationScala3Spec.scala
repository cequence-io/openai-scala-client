package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{JsonSchema, JsonSchemaDescription}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.compiletime.testing.{typeCheckErrors, Error}

object JsonSchemaScala3Types {

  enum Color {
    case Red, Green, Blue
  }

  enum Shape {
    case Circle(radius: Double)
    case Square(side: Double)
  }

  case class Paint(
    @JsonSchemaDescription("The paint's color") color: Color,
    shades: Seq[Color]
  )

  enum Mood {
    @JsonSchemaDescription("Happy with the product") case Happy
    @JsonSchemaDescription("Angry or frustrated") case Angry
    case Neutral
  }

  case class Feedback(
    @JsonSchemaDescription("How does the customer feel?") mood: Mood
  )

  case class WithShape(shape: Shape)
  case class WithMap(counts: Map[String, Int])
  case class Tree(children: Seq[Tree])
  case class WithTuple(pair: (String, Int))
  case class WithEither(value: Either[String, Int])
}

class JsonSchemaDerivationScala3Spec extends AnyWordSpec with Matchers with JsonSchemaReflectionHelper {

  import JsonSchemaScala3Types.*

  "jsonSchemaFor (Scala 3)" should {

    "derive a string enum from an enum of singleton cases, in declaration order" in {
      jsonSchemaFor[Paint]() shouldBe JsonSchema.Object(
        Seq(
          "color" -> JsonSchema.String(
            Some("The paint's color"),
            Seq("Red", "Green", "Blue")
          ),
          "shades" -> JsonSchema.Array(JsonSchema.String(`enum` = Seq("Red", "Green", "Blue")))
        ),
        required = Seq("color", "shades")
      )
    }

    "write the descriptions of an enum's cases into the field's description, in declaration order" in {
      jsonSchemaFor[Feedback]() shouldBe JsonSchema.Object(
        Seq(
          "mood" -> JsonSchema.String(
            Some(
              "How does the customer feel?\n- Happy: Happy with the product\n- Angry: Angry or frustrated"
            ),
            Seq("Happy", "Angry", "Neutral")
          )
        ),
        required = Seq("mood")
      )
    }

    "refuse unsupported types at compile time" in {
      def errors(code: List[Error]) = code.map(_.message).mkString("\n")

      errors(typeCheckErrors("jsonSchemaFor[WithMap]()")) should include("- a map")
      errors(typeCheckErrors("jsonSchemaFor[Tree]()")) should include("- a recursive type")
      errors(typeCheckErrors("jsonSchemaFor[WithTuple]()")) should include("- a tuple")
      errors(typeCheckErrors("jsonSchemaFor[WithEither]()")) should include(
        "- a sealed hierarchy or enum with the class Left"
      )
      errors(typeCheckErrors("jsonSchemaFor[WithShape]()")) should include(
        "- a sealed hierarchy or enum with the class Circle"
      )
      // a well-typed call compiles
      typeCheckErrors("jsonSchemaFor[Paint]()") shouldBe Nil
    }
  }
}
