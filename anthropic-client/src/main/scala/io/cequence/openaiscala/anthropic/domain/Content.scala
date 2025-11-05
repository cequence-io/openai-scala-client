package io.cequence.openaiscala.anthropic.domain

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock
import io.cequence.openaiscala.domain.HasType
import io.cequence.wsclient.domain.EnumValue
import play.api.libs.json.JsObject

sealed trait Content

sealed trait CacheControl

object CacheControl {
  case class Ephemeral(ttl: Option[CacheTTL] = None) extends CacheControl
}

sealed trait CacheTTL extends EnumValue

object CacheTTL {
  case object `5m` extends CacheTTL
  case object `1h` extends CacheTTL

  def values: Seq[CacheTTL] = Seq(`5m`, `1h`)
}

trait Cacheable {
  def cacheControl: Option[CacheControl]
}

object Content {
  case class SingleString(
    text: String,
    override val cacheControl: Option[CacheControl] = None
  ) extends Content
      with Cacheable

  case class ContentBlocks(blocks: Seq[ContentBlockBase]) extends Content

  case class ContentBlockBase(
    content: ContentBlock,
    override val cacheControl: Option[CacheControl] = None
  ) extends Content
      with Cacheable

  sealed trait ContentBlock extends HasType

  object ContentBlock {
    case class TextBlock(
      text: String,
      citations: Seq[Citation] = Nil
    ) extends ContentBlock {
      val `type`: String = "text"
    }

    case class ThinkingBlock(
      thinking: String,
      signature: String
    ) extends ContentBlock {
      val `type`: String = "thinking"
    }

    case class RedactedThinkingBlock(
      data: String
    ) extends ContentBlock {
      val `type`: String = "redacted_thinking"
    }

    case class ToolUseBlock(
      id: String,
      name: String,
      input: JsObject
    ) extends ContentBlock {
      val `type`: String = "tool_use"
    }

    case class ServerToolUseBlock(
      id: String,
      name: ServerToolName,
      input: JsObject
    ) extends ContentBlock {
      val `type`: String = "server_tool_use"
    }

    case class WebSearchToolResultBlock(
      content: WebSearchToolResultContent,
      toolUseId: String
    ) extends ContentBlock {
      val `type`: String = "web_search_tool_result"
    }

    case class WebFetchToolResultBlock(
      content: WebFetchToolResultContent,
      toolUseId: String
    ) extends ContentBlock {
      val `type`: String = "web_fetch_tool_result"
    }

    case class McpToolUseBlock(
      id: String,
      name: String,
      serverName: String,
      input: JsObject
    ) extends ContentBlock {
      val `type`: String = "mcp_tool_use"
    }

    case class McpToolResultBlock(
      content: McpToolResultContent,
      isError: Boolean,
      toolUseId: String
    ) extends ContentBlock {
      val `type`: String = "mcp_tool_result"
    }

    case class ContainerUploadBlock(
      fileId: String
    ) extends ContentBlock {
      val `type`: String = "container_upload"
    }

    case class CodeExecutionToolResultBlock(
      content: CodeExecutionToolResultContent,
      toolUseId: String
    ) extends ContentBlock {
      val `type`: String = "code_execution_tool_result"
    }

    case class BashCodeExecutionToolResultBlock(
      content: BashCodeExecutionToolResultContent,
      toolUseId: String
    ) extends ContentBlock {
      val `type`: String = "bash_code_execution_tool_result"
    }

    case class TextEditorCodeExecutionToolResultBlock(
      content: TextEditorCodeExecutionToolResultContent,
      toolUseId: String
    ) extends ContentBlock {
      val `type`: String = "text_editor_code_execution_tool_result"
    }

    sealed trait Citation extends HasType {
      def citedText: String
    }

    object Citation {

      /**
       * Citation from a document.
       */
      case class DocumentCitation(
        override val `type`: String,
        citedText: String,
        documentIndex: Int,
        documentTitle: Option[String] = None,
        startCharIndex: Option[Int] = None,
        endCharIndex: Option[Int] = None,
        startBlockIndex: Option[Int] = None,
        endBlockIndex: Option[Int] = None,
        startPageNumber: Option[Int] = None,
        endPageNumber: Option[Int] = None,
        fileId: Option[String] = None
      ) extends Citation

      /**
       * Citation from a web search result.
       */
      case class WebSearchResultLocation(
        citedText: String,
        url: String,
        title: String,
        encryptedIndex: String
      ) extends Citation {
        override val `type`: String = "web_search_result_location"
      }
    }

    case class MediaBlock(
      `type`: String,
      encoding: String,
      mediaType: String,
      data: String,
      title: Option[String] = None, // Document Title
      context: Option[String] = None, // Context about the document that will not be cited from
      citations: Option[Boolean] = None
    ) extends ContentBlock

    case class TextsContentBlock(
      texts: Seq[String],
      title: Option[String] = None, // Document Title
      context: Option[String] = None, // Context about the document that will not be cited from
      citations: Option[Boolean] = None
    ) extends ContentBlock {
      override val `type` = "document"
    }

