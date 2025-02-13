package io.cequence.openaiscala.gemini.domain

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.EnumValue

sealed trait Tool {
  val prefix: ToolPrefix
}

object Tool {

  case class FunctionDeclarations(
    functionDeclarations: Seq[FunctionDeclaration]
  ) extends Tool {
    override val prefix: ToolPrefix = ToolPrefix.functionDeclarations
  }

  /**
   * @param dynamicRetrievalConfig
   *   Specifies the dynamic retrieval configuration for the given source.
   */
  case class GoogleSearchRetrieval(
    dynamicRetrievalConfig: DynamicRetrievalConfig
  ) extends Tool {
    override val prefix: ToolPrefix = ToolPrefix.googleSearchRetrieval
  }

  // no fields
  case object CodeExecution extends Tool {
    override val prefix: ToolPrefix = ToolPrefix.codeExecution
  }

  // no fields
  case object GoogleSearch extends Tool {
    override val prefix: ToolPrefix = ToolPrefix.googleSearch
  }
}

sealed trait ToolPrefix extends EnumValue

object ToolPrefix {
  case object functionDeclarations extends ToolPrefix
  case object googleSearchRetrieval extends ToolPrefix
  case object codeExecution extends ToolPrefix
  case object googleSearch extends ToolPrefix

  def values: Seq[ToolPrefix] = Seq(
    functionDeclarations,
    googleSearchRetrieval,
    codeExecution,
    googleSearch
  )

  def of(value: String): ToolPrefix = values.find(_.toString() == value).getOrElse {
    throw new OpenAIScalaClientException(s"Unknown partPrefix: $value")
  }
}

/**
 * Structured representation of a function declaration as defined by the OpenAPI 3.03
 * specification. Included in this declaration are the function name and parameters. This
 * FunctionDeclaration is a representation of a block of code that can be used as a Tool by the
 * model and executed by the client.
 *
 * @param name
 *   Required. The name of the function. Must be a-z, A-Z, 0-9, or contain underscores and
 *   dashes, with a maximum length of 63.
 * @param description
 *   Required. A brief description of the function.
 * @param parameters
 *   Optional. Describes the parameters to this function. Reflects the Open API 3.03 Parameter
 *   Object string Key: the name of the parameter. Parameter names are case sensitive. Schema
 *   Value: the Schema defining the type used for the parameter.
 * @param response
 *   Optional. Describes the output from this function in JSON Schema format. Reflects the Open
 *   API 3.03 Response Object. The Schema defines the type used for the response value of the
 *   function.
 */
case class FunctionDeclaration(
  name: String,
  description: String,
  parameters: Option[Schema] = None,
  response: Option[Schema] = None
)

/**
 * The Schema object allows the definition of input and output data types. These types can be
 * objects, but also primitives and arrays. Represents a select subset of an OpenAPI 3.0 schema
 * object.
 *
 * @param `type`
 *   Required. Data type.
 * @param format
 *   Optional. The format of the data. This is used only for primitive datatypes. Supported
 *   formats: for NUMBER type: float, double for INTEGER type: int32, int64 for STRING type:
 *   enum
 * @param description
 *   Optional. A brief description of the parameter. This could contain examples of use.
 *   Parameter description may be formatted as Markdown.
 * @param nullable
 *   Optional. Indicates if the value may be null.
 * @param enum
 *   Optional. Possible values of the element of Type.STRING with enum format. For example we
 *   can define an Enum Direction as : {type:STRING, format:enum, enum:["EAST", NORTH",
 *   "SOUTH", "WEST"]}
 * @param maxItems
 *   Optional. Maximum number of the elements for Type.ARRAY.
 * @param minItems
 *   Optional. Minimum number of the elements for Type.ARRAY.
 * @param properties
 *   Optional. Properties of Type.OBJECT. An object containing a list of "key": value pairs.
 * @param required
 *   Optional. Required properties of Type.OBJECT.
 * @param propertyOrdering
 *   Optional. The order of the properties. Not a standard field in open api spec. Used to
 *   determine the order of the properties in the response.
 * @param items
 *   Optional. Schema of the elements of Type.ARRAY.
 */
case class Schema(
  `type`: SchemaType,
  format: Option[String] = None,
  description: Option[String] = None,
  nullable: Option[Boolean] = None,
  `enum`: Option[Seq[String]] = None,
  maxItems: Option[String] = None,
  minItems: Option[String] = None,
  properties: Option[Map[String, Schema]] = None,
  required: Option[Seq[String]] = None,
  propertyOrdering: Option[Seq[String]] = None,
  items: Option[Schema] = None
)

/**
 * Describes the options to customize dynamic retrieval.
 *
 * @param mode
 *   The mode of the predictor to be used in dynamic retrieval.
 * @param dynamicThreshold
 *   The threshold to be used in dynamic retrieval. If not set, a system default value is used.
 */
case class DynamicRetrievalConfig(
  mode: DynamicRetrievalPredictorMode,
  dynamicThreshold: Int // TODO: check if not double
)

sealed trait DynamicRetrievalPredictorMode extends EnumValue

object DynamicRetrievalPredictorMode {
  case object MODE_UNSPECIFIED extends DynamicRetrievalPredictorMode
  case object MODE_DYNAMIC extends DynamicRetrievalPredictorMode

  def values: Seq[DynamicRetrievalPredictorMode] = Seq(
    MODE_UNSPECIFIED,
    MODE_DYNAMIC
  )
}

sealed trait SchemaType extends EnumValue

object SchemaType {
  case object TYPE_UNSPECIFIED extends SchemaType
  case object STRING extends SchemaType
  case object NUMBER extends SchemaType
  case object INTEGER extends SchemaType
  case object BOOLEAN extends SchemaType
  case object ARRAY extends SchemaType
  case object OBJECT extends SchemaType

  def values: Seq[SchemaType] = Seq(
    TYPE_UNSPECIFIED,
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT
  )
}
