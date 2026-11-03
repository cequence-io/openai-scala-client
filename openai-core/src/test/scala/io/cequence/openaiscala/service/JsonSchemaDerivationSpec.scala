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
  JsonSchemaIgnore,
  JsonSchemaRange
}
import play.api.libs.json.{Json, Writes}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// case objects declared at a package's top level - their order is not known (sorted)
sealed trait PackageLevelShade
case object Zinc extends PackageLevelShade
case object Amber extends PackageLevelShade

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

  // value classes may not be members of a class - here they are members of an object
  final case class UserId(value: String) extends AnyVal
  final case class Points(count: Int) extends AnyVal

  case class Extras(
    user: UserId,
    points: Option[Points],
    counts: Map[String, Int],
    props: java.util.Map[String, String],
    nickname: java.util.Optional[String],
    items: java.util.List[java.lang.Integer],
    labels: java.util.Set[String],
    uri: java.net.URI,
    url: java.net.URL,
    locale: java.util.Locale,
    currency: java.util.Currency,
    path: java.nio.file.Path,
    file: java.io.File,
    zone: java.time.ZoneId,
    age: java.time.Duration,
    period: java.time.Period,
    timeout: scala.concurrent.duration.FiniteDuration
  )

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

  // a hierarchy declared level by level in objects - in declaration order, flattened
  sealed trait Mood

  object Mood {
    case object Calm extends Mood
    sealed trait Upset extends Mood

    object Upset {
      case object Sad extends Upset
      case object Angry extends Upset
    }

    case object Happy extends Mood
  }

  case class Moods(
    mood: Mood,
    shade: PackageLevelShade
  )

  // the annotations on every kind of field
  sealed trait Level

  object Level {
    @JsonSchemaDescription("Needs action now") case object high extends Level
    case object medium extends Level
    @JsonSchemaDescription("Can wait") case object low extends Level
  }

  object Channel extends Enumeration {
    val email, phone, chat = Value
  }

  @JsonSchemaDescription("Where it happened")
  case class Place(
    @JsonSchemaDescription("The city") city: String,
    @JsonSchemaDescription("Postal code, if known") zip: Option[String]
  )

  @JsonSchemaDescription("An incident")
  case class Incident(
    @JsonSchemaDescription("How bad") level: Level,
    @JsonSchemaDescription("The level it may escalate to") escalation: Option[Level],
    @JsonSchemaDescription("Levels mentioned") mentioned: Seq[Level],
    @JsonSchemaDescription("Levels the customer named, if any") named: Option[Seq[Level]],
    @JsonSchemaDescription("Contact channel") channel: Channel.Value,
    @JsonSchemaDescription("Preferred channel, if any") preferred: Option[Channel.Value],
    @JsonSchemaDescription("Time unit") unit: java.util.concurrent.TimeUnit,
    @JsonSchemaDescription("Fallback unit, if any")
    fallbackUnit: Option[java.util.concurrent.TimeUnit],
    @JsonSchemaDescription("Hours to fix") @JsonSchemaRange(1, 48) hours: Int,
    @JsonSchemaDescription("Hours spent, if known") @JsonSchemaRange(0, 100) spent: Option[
      Int
    ],
    @JsonSchemaDescription("Confidence, if known") @JsonSchemaRange(0, 1) score: Option[
      Double
    ],
    @JsonSchemaDescription("Ratings, if any") @JsonSchemaRange(1, 5) ratings: Option[Seq[Int]],
    @JsonSchemaDescription("Urgent, if stated") urgent: Option[Boolean],
    place: Option[Place],
    @JsonSchemaIgnore internal: Option[Level] = None,
    @JsonSchemaIgnore source: Channel.Value = Channel.email
  )

  // fields the model is never asked for
  case class Change(
    text: Option[String],
    @JsonSchemaIgnore style: Option[String] = None,
    @JsonSchemaIgnore revision: Int = 1,
    @JsonSchemaIgnore author: Option[String]
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
      "nickname" -> JsonSchema.Nullable(JsonSchema.String()),
      "languages" -> JsonSchema.Array(JsonSchema.String())
    ),
    required = Seq(
      "country",
      "capital",
      "populationMil",
      "ratioOfMenToWomen",
      "nickname",
      "languages"
    ),
    description = Some("A country and its capital")
  )

  "jsonSchemaFor" should {

    "derive an object from a case class, with descriptions, ranges and optional fields" in {
      jsonSchemaFor[Country]() shouldBe countrySchema
    }

    "derive an optional field as its type, not required, with nullableOptions = false" in {
      jsonSchemaFor[Country](nullableOptions = false) shouldBe countrySchema.copy(
        properties = countrySchema.properties.map {
          case ("nickname", _) => "nickname" -> JsonSchema.String()
          case other           => other
        },
        required = countrySchema.required.filterNot(_ == "nickname")
      )
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
          // case objects declared in one object: in declaration order
          "sentiment" -> JsonSchema.String(`enum` = Seq("Positive", "Negative", "Mixed")),
          "severity" -> JsonSchema.Nullable(JsonSchema.String(`enum` = Seq("low", "high"))),
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
          "severity",
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

    "derive value classes as their value, maps as open objects, Java collections and more string types" in {
      jsonSchemaFor[Extras]() shouldBe JsonSchema.Object(
        properties = Seq(
          "user" -> JsonSchema.String(),
          "points" -> JsonSchema.Nullable(JsonSchema.Integer()),
          "counts" -> JsonSchema.Object(Nil, Nil, additionalProperties = Some(true)),
          "props" -> JsonSchema.Object(Nil, Nil, additionalProperties = Some(true)),
          "nickname" -> JsonSchema.Nullable(JsonSchema.String()),
          "items" -> JsonSchema.Array(JsonSchema.Integer()),
          "labels" -> JsonSchema.Array(JsonSchema.String()),
          "uri" -> JsonSchema.String(),
          "url" -> JsonSchema.String(),
          "locale" -> JsonSchema.String(),
          "currency" -> JsonSchema.String(),
          "path" -> JsonSchema.String(),
          "file" -> JsonSchema.String(),
          "zone" -> JsonSchema.String(),
          "age" -> JsonSchema.String(),
          "period" -> JsonSchema.String(),
          "timeout" -> JsonSchema.String()
        ),
        required = Seq(
          "user",
          "points",
          "counts",
          "props",
          "nickname",
          "items",
          "labels",
          "uri",
          "url",
          "locale",
          "currency",
          "path",
          "file",
          "zone",
          "age",
          "period",
          "timeout"
        )
      )
    }

    "resolve type parameters" in {
      val schema = jsonSchemaFor[Box[Country]]()

      schema shouldBe JsonSchema.Object(
        Seq(
          "item" -> countrySchema,
          "items" -> JsonSchema.Array(countrySchema),
          "maybe" -> JsonSchema.Nullable(countrySchema)
        ),
        required = Seq("item", "items", "maybe")
      )
    }

    "write the descriptions of an enum's values into the field's description" in {
      val values = Seq("Urgent", "Routine", "Unknown")
      val lines = "- Urgent: Needs a reply today\n- Routine: Can wait a week"

      jsonSchemaFor[Ticket]() shouldBe JsonSchema.Object(
        Seq(
          "priority" -> JsonSchema.String(Some(s"How soon must we reply?\n$lines"), values),
          // a multi-select: the lines go with the field, where each option's question reads them
          "mentioned" -> JsonSchema.Array(
            JsonSchema.String(`enum` = values),
            Some(s"Which priorities were mentioned?\n$lines")
          ),
          "previous" -> JsonSchema.Nullable(JsonSchema.String(Some(lines), values))
        ),
        required = Seq("priority", "mentioned", "previous")
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
        "shade" -> JsonSchema.Nullable(JsonSchema.String())
      )
      schema.required shouldBe Seq("color", "shade")
    }

    "take an enum's values as its enumFormat writes them" in {
      val properties =
        jsonSchemaFor[LibraryEnums]().asInstanceOf[JsonSchema.Object].properties.toMap

      // as sets - the order is the declaration order
      def values(schema: JsonSchema): Seq[String] = schema match {
        case JsonSchema.String(_, values)                      => values.sorted
        case JsonSchema.Array(JsonSchema.String(_, values), _) => values.sorted
        case JsonSchema.Nullable(inner)                        => values(inner)
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

    "carry the annotations on every kind of field - plain, optional, in collections" in {
      val levels = Seq("high", "medium", "low")
      val lines = "- high: Needs action now\n- low: Can wait"
      val channels = Seq("email", "phone", "chat")
      val units = java.util.concurrent.TimeUnit.values().toSeq.map(_.name)

      def orNull(
        schema: JsonSchema,
        description: String
      ) = JsonSchema.AnyOf(Seq(schema, JsonSchema.Null()), Some(description))

      val schema = jsonSchemaFor[Incident]()

      schema shouldBe JsonSchema.Object(
        Seq(
          "level" -> JsonSchema.String(Some(s"How bad\n$lines"), levels),
          // a nullable field's description - the value lines too - on the union
          "escalation" -> orNull(
            JsonSchema.String(`enum` = levels),
            s"The level it may escalate to\n$lines"
          ),
          "mentioned" -> JsonSchema.Array(
            JsonSchema.String(`enum` = levels),
            Some(s"Levels mentioned\n$lines")
          ),
          "named" -> orNull(
            JsonSchema.Array(JsonSchema.String(`enum` = levels)),
            s"Levels the customer named, if any\n$lines"
          ),
          "channel" -> JsonSchema.String(Some("Contact channel"), channels),
          "preferred" -> orNull(
            JsonSchema.String(`enum` = channels),
            "Preferred channel, if any"
          ),
          "unit" -> JsonSchema.String(Some("Time unit"), units),
          "fallbackUnit" -> orNull(JsonSchema.String(`enum` = units), "Fallback unit, if any"),
          "hours" -> JsonSchema.Integer(Some("Hours to fix"), Some(1), Some(48)),
          "spent" -> orNull(
            JsonSchema.Integer(None, Some(0), Some(100)),
            "Hours spent, if known"
          ),
          "score" -> orNull(JsonSchema.Number(None, Some(0), Some(1)), "Confidence, if known"),
          // a range on a numeric collection goes to its items
          "ratings" -> orNull(
            JsonSchema.Array(JsonSchema.Integer(None, Some(1), Some(5))),
            "Ratings, if any"
          ),
          "urgent" -> orNull(JsonSchema.Boolean(), "Urgent, if stated"),
          // a nested class's own description, on the union
          "place" -> orNull(
            JsonSchema.Object(
              Seq(
                "city" -> JsonSchema.String(Some("The city")),
                "zip" -> orNull(JsonSchema.String(), "Postal code, if known")
              ),
              Seq("city", "zip")
            ),
            "Where it happened"
          )
        ),
        required = Seq(
          "level",
          "escalation",
          "mentioned",
          "named",
          "channel",
          "preferred",
          "unit",
          "fallbackUnit",
          "hours",
          "spent",
          "score",
          "ratings",
          "urgent",
          "place"
        ),
        description = Some("An incident")
      )

      // written and read back unchanged
      Json.toJson(schema).as[JsonSchema] shouldBe schema

      // the old shape keeps the annotations on the field's own schema
      val old =
        jsonSchemaFor[Incident](nullableOptions = false).asInstanceOf[JsonSchema.Object]
      old.properties.toMap.apply("escalation") shouldBe
        JsonSchema.String(Some(s"The level it may escalate to\n$lines"), levels)
      old.properties.toMap.apply("ratings") shouldBe
        JsonSchema.Array(JsonSchema.Integer(None, Some(1), Some(5)), Some("Ratings, if any"))
      old.properties.toMap.apply("place") shouldBe JsonSchema.Object(
        Seq(
          "city" -> JsonSchema.String(Some("The city")),
          "zip" -> JsonSchema.String(Some("Postal code, if known"))
        ),
        Seq("city"),
        description = Some("Where it happened")
      )
      old.required should not contain "escalation"
    }

    "keep the declaration order of a hierarchy declared in objects, sort a package's" in {
      jsonSchemaFor[Moods]() shouldBe JsonSchema.Object(
        Seq(
          "mood" -> JsonSchema.String(`enum` = Seq("Calm", "Sad", "Angry", "Happy")),
          "shade" -> JsonSchema.String(`enum` = Seq("Amber", "Zinc"))
        ),
        required = Seq("mood", "shade")
      )
    }

    "make an explicit schema of an optional field nullable" in {
      val nickname = JsonSchema.String(Some("A short name"), Seq("Ace", "Bee"))
      val schema = jsonSchemaFor[Country](explicitTypes = Map("nickname" -> nickname))

      schema.asInstanceOf[JsonSchema.Object].properties.toMap.apply("nickname") shouldBe
        JsonSchema.Nullable(nickname)
    }

    "leave out the fields marked @JsonSchemaIgnore" in {
      jsonSchemaFor[Change]() shouldBe JsonSchema.Object(
        Seq("text" -> JsonSchema.Nullable(JsonSchema.String())),
        required = Seq("text")
      )
    }

    "write an optional field as anyOf with null, its description on the wrapper" in {
      val json = Json.toJson(jsonSchemaFor[Ticket]())

      (json \ "properties" \ "previous").get shouldBe Json.obj(
        "anyOf" -> Json.arr(
          Json.obj("enum" -> Seq("Urgent", "Routine", "Unknown"), "type" -> "string"),
          Json.obj("type" -> "null")
        ),
        "description" -> "- Urgent: Needs a reply today\n- Routine: Can wait a week"
      )
      (json \ "required").as[Seq[String]] shouldBe Seq("priority", "mentioned", "previous")
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
