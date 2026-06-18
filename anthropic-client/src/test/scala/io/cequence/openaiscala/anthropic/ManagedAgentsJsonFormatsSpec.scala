package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.managedagents._
import io.cequence.openaiscala.anthropic.domain.settings.Speed
import io.cequence.openaiscala.anthropic.domain.skills.{SkillParams, SkillSource}
import io.cequence.openaiscala.anthropic.domain.tools.CustomTool
import io.cequence.openaiscala.domain.JsonSchema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

class ManagedAgentsJsonFormatsSpec extends AnyWordSpecLike with Matchers with JsonFormats {

  "Managed Agents JSON formats" should {

    "serialize/deserialize PermissionPolicy as a typed object" in {
      testCodec[PermissionPolicy](PermissionPolicy.always_ask, """{"type":"always_ask"}""")
      testCodec[PermissionPolicy](PermissionPolicy.always_allow, """{"type":"always_allow"}""")
    }

    "serialize/deserialize AgentToolConfig omitting empty fields" in {
      testCodec[AgentToolConfig](
        AgentToolConfig(
          name = Some("bash"),
          enabled = Some(false),
          permissionPolicy = Some(PermissionPolicy.always_ask)
        ),
        """{"name":"bash","enabled":false,"permission_policy":{"type":"always_ask"}}"""
      )
      testCodec[AgentToolConfig](AgentToolConfig(), "{}")
    }

    "serialize/deserialize AgentTool.Toolset" in {
      testCodec[AgentTool](
        AgentTool.Toolset(
          configs = Seq(AgentToolConfig(name = Some("bash"), enabled = Some(false))),
          defaultConfig = Some(
            AgentToolConfig(
              enabled = Some(true),
              permissionPolicy = Some(PermissionPolicy.always_allow)
            )
          )
        ),
        """{"type":"agent_toolset_20260401",""" +
          """"configs":[{"name":"bash","enabled":false}],""" +
          """"default_config":{"enabled":true,"permission_policy":{"type":"always_allow"}}}"""
      )
    }

    "serialize/deserialize AgentTool.McpToolset (minimal)" in {
      testCodec[AgentTool](
        AgentTool.McpToolset("github"),
        """{"type":"mcp_toolset","mcp_server_name":"github"}"""
      )
    }

    "round-trip AgentTool.Custom (reuses CustomTool)" in {
      roundTrip[AgentTool](
        AgentTool.Custom(
          CustomTool(
            name = "calculator",
            inputSchema = JsonSchema.Object(
              properties = Seq("x" -> JsonSchema.Number()),
              required = Seq("x")
            ),
            description = Some("Adds numbers")
          )
        )
      )
      // wire shape sanity: type discriminator present
      (Json.toJson(
        AgentTool.Custom(CustomTool("c", JsonSchema.Object(Nil), None)): AgentTool
      ) \ "type").as[String] shouldBe "custom"
    }

    "serialize/deserialize AgentModelConfig as string when no speed" in {
      testCodec[AgentModelConfig](AgentModelConfig("claude-fable-5"), "\"claude-fable-5\"")
    }

    "serialize/deserialize AgentModelConfig as object when speed is set" in {
      testCodec[AgentModelConfig](
        AgentModelConfig("claude-opus-4-6", Some(Speed.fast)),
        """{"id":"claude-opus-4-6","speed":"fast"}"""
      )
    }

    "serialize/deserialize multiagent members and coordinator" in {
      testCodec[MultiagentMember](
        MultiagentMember.AgentRef("agent_123", Some(4)),
        """{"type":"agent","id":"agent_123","version":4}"""
      )
      testCodec[MultiagentMember](MultiagentMember.SelfRef, """{"type":"self"}""")
      testCodec[Multiagent](
        Multiagent(Seq(MultiagentMember.AgentRef("agent_123"), MultiagentMember.SelfRef)),
        """{"type":"coordinator","agents":[{"type":"agent","id":"agent_123"},{"type":"self"}]}"""
      )
    }

    "deserialize a full Agent response (every section)" in {
      val json =
        """{
          |  "type": "agent",
          |  "id": "agent_011CZkYpogX7uDKUyvBTophP",
          |  "name": "My Agent",
          |  "description": "A general-purpose agent",
          |  "version": 2,
          |  "model": { "id": "claude-fable-5", "speed": "standard" },
          |  "system": "You are helpful.",
          |  "tools": [
          |    { "type": "agent_toolset_20260401" },
          |    { "type": "mcp_toolset", "mcp_server_name": "github" },
          |    { "type": "custom", "name": "calc", "description": "d",
          |      "input_schema": { "type": "object", "properties": {}, "required": [] } }
          |  ],
          |  "mcp_servers": [ { "type": "url", "name": "github", "url": "https://mcp.example/sse" } ],
          |  "skills": [ { "type": "anthropic", "skill_id": "xlsx", "version": "latest" } ],
          |  "metadata": { "env": "prod" },
          |  "multiagent": { "type": "coordinator", "agents": [ { "type": "self" } ] },
          |  "created_at": "2026-06-18T00:00:00Z",
          |  "updated_at": "2026-06-18T01:00:00Z",
          |  "archived_at": null
          |}""".stripMargin

      val agent = Json.parse(json).as[Agent]
      agent.id shouldBe "agent_011CZkYpogX7uDKUyvBTophP"
      agent.version shouldBe 2
      agent.model shouldBe AgentModelConfig("claude-fable-5", Some(Speed.standard))
      agent.tools should have size 3
      agent.tools.collect { case t: AgentTool.McpToolset => t.mcpServerName } shouldBe Seq(
        "github"
      )
      agent.mcpServers.map(_.name) shouldBe Seq("github")
      agent.skills shouldBe Seq(SkillParams("xlsx", SkillSource.anthropic, Some("latest")))
      agent.metadata shouldBe Map("env" -> "prod")
      agent.multiagent.map(_.agents) shouldBe Some(Seq(MultiagentMember.SelfRef))
      agent.archivedAt shouldBe None

      // round-trips back to an equal object
      Json.parse(Json.toJson(agent).toString()).as[Agent] shouldBe agent
    }

    "deserialize a paged Agent list" in {
      val json =
        """{"data":[{"type":"agent","id":"a1","name":"n","version":1,""" +
          """"model":{"id":"claude-fable-5","speed":"standard"}}],"next_page":"pg_2"}"""
      val page = Json.parse(json).as[PagedResponse[Agent]]
      page.data.map(_.id) shouldBe Seq("a1")
      page.nextPage shouldBe Some("pg_2")
    }

    // --- Environments ---

    "serialize/deserialize networking (unrestricted and limited)" in {
      testCodec[Networking](Networking.Unrestricted, """{"type":"unrestricted"}""")
      testCodec[Networking](
        Networking.Limited(
          allowMcpServers = Some(false),
          allowPackageManagers = Some(true),
          allowedHosts = Seq("api.example.com")
        ),
        """{"type":"limited","allow_mcp_servers":false,""" +
          """"allow_package_managers":true,"allowed_hosts":["api.example.com"]}"""
      )
    }

    "serialize/deserialize a cloud environment config with packages" in {
      testCodec[EnvironmentConfig](
        EnvironmentConfig.Cloud(
          networking = Some(Networking.Unrestricted),
          packages = Some(Packages(pip = Seq("pandas", "numpy")))
        ),
        """{"type":"cloud","networking":{"type":"unrestricted"},""" +
          """"packages":{"type":"packages","pip":["pandas","numpy"]}}"""
      )
    }

    "serialize/deserialize a self-hosted environment config" in {
      testCodec[EnvironmentConfig](EnvironmentConfig.SelfHosted, """{"type":"self_hosted"}""")
    }

    "serialize/deserialize the environment delete response" in {
      testCodec[EnvironmentDeleteResponse](
        EnvironmentDeleteResponse("env_1"),
        """{"id":"env_1","type":"environment_deleted"}"""
      )
    }

    "deserialize a full Environment response" in {
      val json =
        """{
          |  "id": "env_011CZkZ9X2dpNyB7HsEFoRfW",
          |  "archived_at": null,
          |  "config": {
          |    "networking": { "type": "limited", "allow_mcp_servers": false,
          |      "allow_package_managers": true, "allowed_hosts": ["api.example.com"] },
          |    "packages": { "type": "packages", "pip": ["pandas", "numpy"] },
          |    "type": "cloud"
          |  },
          |  "created_at": "2026-03-15T10:00:00Z",
          |  "description": "Python data-analysis env.",
          |  "metadata": { "team": "ds" },
          |  "name": "python-data-analysis",
          |  "type": "environment",
          |  "updated_at": "2026-03-15T10:00:00Z",
          |  "scope": "organization"
          |}""".stripMargin

      val env = Json.parse(json).as[Environment]
      env.id shouldBe "env_011CZkZ9X2dpNyB7HsEFoRfW"
      env.name shouldBe "python-data-analysis"
      env.scope shouldBe Some(EnvironmentScope.organization)
      env.archivedAt shouldBe None
      env.config match {
        case EnvironmentConfig.Cloud(Some(n: Networking.Limited), Some(p)) =>
          n.allowPackageManagers shouldBe Some(true)
          n.allowedHosts shouldBe Seq("api.example.com")
          p.pip shouldBe Seq("pandas", "numpy")
        case other => fail(s"Unexpected config: $other")
      }
      Json.parse(Json.toJson(env).toString()).as[Environment] shouldBe env
    }

    // --- Environment work (self-hosted) ---

    "deserialize a self-hosted work item" in {
      val json =
        """{"type":"work","id":"work_1","data":{"id":"sesn_1","type":"session"},""" +
          """"environment_id":"env_1","state":"active","metadata":{"k":"v"}}"""
      val work = Json.parse(json).as[SelfHostedWork]
      work.id shouldBe "work_1"
      work.data.id shouldBe "sesn_1"
      work.state shouldBe WorkState.active
      work.metadata shouldBe Map("k" -> "v")
      Json.parse(Json.toJson(work).toString()).as[SelfHostedWork] shouldBe work
    }

    "serialize/deserialize a work heartbeat response" in {
      testCodec[WorkHeartbeatResponse](
        WorkHeartbeatResponse(
          lastHeartbeat = "2026-06-18T00:00:00Z",
          leaseExtended = true,
          state = WorkState.active,
          ttlSeconds = 30
        ),
        """{"type":"work_heartbeat","last_heartbeat":"2026-06-18T00:00:00Z",""" +
          """"lease_extended":true,"state":"active","ttl_seconds":30}"""
      )
    }

    "serialize/deserialize work-queue stats" in {
      testCodec[WorkQueueStats](
        WorkQueueStats(depth = 2, pending = 1, workersPolling = 3, oldestQueuedAt = Some("t")),
        """{"type":"work_queue_stats","depth":2,"pending":1,""" +
          """"workers_polling":3,"oldest_queued_at":"t"}"""
      )
    }
  }

  private def testCodec[A](
    value: A,
    json: String
  )(
    implicit format: Format[A]
  ): Unit = {
    Json.toJson(value).toString() shouldBe json
    Json.parse(json).as[A] shouldBe value
  }

  private def roundTrip[A](
    value: A
  )(
    implicit format: Format[A]
  ): Unit =
    Json.parse(Json.toJson(value).toString()).as[A] shouldBe value
}