    // TODO: revisit this
    case class FileDocumentContentBlock(
      fileId: String,
      title: Option[String] = None, // Document Title
      context: Option[String] = None, // Context about the document that will not be cited from
      citations: Option[Boolean] = None
    ) extends ContentBlock {
      override val `type` = "document"
    }

    object MediaBlock {
      def pdf(
        data: String,
        cacheControl: Option[CacheControl] = None,
        title: Option[String] = None,
        context: Option[String] = None,
        citations: Boolean = false
      ): ContentBlockBase =
        ContentBlockBase(
          MediaBlock(
            "document",
            "base64",
            "application/pdf",
            data,
            title,
            context,
            Some(citations)
          ),
          cacheControl
        )

      def txt(
        data: String,
        cacheControl: Option[CacheControl] = None,
        title: Option[String] = None,
        context: Option[String] = None,
        // https://docs.anthropic.com/en/docs/build-with-claude/citations
        citations: Boolean = false
      ): ContentBlockBase =
        ContentBlockBase(
          MediaBlock("document", "text", "text/plain", data, title, context, Some(citations)),
          cacheControl
        )

      def txts(
        contents: Seq[String],
        cacheControl: Option[CacheControl] = None,
        title: Option[String] = None,
        context: Option[String] = None,
        citations: Boolean = false
      ): ContentBlockBase =
        ContentBlockBase(
          TextsContentBlock(contents, title, context, Some(citations)),
          cacheControl
        )

