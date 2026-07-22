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

  /**
   * Remote MCP servers the Gemini backend connects to directly and whose tools it can invoke
   * server-side (no client-side tool loop needed). Only streamable-HTTP transport is
   * supported. Note: custom HTTP headers (e.g. auth) can be passed via the transport.
   */
  case class McpServers(
    mcpServers: Seq[McpServer]
  ) extends Tool {
    override val prefix: ToolPrefix = ToolPrefix.mcpServers
  }
}

sealed trait ToolPrefix extends EnumValue

object ToolPrefix {
  case object functionDeclarations extends ToolPrefix
  case object googleSearchRetrieval extends ToolPrefix
  case object codeExecution extends ToolPrefix
  case object googleSearch extends ToolPrefix
  case object mcpServers extends ToolPrefix

  def values: Seq[ToolPrefix] = Seq(
    functionDeclarations,
    googleSearchRetrieval,
    codeExecution,
    googleSearch,
    mcpServers
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
 * A remote MCP server made available to the model - the Gemini backend connects to it and
 * invokes its tools server-side.
 *
 * @param name
 *   The name of the MCP server.
 * @param streamableHttpTransport
 *   Streamable HTTP transport configuration (the only transport currently supported).
 */
case class McpServer(
  name: String,
  streamableHttpTransport: StreamableHttpTransport
)

/**
 * Streamable HTTP transport configuration for a remote MCP server.
 *
 * @param url
 *   Required. The URL of the MCP server.
 * @param headers
 *   Optional. Custom HTTP headers to send to the MCP server (e.g. x-api-key for auth).
 * @param timeout
 *   Optional. Request timeout as a Duration string (e.g. "30s").
 * @param sseReadTimeout
 *   Optional. SSE read timeout as a Duration string (e.g. "120s").
 * @param terminateOnClose
 *   Optional. Whether to terminate the MCP session when the connection closes.
 */
case class StreamableHttpTransport(
  url: String,
  headers: Option[Map[String, String]] = None,
  timeout: Option[String] = None,
  sseReadTimeout: Option[String] = None,
  terminateOnClose: Option[Boolean] = None
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
