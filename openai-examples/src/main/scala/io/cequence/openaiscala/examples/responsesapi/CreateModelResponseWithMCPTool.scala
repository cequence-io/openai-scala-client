package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.tools.Tool
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.{
  MCPApprovalRequest,
  MCPApprovalResponse,
  MCPRequireApproval
}
import io.cequence.openaiscala.domain.responsesapi.{CreateModelResponseSettings, Inputs}
import io.cequence.openaiscala.examples.Example

import scala.concurrent.Future

object CreateModelResponseWithMCPTool extends Example {

  private val model = ModelId.gpt_5_mini

  // NOTE: OpenAI's MCP connector needs streamable-HTTP server URLs (e.g. `.../mcp`); the older
  // SSE endpoints (`.../sse`) are rejected with "424 Failed Dependency".

  // Example 1: DeepWiki MCP Tool
  private val deepwikiMcpTool = Tool.mcp(
    serverLabel = "deepwiki",
    serverUrl = Some(
      "https://mcp.deepwiki.com/mcp"
    ), // streamable HTTP; the /sse endpoint fails with 424
    requireApproval = Some(MCPRequireApproval.Setting.Always)
  )

  // Example 2: Context7 MCP Tool (library documentation lookup; public, no auth)
  private val context7McpTool = Tool.mcp(
    serverLabel = "context7",
    serverUrl = Some("https://mcp.context7.com/mcp"),
    requireApproval = Some(MCPRequireApproval.Setting.Never)
  )

  override def run: Future[Unit] = {
    for {
      // Example 1: Using DeepWiki MCP Server (with approval flow)
      response1Initial <- {
        println("=" * 60)
        println("Example 1: Using DeepWiki MCP Server (with approval)")
        println("=" * 60)

        service.createModelResponse(
          Inputs.Text(
            "Search for information about Scala programming language in scala/scala."
          ),
          settings = CreateModelResponseSettings(
            model = model,
            tools = Seq(deepwikiMcpTool)
          )
        )
      }

      // Check for approval requests in the output
      approvalRequests = response1Initial.output.collect { case req: MCPApprovalRequest =>
        req
      }

      _ = {
        if (approvalRequests.nonEmpty) {
          println(s"Received ${approvalRequests.length} approval request(s)")
          approvalRequests.foreach { req =>
            println(s"  - Tool: ${req.name} on server: ${req.serverLabel}")
            println(s"    Request ID: ${req.id}")
            println(s"    Arguments: ${req.arguments}")
          }
        }
      }

      // Submit approval responses if needed
      response1Final <- {
        if (approvalRequests.nonEmpty) {
          println("Submitting approval responses...")

          val approvalResponses = approvalRequests.map { req =>
            MCPApprovalResponse(
              approvalRequestId = req.id,
              approve = true
              // Note: 'reason' can only be provided when approve=false
            )
          }

          // Submit approval responses by continuing the conversation
          // IMPORTANT: Set previousResponseId to link the approval responses to the original request
          service.createModelResponse(
            Inputs.Items(approvalResponses: _*),
            settings = CreateModelResponseSettings(
              model = model,
              previousResponseId =
                Some(response1Initial.id), // Continue from the request that asked for approval
              tools = Seq(deepwikiMcpTool)
            )
          )
        } else {
          Future.successful(response1Initial)
        }
      }

      _ = {
        response1Final.output.foreach { output =>
          println(output)
        }
      }

      // Example 2: Using Context7 MCP Server (no approval required)
      response2 <- {
        println("=" * 60)
        println("Example 2: Using Context7 MCP Server")
        println("=" * 60)

        service.createModelResponse(
          Inputs.Text(
            "Using context7, look up the Play JSON library and summarise in two sentences how to define a case class Format."
          ),
          settings = CreateModelResponseSettings(
            model = model,
            tools = Seq(context7McpTool)
          )
        )
      }

    } yield {
      response2.output.foreach { output =>
        println(output)
      }

      println("=" * 60)
      println("Example Summary:")
      println("  - Example 1 (DeepWiki): Demonstrated approval flow")
      println("  - Example 2 (Context7): Library documentation lookup")
      println("=" * 60)
    }
  }
}
