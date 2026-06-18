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

    // --- Sessions ---

    "serialize the five send-event shapes" in {
      Json.toJson(SessionEvent.UserMessage("hi"): SessionEvent) shouldBe Json.parse(
        """{"type":"user.message","content":[{"type":"text","text":"hi"}]}"""
      )
      Json.toJson(SessionEvent.UserInterrupt: SessionEvent) shouldBe Json.parse(
        """{"type":"user.interrupt"}"""
      )
      Json.toJson(
        SessionEvent.UserToolConfirmation(
          "sevt_1",
          allow = false,
          denyMessage = Some("no")
        ): SessionEvent
      ) shouldBe Json.parse(
        """{"type":"user.tool_confirmation","tool_use_id":"sevt_1","result":"deny","deny_message":"no"}"""
      )
      Json.toJson(
        SessionEvent
          .UserCustomToolResult("sevt_2", "done", isError = Some(false)): SessionEvent
      ) shouldBe Json.parse(
        """{"type":"user.custom_tool_result","custom_tool_use_id":"sevt_2",""" +
          """"content":[{"type":"text","text":"done"}],"is_error":false}"""
      )
      Json.toJson(
        SessionEvent.UserDefineOutcome(
          "build it",
          OutcomeRubric.Text("must compile"),
          maxIterations = Some(5)
        ): SessionEvent
      ) shouldBe Json.parse(
        """{"type":"user.define_outcome","description":"build it",""" +
          """"rubric":{"type":"text","content":"must compile"},"max_iterations":5}"""
      )
    }

    "serialize/deserialize session resources (file, github, memory_store)" in {
      testCodec[SessionResource](
        SessionResource.File(fileId = "file_1", mountPath = Some("/workspace/d.csv")),
        """{"type":"file","file_id":"file_1","mount_path":"/workspace/d.csv"}"""
      )
      testCodec[SessionResource](
        SessionResource.GithubRepository(
          url = "https://github.com/o/r",
          authorizationToken = Some("ghp_x"),
          checkout = Some(Checkout.Branch("main"))
        ),
        """{"type":"github_repository","url":"https://github.com/o/r",""" +
          """"authorization_token":"ghp_x","checkout":{"type":"branch","name":"main"}}"""
      )
      testCodec[SessionResource](
        SessionResource.MemoryStore(
          memoryStoreId = "memstore_1",
          access = Some(MemoryStoreAccess.read_only),
          instructions = Some("check first")
        ),
        """{"type":"memory_store","memory_store_id":"memstore_1",""" +
          """"access":"read_only","instructions":"check first"}"""
      )
    }

    "deserialize a session event envelope keeping the raw payload" in {
      val json =
        """{"type":"agent.message","id":"sevt_9","processed_at":"2026-06-18T00:00:00Z",""" +
          """"content":[{"type":"text","text":"hello"}]}"""
      val ev = Json.parse(json).as[SessionEventEnvelope]
      ev.`type` shouldBe "agent.message"
      ev.id shouldBe Some("sevt_9")
      ev.processedAt shouldBe Some("2026-06-18T00:00:00Z")
      (ev.raw \ "content" \ 0 \ "text").as[String] shouldBe "hello"
    }

    "deserialize a full Session response" in {
      val json =
        """{
          |  "id": "sesn_1", "type": "session", "status": "idle",
          |  "environment_id": "env_1",
          |  "agent": { "type": "agent", "id": "agent_1", "name": "a", "version": 1,
          |    "model": { "id": "claude-fable-5", "speed": "standard" } },
          |  "title": "t",
          |  "resources": [ { "id": "res_1", "type": "file", "file_id": "file_1",
          |    "mount_path": "/workspace/d.csv" } ],
          |  "vault_ids": ["vlt_1"],
          |  "created_at": "2026-06-18T00:00:00Z"
          |}""".stripMargin
      val s = Json.parse(json).as[Session]
      s.id shouldBe "sesn_1"
      s.status shouldBe SessionStatus.idle
      s.agent.id shouldBe "agent_1"
      s.resources.collect { case f: SessionResource.File => f.fileId } shouldBe Seq("file_1")
      s.vaultIds shouldBe Seq("vlt_1")
      Json.parse(Json.toJson(s).toString()).as[Session] shouldBe s
    }

    "deserialize a session thread" in {
      val json =
        """{"type":"thread","id":"thread_1","status":"running","session_id":"sesn_1",""" +
          """"agent_id":"agent_1"}"""
      val t = Json.parse(json).as[SessionThread]
      t.id shouldBe "thread_1"
      t.status shouldBe SessionThreadStatus.running
      Json.parse(Json.toJson(t).toString()).as[SessionThread] shouldBe t
    }

    // --- Deployments ---

    "serialize/deserialize an agent reference" in {
      testCodec[AgentReference](
        AgentReference("agent_1", Some(3)),
        """{"type":"agent","id":"agent_1","version":3}"""
      )
    }

    "serialize/deserialize a cron schedule" in {
      testCodec[Schedule](
        Schedule(expression = "0 9 * * 1", timezone = "America/New_York"),
        """{"type":"cron","expression":"0 9 * * 1","timezone":"America/New_York"}"""
      )
    }

    "serialize/deserialize deployment initial events" in {
      testCodec[DeploymentInitialEvent](
        DeploymentInitialEvent.UserMessage("run the report"),
        """{"type":"user.message","content":[{"type":"text","text":"run the report"}]}"""
      )
      testCodec[DeploymentInitialEvent](
        DeploymentInitialEvent
          .UserDefineOutcome("ship it", OutcomeRubric.File("file_1"), Some(3)),
        """{"type":"user.define_outcome","description":"ship it",""" +
          """"rubric":{"type":"file","file_id":"file_1"},"max_iterations":3}"""
      )
    }

    "deserialize a full Deployment response" in {
      val json =
        """{
          |  "id": "deploy_1", "type": "deployment", "name": "nightly",
          |  "agent": { "type": "agent", "id": "agent_1", "version": 2 },
          |  "environment_id": "env_1", "status": "paused",
          |  "initial_events": [ { "type": "user.message",
          |    "content": [ { "type": "text", "text": "go" } ] } ],
          |  "schedule": { "type": "cron", "expression": "0 0 * * *", "timezone": "UTC",
          |    "upcoming_runs_at": ["2026-06-19T00:00:00Z"] },
          |  "paused_reason": { "type": "error", "error": { "type": "vault_not_found_error" } },
          |  "vault_ids": ["vlt_1"],
          |  "created_at": "2026-06-18T00:00:00Z"
          |}""".stripMargin
      val d = Json.parse(json).as[Deployment]
      d.id shouldBe "deploy_1"
      d.agent shouldBe AgentReference("agent_1", Some(2))
      d.status shouldBe DeploymentStatus.paused
      d.schedule.map(_.expression) shouldBe Some("0 0 * * *")
      d.schedule.map(_.upcomingRunsAt) shouldBe Some(Seq("2026-06-19T00:00:00Z"))
      d.pausedReason shouldBe Some(DeploymentPausedReason.Error("vault_not_found_error"))
      d.initialEvents shouldBe Seq(DeploymentInitialEvent.UserMessage("go"))
      Json.parse(Json.toJson(d).toString()).as[Deployment] shouldBe d
    }

    "deserialize a deployment run keeping the raw payload" in {
      val json =
        """{"id":"deplrun_1","deployment_id":"deploy_1","session_id":"sesn_1",""" +
          """"status":"running","created_at":"2026-06-18T00:00:00Z","extra":42}"""
      val r = Json.parse(json).as[DeploymentRun]
      r.id shouldBe Some("deplrun_1")
      r.deploymentId shouldBe Some("deploy_1")
      r.sessionId shouldBe Some("sesn_1")
      r.status shouldBe Some("running")
      (r.raw \ "extra").as[Int] shouldBe 42
    }

    // --- Vaults ---

    "serialize/deserialize a vault" in {
      testCodec[Vault](
        Vault(id = "vlt_1", displayName = "My vault", metadata = Map("env" -> "prod")),
        """{"type":"vault","id":"vlt_1","display_name":"My vault","metadata":{"env":"prod"}}"""
      )
    }

    // --- Credentials (write-only auth) ---

    "serialize credential auth variants (write-only)" in {
      Json.toJson(
        CredentialAuth.StaticBearer("tok", "https://mcp.example/sse"): CredentialAuth
      ) shouldBe Json.parse(
        """{"type":"static_bearer","token":"tok","mcp_server_url":"https://mcp.example/sse"}"""
      )
      Json.toJson(
        CredentialAuth.McpOAuth(
          accessToken = "at",
          mcpServerUrl = "https://mcp.example/sse",
          refresh = Some(
            McpOAuthRefresh(
              clientId = "cid",
              refreshToken = "rt",
              tokenEndpoint = "https://auth.example/token",
              tokenEndpointAuth = TokenEndpointAuth.ClientSecretBasic("sec")
            )
          )
        ): CredentialAuth
      ) shouldBe Json.parse(
        """{"type":"mcp_oauth","access_token":"at","mcp_server_url":"https://mcp.example/sse",""" +
          """"refresh":{"client_id":"cid","refresh_token":"rt",""" +
          """"token_endpoint":"https://auth.example/token",""" +
          """"token_endpoint_auth":{"type":"client_secret_basic","client_secret":"sec"}}}"""
      )
      Json.toJson(
        CredentialAuth.EnvironmentVariable(
          secretName = "API_KEY",
          secretValue = "xyz",
          networking = CredentialNetworking.Limited(Seq("api.example.com"))
        ): CredentialAuth
      ) shouldBe Json.parse(
        """{"type":"environment_variable","secret_name":"API_KEY","secret_value":"xyz",""" +
          """"networking":{"type":"limited","allowed_hosts":["api.example.com"]}}"""
      )
    }

    "deserialize a credential response (secrets omitted)" in {
      val json =
        """{"id":"cred_1","display_name":"My cred","created_at":"2026-06-18T00:00:00Z",""" +
          """"auth":{"type":"static_bearer","mcp_server_url":"https://mcp.example/sse"}}"""
      val c = Json.parse(json).as[Credential]
      c.id shouldBe "cred_1"
      c.authType shouldBe "static_bearer"
      c.mcpServerUrl shouldBe Some("https://mcp.example/sse")
      c.displayName shouldBe Some("My cred")
    }

    // --- Memory stores ---

    "serialize/deserialize a memory store" in {
      testCodec[MemoryStore](
        MemoryStore(id = "memstore_1", name = "prefs", description = Some("user prefs")),
        """{"type":"memory_store","id":"memstore_1","name":"prefs","description":"user prefs"}"""
      )
    }

    "deserialize a memory (basic view → content null)" in {
      val json =
        """{"type":"memory","id":"mem_1","path":"/notes.md","content_sha256":"abc",""" +
          """"content_size_bytes":12,"memory_store_id":"memstore_1",""" +
          """"memory_version_id":"memver_1","content":null}"""
      val m = Json.parse(json).as[Memory]
      m.id shouldBe "mem_1"
      m.path shouldBe "/notes.md"
      m.contentSizeBytes shouldBe 12L
      m.content shouldBe None
    }

    "deserialize a memory-list entry union (memory and prefix)" in {
      val memJson =
        """{"type":"memory","id":"mem_1","path":"/a.md","content_sha256":"x",""" +
          """"content_size_bytes":1,"memory_store_id":"ms_1","memory_version_id":"mv_1"}"""
      Json.parse(memJson).as[MemoryEntry] shouldBe a[MemoryEntry.Item]
      Json.parse("""{"type":"memory_prefix","path":"/dir/"}""").as[MemoryEntry] shouldBe
        MemoryEntry.Prefix("/dir/")
    }

    "deserialize a memory version (incl. redacted nulls)" in {
      val json =
        """{"type":"memory_version","id":"memver_1","memory_id":"mem_1",""" +
          """"memory_store_id":"memstore_1","operation":"modified",""" +
          """"created_by":{"type":"api_actor"},"redacted_at":null,"content":null}"""
      val v = Json.parse(json).as[MemoryVersion]
      v.operation shouldBe MemoryVersionOperation.modified
      v.createdByType shouldBe Some("api_actor")
      v.content shouldBe None
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