      def image(
        mediaType: String
      )(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase =
        ContentBlockBase(MediaBlock("image", "base64", mediaType, data), cacheControl)

      def jpeg(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/jpeg")(data, cacheControl)

      def png(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/png")(data, cacheControl)

      def gif(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/gif")(data, cacheControl)

      def webp(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/webp")(data, cacheControl)
    }

  }
}

sealed trait ServerToolName extends EnumValue

object ServerToolName {
  case object web_search extends ServerToolName
  case object web_fetch extends ServerToolName
  case object code_execution extends ServerToolName
  case object bash_code_execution extends ServerToolName
  case object text_editor_code_execution extends ServerToolName

  def values: Seq[ServerToolName] = Seq(
    web_search,
    web_fetch,
    code_execution,
    bash_code_execution,
    text_editor_code_execution
  )
}

sealed trait WebSearchToolResultContent

object WebSearchToolResultContent {

  case class Success(
    results: Seq[Item]
  ) extends WebSearchToolResultContent

  case class Error(
    errorCode: WebSearchErrorCode
  ) extends WebSearchToolResultContent
      with HasType {
    val `type`: String = "web_search_tool_result_error"
  }

  case class Item(
    encryptedContent: String,
    pageAge: Option[String],
    title: String,
    url: String
  ) extends HasType {
    val `type`: String = "web_search_result"
  }

  sealed trait WebSearchErrorCode extends EnumValue

  object WebSearchErrorCode {
    case object invalid_tool_input extends WebSearchErrorCode
    case object unavailable extends WebSearchErrorCode
    case object max_uses_exceeded extends WebSearchErrorCode
    case object too_many_requests extends WebSearchErrorCode
    case object query_too_long extends WebSearchErrorCode

    def values: Seq[WebSearchErrorCode] = Seq(
      invalid_tool_input,
      unavailable,
      max_uses_exceeded,
      too_many_requests,
      query_too_long
    )
  }
}

sealed trait WebFetchToolResultContent extends HasType

object WebFetchToolResultContent {

  case class Success(
    content: Document,
    url: String,
    retrievedAt: String
  ) extends WebFetchToolResultContent {
    val `type`: String = "web_fetch_result"
  }

  case class Error(
    errorCode: WebFetchErrorCode
  ) extends WebFetchToolResultContent {
    val `type`: String = "web_fetch_tool_result_error"
  }

  case class Document(
    citations: CitationsFlag,
    source: Source,
    title: String
  ) extends HasType {
    override val `type` = "document"
  }

  case class Source(
    data: String,
    mediaType: String,
    `type`: String // Allowed value: "text" and "base64"
  )

  sealed trait WebFetchErrorCode extends EnumValue

  object WebFetchErrorCode {
    case object invalid_tool_input extends WebFetchErrorCode
    case object url_too_long extends WebFetchErrorCode
    case object url_not_allowed extends WebFetchErrorCode
    case object url_not_accessible extends WebFetchErrorCode
    case object unsupported_content_type extends WebFetchErrorCode
    case object too_many_requests extends WebFetchErrorCode
    case object max_uses_exceeded extends WebFetchErrorCode
    case object unavailable extends WebFetchErrorCode

    def values: Seq[WebFetchErrorCode] = Seq(
      invalid_tool_input,
      url_too_long,
      url_not_allowed,
      url_not_accessible,
      unsupported_content_type,
      too_many_requests,
      max_uses_exceeded,
      unavailable
    )
  }
}

sealed trait McpToolResultContent

case class McpToolResultString(
  value: String
) extends McpToolResultContent

case class McpToolResultStructured(
  results: Seq[MCPToolResultItem] = Nil
) extends McpToolResultContent

case class MCPToolResultItem(
  text: String,
  citations: Seq[ContentBlock.Citation] = Nil
) extends HasType {
  val `type`: String = "text"
}

sealed trait CodeExecutionToolResultContent extends HasType

object CodeExecutionToolResultContent {

  case class Success(
    content: Seq[Item],
    returnCode: Int,
    stderr: String,
    stdout: String
  ) extends CodeExecutionToolResultContent {
    val `type`: String = "code_execution_result"
  }

  case class Item(
    fileId: String
  ) extends HasType {
    val `type`: String = "code_execution_output"
  }

  case class Error(
    errorCode: CodeExecutionErrorCode
  ) extends CodeExecutionToolResultContent {
    val `type`: String = "code_execution_tool_result_error"
  }

  // Code Execution Tool Result types
  sealed trait CodeExecutionErrorCode extends EnumValue

  object CodeExecutionErrorCode {
    case object invalid_tool_input extends CodeExecutionErrorCode
    case object unavailable extends CodeExecutionErrorCode
    case object too_many_requests extends CodeExecutionErrorCode
    case object execution_time_exceeded extends CodeExecutionErrorCode

    def values: Seq[CodeExecutionErrorCode] = Seq(
      invalid_tool_input,
      unavailable,
      too_many_requests,
      execution_time_exceeded
    )
  }
}

sealed trait BashCodeExecutionToolResultContent extends HasType

object BashCodeExecutionToolResultContent {

  case class Success(
    content: Seq[Item],
    returnCode: Int,
    stderr: String,
    stdout: String
  ) extends BashCodeExecutionToolResultContent {
    val `type`: String = "bash_code_execution_result"
  }

  case class Item(
    fileId: String
  ) extends HasType {
    val `type`: String = "bash_code_execution_output"
  }

  case class Error(
    errorCode: BashCodeExecutionErrorCode
  ) extends BashCodeExecutionToolResultContent {
    val `type`: String = "bash_code_execution_tool_result_error"
  }

  // Bash Code Execution Tool Result types
  sealed trait BashCodeExecutionErrorCode extends EnumValue

  object BashCodeExecutionErrorCode {
    case object invalid_tool_input extends BashCodeExecutionErrorCode
    case object unavailable extends BashCodeExecutionErrorCode
    case object too_many_requests extends BashCodeExecutionErrorCode
    case object execution_time_exceeded extends BashCodeExecutionErrorCode
    case object output_file_too_large extends BashCodeExecutionErrorCode

    def values: Seq[BashCodeExecutionErrorCode] = Seq(
      invalid_tool_input,
      unavailable,
      too_many_requests,
      execution_time_exceeded,
      output_file_too_large
    )
  }
}

sealed trait TextEditorCodeExecutionToolResultContent extends HasType

object TextEditorCodeExecutionToolResultContent {

  case class Error(
    errorCode: TextEditorCodeExecutionErrorCode,
    errorMessage: Option[String]
  ) extends TextEditorCodeExecutionToolResultContent {
    val `type`: String = "text_editor_code_execution_tool_result_error"
  }

  case class ViewResult(
    content: String,
    fileType: FileType,
    numLines: Option[Int],
    startLine: Option[Int],
    totalLines: Option[Int]
  ) extends TextEditorCodeExecutionToolResultContent {
    val `type`: String = "text_editor_code_execution_view_result"
  }

  case class CreateResult(
    isFileUpdate: Boolean
  ) extends TextEditorCodeExecutionToolResultContent {
    val `type`: String = "text_editor_code_execution_create_result"
  }

  case class ReplaceResult(
    lines: Seq[String] = Nil,
    newLines: Option[Int] = None,
    newStart: Option[Int] = None,
    oldLines: Option[Int] = None,
    oldStart: Option[Int] = None
  ) extends TextEditorCodeExecutionToolResultContent {
    val `type`: String = "text_editor_code_execution_str_replace_result"
  }

  sealed trait TextEditorCodeExecutionErrorCode extends EnumValue

  object TextEditorCodeExecutionErrorCode {
    case object invalid_tool_input extends TextEditorCodeExecutionErrorCode
    case object unavailable extends TextEditorCodeExecutionErrorCode
    case object too_many_requests extends TextEditorCodeExecutionErrorCode
    case object execution_time_exceeded extends TextEditorCodeExecutionErrorCode
    case object file_not_found extends TextEditorCodeExecutionErrorCode

    def values: Seq[TextEditorCodeExecutionErrorCode] = Seq(
      invalid_tool_input,
      unavailable,
      too_many_requests,
      execution_time_exceeded,
      file_not_found
    )
  }

  sealed trait FileType extends EnumValue

  object FileType {
    case object text extends FileType
    case object image extends FileType
    case object pdf extends FileType

    def values: Seq[FileType] = Seq(text, image, pdf)
  }
}
