package io.cequence.openaiscala.service

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  ReasoningEffort,
  ServiceTier,
  Verbosity
}
import io.cequence.openaiscala.domain.{
  ChatRole,
  JsonSchema,
  JsonSchemaDescription,
  JsonSchemaRange
}
import play.api.libs.json.{Json, Writes}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// top-level, so Scala 2's runtime reflection reaches the objects
object JsonSchemaDerivationTypes {

  @JsonSchemaDescription("A country and its capital")
  case class Country(
    country: String,
    @JsonSchemaDescription("The capital city") capital: String,
    @JsonSchemaRange(0, 2000) populationMil: Int,
    ratioOfMenToWomen: Double,
    nickname: Option[String],
    languages: Seq[String]
  )

  case class CapitalsResponse(capitals: Seq[Country])

  object Weekday extends Enumeration {
    val Mon, Tue, Wed = Value
  }

  sealed trait Sentiment
  case object Positive extends Sentiment
  case object Negative extends Sentiment
  case object Mixed extends Sentiment

  // a value named by its toString, like the library's own NamedEnumValue enums
  sealed abstract class Severity(value: String) {
    override def toString: String = value
  }

  object Severity {
    case object Low extends Severity("low")
    case object High extends Severity("high")
  }

  sealed trait Animal
  sealed trait Pet extends Animal
  case object Dog extends Pet
  case object Wolf extends Animal

  case class Review(
    sentiment: Sentiment,
    severity: Option[Severity],
    animal: Animal,
    weekday: Weekday.Value,
    unit: java.util.concurrent.TimeUnit,
    @JsonSchemaRange(1.5, 4.5) scores: List[Int],
    ratings: Vector[Double],
    tags: Set[String],
    ids: Array[Long],
    id: java.util.UUID,
    flag: java.lang.Boolean,
    big: BigInt,
    amount: BigDecimal,
    initial: Char,
    created: java.util.Date,
    day: java.time.LocalDate,
    month: java.time.Month
  )

  case class Box[T](
    item: T,
    items: List[T],
    maybe: Option[T]
  )

  // values with descriptions of their own
  sealed trait Priority
  @JsonSchemaDescription("Needs a reply today") case object Urgent extends Priority
  @JsonSchemaDescription("Can wait a week") case object Routine extends Priority
  case object Unknown extends Priority

  case class Ticket(
    @JsonSchemaDescription("How soon must we reply?") priority: Priority,
    @JsonSchemaDescription("Which priorities were mentioned?") mentioned: Seq[Priority],
    previous: Option[Priority]
  )

  case class Backticked(
    `type`: String,
    `class-name`: Int
  )

  // an Enumeration declared in a class - no reflection reaches its values
  class Palette {
    object Color extends Enumeration {
      val Red, Green = Value
    }

    case class Paint(
      color: Color.Value,
      shade: Option[Color.Value]
    )
  }

  // the library's own enums: sealed traits of case objects named by EnumValue's toString
  case class LibraryEnums(
    role: ChatRole,
    effort: ReasoningEffort,
    tier: Option[ServiceTier],
    format: ChatCompletionResponseFormatType,
    verbosity: Seq[Verbosity]
  )
}

