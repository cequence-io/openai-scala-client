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

  // Example 1: DeepWiki MCP Tool
  private val deepwikiMcpTool = Tool.mcp(
    serverLabel = "deepwiki",
    serverUrl = Some("https://mcp.deepwiki.com/sse"),
    requireApproval = Some(MCPRequireApproval.Setting.Always)
  )

  // Example 2: Semgrep MCP Tool
  private val semgrepMcpTool = Tool.mcp(
    serverLabel = "semgrep",
    serverUrl = Some("https://mcp.semgrep.ai/mcp"), // sse
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

      // Example 2: Using Semgrep MCP Server (no approval required)
      response2 <- {
        println("=" * 60)
        println("Example 2: Using Semgrep MCP Server")
        println("=" * 60)

        service.createModelResponse(
          Inputs.Text(
            "Analyze this code for security vulnerabilities: def unsafe(input: String) = s\"SELECT * FROM users WHERE name = '$input'\""
          ),
          settings = CreateModelResponseSettings(
            model = model,
            tools = Seq(semgrepMcpTool)
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
      println("  - Example 2 (Semgrep): No approval required")
      println("=" * 60)
    }
  }
}