class JsonSchemaDerivationSpec
    extends AnyWordSpec
    with Matchers
    with JsonSchemaReflectionHelper {

  import JsonSchemaDerivationTypes._

  private val countrySchema = JsonSchema.Object(
    properties = Seq(
      "country" -> JsonSchema.String(),
      "capital" -> JsonSchema.String(Some("The capital city")),
      "populationMil" -> JsonSchema.Integer(minimum = Some(0), maximum = Some(2000)),
      "ratioOfMenToWomen" -> JsonSchema.Number(),
      "nickname" -> JsonSchema.String(),
      "languages" -> JsonSchema.Array(JsonSchema.String())
    ),
    required = Seq("country", "capital", "populationMil", "ratioOfMenToWomen", "languages"),
    description = Some("A country and its capital")
  )

  "jsonSchemaFor" should {

    "derive an object from a case class, with descriptions, ranges and optional fields" in {
      jsonSchemaFor[Country]() shouldBe countrySchema
    }

    "derive nested case classes in collections" in {
      jsonSchemaFor[CapitalsResponse]() shouldBe JsonSchema.Object(
        Seq("capitals" -> JsonSchema.Array(countrySchema)),
        required = Seq("capitals")
      )
    }

    "derive enums, collections and the other value types" in {
      jsonSchemaFor[Review]() shouldBe JsonSchema.Object(
        properties = Seq(
          "sentiment" -> JsonSchema.String(`enum` = Seq("Mixed", "Negative", "Positive")),
          "severity" -> JsonSchema.String(`enum` = Seq("high", "low")),
          "animal" -> JsonSchema.String(`enum` = Seq("Dog", "Wolf")),
          "weekday" -> JsonSchema.String(`enum` = Seq("Mon", "Tue", "Wed")),
          "unit" -> JsonSchema.String(
            `enum` = java.util.concurrent.TimeUnit.values().toSeq.map(_.name)
          ),
          // an integer range takes the whole numbers within
          "scores" -> JsonSchema.Array(
            JsonSchema.Integer(minimum = Some(2), maximum = Some(4))
          ),
          "ratings" -> JsonSchema.Array(JsonSchema.Number()),
          "tags" -> JsonSchema.Array(JsonSchema.String()),
          "ids" -> JsonSchema.Array(JsonSchema.Integer()),
          "id" -> JsonSchema.String(),
          "flag" -> JsonSchema.Boolean(),
          "big" -> JsonSchema.Integer(),
          "amount" -> JsonSchema.Number(),
          "initial" -> JsonSchema.String(),
          "created" -> JsonSchema.String(),
          "day" -> JsonSchema.String(),
          // a Java enum, though also a java.time value
          "month" -> JsonSchema.String(`enum` = java.time.Month.values().toSeq.map(_.name))
        ),
        required = Seq(
          "sentiment",
          "animal",
          "weekday",
          "unit",
          "scores",
          "ratings",
          "tags",
          "ids",
          "id",
          "flag",
          "big",
          "amount",
          "initial",
          "created",
          "day",
          "month"
        )
      )
    }

    "resolve type parameters" in {
      val schema = jsonSchemaFor[Box[Country]]()

      schema shouldBe JsonSchema.Object(
        Seq(
          "item" -> countrySchema,
          "items" -> JsonSchema.Array(countrySchema),
          "maybe" -> countrySchema
        ),
        required = Seq("item", "items")
      )
    }

    "write the descriptions of an enum's values into the field's description" in {
      val values = Seq("Routine", "Unknown", "Urgent")
      val lines = "- Routine: Can wait a week\n- Urgent: Needs a reply today"

      jsonSchemaFor[Ticket]() shouldBe JsonSchema.Object(
        Seq(
          "priority" -> JsonSchema.String(Some(s"How soon must we reply?\n$lines"), values),
          // a multi-select: the lines go with the field, where each option's question reads them
          "mentioned" -> JsonSchema.Array(
            JsonSchema.String(`enum` = values),
            Some(s"Which priorities were mentioned?\n$lines")
          ),
          "previous" -> JsonSchema.String(Some(lines), values)
        ),
        required = Seq("priority", "mentioned")
      )
    }

    "use the decoded field names" in {
      jsonSchemaFor[Backticked]() shouldBe JsonSchema.Object(
        Seq("type" -> JsonSchema.String(), "class-name" -> JsonSchema.Integer()),
        required = Seq("type", "class-name")
      )
    }

    "write a date as a number when asked" in {
      val schema = jsonSchemaFor[Review](dateAsNumber = true).asInstanceOf[JsonSchema.Object]
      schema.properties.toMap.apply("created") shouldBe JsonSchema.Number()
    }

    "use an explicit schema for a field name, at any depth" in {
      val capital = JsonSchema.String(Some("One of the two"), Seq("Paris", "Rome"))
      val schema = jsonSchemaFor[CapitalsResponse](explicitTypes = Map("capital" -> capital))

      val country = schema
        .asInstanceOf[JsonSchema.Object]
        .properties
        .head
        ._2
        .asInstanceOf[JsonSchema.Array]
        .items
        .asInstanceOf[JsonSchema.Object]

      country.properties.toMap.apply("capital") shouldBe capital
    }

    "make an Enumeration it cannot reach a plain string" in {
      val palette = new Palette
      val schema = jsonSchemaFor[palette.Paint]().asInstanceOf[JsonSchema.Object]

      schema.properties.toMap shouldBe Map(
        "color" -> JsonSchema.String(),
        "shade" -> JsonSchema.String()
      )
      schema.required shouldBe Seq("color")
    }

    "take an enum's values as its enumFormat writes them" in {
      val properties =
        jsonSchemaFor[LibraryEnums]().asInstanceOf[JsonSchema.Object].properties.toMap

      def values(schema: JsonSchema) = schema match {
        case JsonSchema.String(_, values)                      => values
        case JsonSchema.Array(JsonSchema.String(_, values), _) => values
        case other => fail(s"Not a string enum: $other")
      }

      def written[E: Writes](all: Seq[E]) = all.map(Json.toJson(_).as[String]).sorted

      values(properties("role")) shouldBe written[ChatRole](
        Seq(
          ChatRole.User,
          ChatRole.System,
          ChatRole.Developer,
          ChatRole.Assistant,
          ChatRole.Function,
          ChatRole.Tool
        )
      )
      values(properties("effort")) shouldBe written(ReasoningEffort.values)
      values(properties("tier")) shouldBe written(ServiceTier.values)
      values(properties("format")) shouldBe written[ChatCompletionResponseFormatType](
        Seq(
          ChatCompletionResponseFormatType.text,
          ChatCompletionResponseFormatType.json_object,
          ChatCompletionResponseFormatType.json_schema
        )
      )
      values(properties("verbosity")) shouldBe written[Verbosity](
        Seq(Verbosity.low, Verbosity.medium, Verbosity.high)
      )
    }

    "give a type's schema as a JsonSchemaOf, unless one of your own is in scope" in {
      JsonSchemaOf[Country].schema shouldBe countrySchema

      locally {
        implicit val custom: JsonSchemaOf[Country] = JsonSchemaOf.instance(JsonSchema.String())
        JsonSchemaOf[Country].schema shouldBe JsonSchema.String()
      }
    }

    "be callable through the companion" in {
      JsonSchemaReflectionHelper.jsonSchemaFor[Country]() shouldBe countrySchema
    }
  }
}
