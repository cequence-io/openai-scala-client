package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import play.api.libs.json.JsObject

import java.util.UUID
import scala.concurrent.Future

/**
 * Experiment: Does OpenAI prompt caching (prompt_cache_key) survive changes to user prompt,
 * json_schema, and number of prior calls?
 *
 * Setup: gpt-5.2, response format: json_schema, 20 iterations per setting
 *
 * Results:
 *   - Changing the user prompt does NOT break caching (system prompt prefix ~1664/2350 tokens
 *     cached)
 *   - Changing the json_schema does NOT break caching (slightly lower hit rate)
 *   - Wait time between calls (0–10s) has no meaningful effect on hit rate
 *   - 2 identical warmup calls is the sweet spot: same schema 70%→100%, diff schema 75%→95%
 *   - Sequential calls with different prompt+schema each time also accumulate: 70%→75%→95% by
 *     3rd call
 *   - Caching provides no latency improvement; the benefit is purely cost reduction
 */
object CreateChatCompletionJsonCaching extends Example {

  private val Iterations = 20

  case class ExperimentResult(
    name: String,
    sleepMs: Int,
    schemaChange: Boolean,
    warmupCalls: Int,
    promptTokens: Int,
    cachedTokens: Int,
    responseTokens: Int,
    totalTokens: Int,
    latencyMs: Long
  )

  private def makeSystemPrompt(prefix: UUID): String =
    s"""$prefix. Hey hey, hey, You are a helpful weather assistant. In this role, you are entrusted with delivering accurate, timely, and comprehensible weather information to users from all walks of life. Your responsibilities encompass not only providing up-to-date weather forecasts and climate data but also explaining complex meteorological phenomena in plain language. The following comprehensive guidelines outline your role, core functions, interaction protocols, safety advisories, and ethical considerations. These instructions are designed to ensure that your responses are reliable, engaging, and accessible, regardless of the user's background knowledge.
      |
      |──────────────────────────────────────────────
      |I. ROLE OVERVIEW
      |──────────────────────────────────────────────
      |• **Identity & Expertise:**
      |  - You are a specialized weather assistant with extensive knowledge in meteorology, climatology, and atmospheric sciences.
      |  - Your expertise includes current weather conditions, short- and long-term forecasts, historical weather trends, severe weather warnings, and the science behind weather phenomena.
      |
      |• **Primary Purpose:**
      |  - To provide accurate weather forecasts and climate information tailored to the user's query, ensuring clarity and usefulness in every response.
      |  - To educate users about meteorological processes, explain weather-related terminology, and help them interpret various weather data presentations.
      |
      |──────────────────────────────────────────────
      |II. CORE RESPONSIBILITIES
      |──────────────────────────────────────────────
      |• **Current Weather & Forecasting:**
      |  - Deliver current weather conditions, forecasts, and meteorological data for specified locations, whether global, regional, or local.
      |  - Ensure that responses include pertinent details such as temperature, humidity, precipitation, wind speed, atmospheric pressure, and other relevant factors.
      |
      |• **Explanatory Support:**
      |  - Explain meteorological terms (e.g., "barometric pressure," "dew point," "wind chill") in accessible language.
      |  - Break down the science behind weather phenomena like storm formation, cloud development, and seasonal changes.
      |
      |• **Historical & Climate Data:**
      |  - Provide historical weather data and discuss long-term climate trends when requested, highlighting both the data's significance and its limitations.
      |  - Offer context on how past weather patterns compare with current trends, including the potential influence of climate change.
      |
      |• **Severe Weather & Safety Guidance:**
      |  - In situations involving severe weather events (e.g., hurricanes, tornadoes, floods), include essential safety warnings and advise users to follow guidance from local authorities.
      |  - Always remind users that your forecasts are based on available data and that conditions can change rapidly. Encourage verification from official meteorological sources during emergencies.
      |
      |──────────────────────────────────────────────
      |III. USER INTERACTION PROTOCOLS
      |──────────────────────────────────────────────
      |• **Clarity & Engagement:**
      |  - Maintain a friendly, professional, and supportive tone in all interactions.
      |  - Clarify ambiguous queries by asking targeted follow-up questions, such as confirming the location, desired time frame, or specific aspects of the weather (e.g., "Are you asking about today's forecast, or do you need a long-range outlook?").
      |
      |• **Detail & Structure:**
      |  - Organize responses clearly by summarizing the key points first, then offering detailed explanations.
      |  - Use bullet points, lists, or numbered steps if it helps break down complex information for the user.
      |
      |• **Adaptability:**
      |  - Tailor your explanations to the user's level of expertise. For technical questions, provide detailed data and context; for casual inquiries, keep explanations simple and jargon-free.
      |  - Invite follow-up questions and indicate your willingness to expand on topics if further clarification is needed.
      |
      |──────────────────────────────────────────────
      |IV. ACCURACY, DATA USAGE, AND LIMITATIONS
      |──────────────────────────────────────────────
      |• **Reliability of Information:**
      |  - Base your responses on the most reliable, evidence-based meteorological data available.
      |  - Clearly communicate the inherent uncertainties in weather forecasting by including disclaimers like, "Forecasts are subject to change," or "These predictions are based on current data and might be updated."
      |
      |• **Data Considerations:**
      |  - Specify the units of measurement (e.g., Celsius vs. Fahrenheit, millimeters vs. inches) and perform accurate conversions when requested.
      |  - When referring to specific weather models (such as GFS, ECMWF, NAM), explain their role in forecasting and emphasize that they are part of a broader suite of predictive tools.
      |
      |• **Historical and Climate Context:**
      |  - When discussing historical weather or climate trends, provide context regarding the data's time frame, its reliability, and any potential anomalies.
      |  - Acknowledge that while historical data can guide expectations, it may not precisely predict current conditions due to dynamic atmospheric changes and emerging climate patterns.
      |
      |──────────────────────────────────────────────
      |V. EXPLANATORY AND EDUCATIONAL APPROACHES
      |──────────────────────────────────────────────
      |• **Simplification of Complex Concepts:**
      |  - Break down complex scientific ideas (e.g., the Coriolis effect, adiabatic processes, or jet stream dynamics) into easy-to-understand components.
      |  - Use analogies, examples, or comparisons to everyday experiences to make abstract concepts relatable.
      |
      |• **Step-by-Step Guidance:**
      |  - Provide clear, step-by-step instructions for interpreting weather maps, radar images, or forecast charts.
      |  - Explain how to read and understand different weather symbols, color codes, and other visual indicators that are often used in meteorological reporting.
      |
      |• **Encouraging Informed Decisions:**
      |  - Empower users by explaining how to cross-check weather information with official local resources, weather apps, or news updates.
      |  - Suggest practical measures (e.g., "If heavy rain is forecasted, consider planning indoor activities and monitoring local updates for any sudden changes.")
      |
      |──────────────────────────────────────────────
      |VI. SPECIAL TOPICS AND CONTEXT-SPECIFIC GUIDELINES
      |──────────────────────────────────────────────
      |• **Extreme Weather Events:**
      |  - When users inquire about severe weather (e.g., storms, blizzards, heatwaves), include detailed safety advice.
      |  - Emphasize that users should follow local emergency services' recommendations and provide links or suggestions for where to find up-to-date alerts (when possible).
      |
      |• **Seasonal & Regional Variations:**
      |  - Highlight the significance of seasonal weather patterns, such as monsoon cycles, winter storms, or summer heatwaves.
      |  - Discuss how geographic features (mountains, coastlines, urban areas) can affect local weather conditions.
      |
      |• **Climate Change Discussions:**
      |  - Provide balanced, evidence-based insights into climate change, distinguishing between short-term weather variability and long-term climate trends.
      |  - Outline factors like global warming, changing precipitation patterns, and shifting weather extremes, while ensuring to include appropriate disclaimers regarding predictive uncertainties.
      |
      |• **Travel, Agriculture, and Outdoor Activities:**
      |  - For queries related to travel or outdoor events, offer detailed forecasts alongside any pertinent safety or preparation tips.
      |  - When addressing agricultural concerns, include contextual information on seasonal patterns, potential frost dates, or drought conditions that might impact crops.
      |
      |──────────────────────────────────────────────
      |VII. TECHNICAL REPORTING AND DATA PRESENTATION
      |──────────────────────────────────────────────
      |• **Data Reporting:**
      |  - When providing numerical data, always include the measurement units and, where applicable, a brief explanation of what those numbers mean in practical terms.
      |  - Clarify the difference between "chance of precipitation" and "expected rainfall," ensuring users understand the probabilistic nature of forecasts.
      |
      |• **Use of Technical Terminology:**
      |  - Introduce and define technical terms succinctly; for instance, explain "isobar" as a line on a weather map that connects points of equal atmospheric pressure.
      |  - Ensure that any technical discussion is accessible by providing contextual explanations or analogies for complex scientific principles.
      |
      |• **Visual and Comparative Aids:**
      |  - When possible, describe how users might interpret visual aids such as radar maps, satellite images, or weather charts.
      |  - Offer guidance on what to look for in these visual representations to better understand the overall weather scenario.
      |
      |──────────────────────────────────────────────
      |VIII. USER SAFETY, ETHICS, AND EMERGENCY RESPONSE
      |──────────────────────────────────────────────
      |• **Safety First:**
      |  - Always prioritize user safety by including clear disclaimers: "I am not an emergency service; please follow local instructions in the event of severe weather."
      |  - In emergencies, advise users to seek immediate assistance from local authorities and refrain from relying solely on online forecasts.
      |
      |• **Ethical Considerations:**
      |  - Maintain a neutral and objective tone, avoiding partisan or alarmist language.
      |  - Respect user privacy by only addressing location-based inquiries when explicitly provided, and do not attempt to infer personal data.
      |
      |• **Emergency Instructions:**
      |  - For severe weather alerts, provide guidance on what immediate actions to take, such as seeking shelter, preparing emergency kits, or evacuating if necessary.
      |  - Remind users to monitor local news channels, official weather websites, or government alerts for the most current updates.
      |
      |──────────────────────────────────────────────
      |IX. FINAL REMINDERS AND OVERALL STRATEGY
      |──────────────────────────────────────────────
      |• **Summarization & Clarity:**
      |  - Conclude responses with a brief summary of the key points, ensuring that the user understands the forecast or explanation fully.
      |  - Reiterate any crucial safety information or recommended actions, especially when the weather situation is volatile.
      |
      |• **Encouragement of Further Engagement:**
      |  - Invite users to ask follow-up questions if any part of your explanation is unclear or if they require additional details.
      |  - Express your readiness to help with more detailed insights or clarifications on any weather-related topic.
      |
      |• **Continuous Learning & Adaptation:**
      |  - Stay informed about the latest meteorological research, technological advancements in forecasting, and changes in climate patterns.
      |  - Adjust your explanations as needed to reflect the most current understanding and data, while remaining transparent about any uncertainties.
      |
      |• **Overall Mission:**
      |  - Your core objective is to empower users with reliable, actionable, and clear weather information.
      |  - Whether the user is a student seeking to understand atmospheric dynamics, a traveler planning a trip, or someone preparing for a severe weather event, your role is to provide them with the best possible guidance grounded in scientific evidence and clear communication.
      |
      |──────────────────────────────────────────────
      |SUMMARY
      |──────────────────────────────────────────────
      |You are a weather assistant whose mission is to serve as both an information provider and an educator. Your role involves:
      |  • Offering accurate and timely weather forecasts.
      |  • Explaining meteorological concepts in accessible language.
      |  • Providing detailed, context-sensitive advice for everyday and emergency weather situations.
      |  • Maintaining a neutral, professional, and supportive tone throughout your interactions.
      |  • Emphasizing user safety and encouraging cross-verification with authoritative sources.
      |  • Balancing technical detail with clarity to ensure all users—regardless of their expertise—can make informed decisions.
      |
      |By following these guidelines meticulously, you will consistently deliver high-quality, accurate, and helpful weather information. Remember, the goal is to foster understanding, ensure safety, and empower users with knowledge about the dynamic nature of our atmosphere.""".stripMargin

  private val weatherOverviewSchema = JsonSchemaDef(
    name = "weather_overview",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "temperature" -> JsonSchema
            .String(description = Some("Current temperature with unit")),
          "humidity" -> JsonSchema.String(description = Some("Current humidity percentage")),
          "conditions" -> JsonSchema.String(description = Some("Current weather conditions")),
          "summary" -> JsonSchema.String(description = Some("Brief weather summary"))
        ),
        required = Seq("temperature", "humidity", "conditions", "summary")
      )
    )
  )

  private val windDetailsSchema = JsonSchemaDef(
    name = "wind_details",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "wind_speed" -> JsonSchema
            .String(description = Some("Current wind speed with unit")),
          "pressure" -> JsonSchema.String(description = Some("Atmospheric pressure")),
          "visibility" -> JsonSchema.String(description = Some("Current visibility")),
          "summary" -> JsonSchema.String(description = Some("Brief wind conditions summary"))
        ),
        required = Seq("wind_speed", "pressure", "visibility", "summary")
      )
    )
  )

  private val precipitationSchema = JsonSchemaDef(
    name = "precipitation_info",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "rain_chance" -> JsonSchema.String(description = Some("Chance of rain percentage")),
          "snow_chance" -> JsonSchema.String(description = Some("Chance of snow percentage")),
          "forecast_hours" -> JsonSchema.String(description = Some("Forecast time range")),
          "summary" -> JsonSchema.String(description = Some("Brief precipitation summary"))
        ),
        required = Seq("rain_chance", "snow_chance", "forecast_hours", "summary")
      )
    )
  )

  private val uvIndexSchema = JsonSchemaDef(
    name = "uv_index",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "uv_index" -> JsonSchema.String(description = Some("Current UV index value")),
          "risk_level" -> JsonSchema.String(description = Some("UV risk level")),
          "recommendation" -> JsonSchema
            .String(description = Some("Sun protection recommendation")),
          "summary" -> JsonSchema.String(description = Some("Brief UV summary"))
        ),
        required = Seq("uv_index", "risk_level", "recommendation", "summary")
      )
    )
  )

  private def exec(
    messages: Seq[BaseMessage],
    schema: JsonSchemaDef,
    cacheKey: String
  ) =
    service.createChatCompletionWithJSONFullResponse[JsObject](
      messages = messages,
      settings = CreateChatCompletionSettings(
        model = ModelId.gpt_5_2,
        temperature = Some(0),
        max_tokens = Some(4000),
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(schema),
        extra_params = Map("prompt_cache_key" -> cacheKey)
      )
    )

  private def runExperiment(
    name: String,
    sleepMs: Int,
    seedCity: String,
    testCity: String,
    seedSchema: JsonSchemaDef,
    testSchema: JsonSchemaDef,
    warmupCalls: Int = 1
  ): Future[ExperimentResult] = {
    val uuid = UUID.randomUUID()
    val systemPrompt = makeSystemPrompt(uuid)
    val cacheKey = s"cache-experiment-$uuid"

    val warmups = (1 to warmupCalls).foldLeft(Future.successful(())) {
      (
        prev,
        _
      ) =>
        prev.flatMap { _ =>
          exec(
            Seq(SystemMessage(systemPrompt), UserMessage(s"Weather in $seedCity?")),
            seedSchema,
            cacheKey
          ).map(_ => ())
        }
    }

    for {
      _ <- warmups

      _ = {
        println(s"  [$name] $warmupCalls warmup call(s) done. Sleeping ${sleepMs}ms...")
        java.lang.Thread.sleep(sleepMs)
      }

      startTime = System.currentTimeMillis()

      (_, testResp) <- exec(
        Seq(SystemMessage(systemPrompt), UserMessage(s"Weather in $testCity?")),
        testSchema,
        cacheKey
      )

      endTime = System.currentTimeMillis()
    } yield {
      val usage = testResp.usage.get
      val result = ExperimentResult(
        name = name,
        sleepMs = sleepMs,
        schemaChange = seedSchema.name != testSchema.name,
        warmupCalls = warmupCalls,
        promptTokens = usage.prompt_tokens,
        cachedTokens = usage.prompt_tokens_details.map(_.cached_tokens).getOrElse(0),
        responseTokens = usage.completion_tokens.getOrElse(0),
        totalTokens = usage.total_tokens,
        latencyMs = endTime - startTime
      )
      println(
        s"  [$name] Done. Cached: ${result.cachedTokens}/${result.promptTokens} tokens, latency: ${result.latencyMs}ms"
      )
      result
    }
  }

  case class AggregatedResult(
    name: String,
    sleepMs: Int,
    schemaChange: Boolean,
    warmupCalls: Int,
    iterations: Int,
    cacheHits: Int,
    cacheHitRate: Double,
    avgCachedTokens: Double,
    avgPromptTokens: Double,
    avgLatencyMs: Double,
    medianLatencyMs: Long,
    stddevLatencyMs: Double,
    avgLatencyCachedMs: Option[Double],
    avgLatencyUncachedMs: Option[Double]
  )

  private def aggregate(
    name: String,
    results: Seq[ExperimentResult]
  ): AggregatedResult = {
    val n = results.size
    val hits = results.count(_.cachedTokens > 0)
    val latencies = results.map(_.latencyMs).sorted
    val avgLat = latencies.sum.toDouble / n
    val median =
      if (n % 2 == 0) (latencies(n / 2 - 1) + latencies(n / 2)) / 2 else latencies(n / 2)
    val variance = latencies.map(l => math.pow(l - avgLat, 2)).sum / n
    val cachedLats = results.filter(_.cachedTokens > 0).map(_.latencyMs)
    val uncachedLats = results.filter(_.cachedTokens == 0).map(_.latencyMs)

    AggregatedResult(
      name = name,
      sleepMs = results.head.sleepMs,
      schemaChange = results.head.schemaChange,
      warmupCalls = results.head.warmupCalls,
      iterations = n,
      cacheHits = hits,
      cacheHitRate = hits * 100.0 / n,
      avgCachedTokens = results.map(_.cachedTokens.toDouble).sum / n,
      avgPromptTokens = results.map(_.promptTokens.toDouble).sum / n,
      avgLatencyMs = avgLat,
      medianLatencyMs = median,
      stddevLatencyMs = math.sqrt(variance),
      avgLatencyCachedMs =
        if (cachedLats.nonEmpty) Some(cachedLats.sum.toDouble / cachedLats.size) else None,
      avgLatencyUncachedMs =
        if (uncachedLats.nonEmpty) Some(uncachedLats.sum.toDouble / uncachedLats.size)
        else None
    )
  }

  private def runSettingNTimes(
    name: String,
    sleepMs: Int,
    seedCity: String,
    testCity: String,
    seedSchema: JsonSchemaDef,
    testSchema: JsonSchemaDef,
    warmupCalls: Int = 1
  ): Future[AggregatedResult] = {
    println(
      s"\n>>> Starting setting: $name ($Iterations iterations, $warmupCalls warmup call(s))"
    )
    (1 to Iterations)
      .foldLeft(Future.successful(Seq.empty[ExperimentResult])) {
        (
          accFuture,
          i
        ) =>
          accFuture.flatMap { acc =>
            println(s"  [$name] iteration $i/$Iterations...")
            runExperiment(
              name,
              sleepMs,
              seedCity,
              testCity,
              seedSchema,
              testSchema,
              warmupCalls
            ).map(acc :+ _)
          }
      }
      .map { results =>
        val agg = aggregate(name, results)
        println(
          s"<<< Setting $name done: ${agg.cacheHits}/$Iterations hits (${f"${agg.cacheHitRate}%.1f"}%)"
        )
        agg
      }
  }

  // Sequence: 1 warmup call (schema A) -> 3 subsequent calls each with different user prompt + different schema
  case class SequenceCallResult(
    position: Int, // 1, 2, or 3 (position after warmup)
    schema: String,
    city: String,
    cachedTokens: Int,
    promptTokens: Int,
    latencyMs: Long
  )

  case class SequenceResult(calls: Seq[SequenceCallResult])

  private def runSequence(iteration: Int): Future[SequenceResult] = {
    val uuid = UUID.randomUUID()
    val systemPrompt = makeSystemPrompt(uuid)
    val cacheKey = s"cache-seq-$uuid"

    val steps = Seq(
      ("Bergen", windDetailsSchema, "wind_details"),
      ("Helsinki", precipitationSchema, "precipitation_info"),
      ("Madrid", uvIndexSchema, "uv_index")
    )

    for {
      // warmup call
      _ <- exec(
        Seq(SystemMessage(systemPrompt), UserMessage("Weather in Oslo?")),
        weatherOverviewSchema,
        cacheKey
      )
      _ = println(s"  [seq] iter $iteration warmup done")

      // 3 subsequent calls with different prompt + schema each
      results <- steps.zipWithIndex.foldLeft(
        Future.successful(Seq.empty[SequenceCallResult])
      ) { case (accFuture, ((city, schema, schemaName), idx)) =>
        accFuture.flatMap { acc =>
          val start = System.currentTimeMillis()
          exec(
            Seq(SystemMessage(systemPrompt), UserMessage(s"Weather in $city?")),
            schema,
            cacheKey
          ).map { case (_, resp) =>
            val usage = resp.usage.get
            val cached = usage.prompt_tokens_details.map(_.cached_tokens).getOrElse(0)
            val elapsed = System.currentTimeMillis() - start
            println(
              s"  [seq] iter $iteration call ${idx + 1}: $schemaName/$city -> cached $cached/${usage.prompt_tokens}, ${elapsed}ms"
            )
            acc :+ SequenceCallResult(
              idx + 1,
              schemaName,
              city,
              cached,
              usage.prompt_tokens,
              elapsed
            )
          }
        }
      }
    } yield SequenceResult(results)
  }

  override protected def run: Future[_] = {
    println(
      s"\n>>> Sequential caching experiment: 1 warmup + 3 calls with different prompt+schema ($Iterations iterations)"
    )

    (1 to Iterations)
      .foldLeft(Future.successful(Seq.empty[SequenceResult])) {
        (
          accFuture,
          i
        ) =>
          accFuture.flatMap { acc =>
            runSequence(i).map(acc :+ _)
          }
      }
      .map { sequences =>
        // Group results by call position (1, 2, 3)
        val byPosition = (1 to 3).map { pos =>
          val calls = sequences.map(_.calls(pos - 1))
          val hits = calls.count(_.cachedTokens > 0)
          val avgCached = calls.map(_.cachedTokens.toDouble).sum / calls.size
          val avgPrompt = calls.map(_.promptTokens.toDouble).sum / calls.size
          val latencies = calls.map(_.latencyMs).sorted
          val avgLat = latencies.sum.toDouble / latencies.size
          val median =
            if (latencies.size % 2 == 0)
              (latencies(latencies.size / 2 - 1) + latencies(latencies.size / 2)) / 2
            else latencies(latencies.size / 2)
          val cachedLats = calls.filter(_.cachedTokens > 0).map(_.latencyMs)
          val uncachedLats = calls.filter(_.cachedTokens == 0).map(_.latencyMs)
          (
            pos,
            calls.head.schema,
            calls.head.city,
            hits,
            avgCached,
            avgPrompt,
            avgLat,
            median,
            cachedLats,
            uncachedLats
          )
        }

        println("\n" + "=" * 130)
        println(s"SEQUENTIAL CACHING EXPERIMENT (gpt-5.2, $Iterations iterations)")
        println(
          "Flow: warmup(schema A, city 1) -> call 1(schema B, city 2) -> call 2(schema C, city 3) -> call 3(schema D, city 4)"
        )
        println(
          "All calls share the same system prompt + prompt_cache_key. Each iteration = fresh UUID."
        )
        println("=" * 130)
        println(
          f"${"Call#"}%-8s ${"Schema"}%-22s ${"City"}%-12s ${"Hits"}%-10s ${"Hit-rate"}%-10s ${"Avg-cached"}%-12s ${"Avg-prompt"}%-12s ${"Avg-lat"}%-10s ${"Med-lat"}%-10s ${"Lat(hit)"}%-14s ${"Lat(miss)"}%-14s"
        )
        println("-" * 130)

        byPosition.foreach {
          case (
                pos,
                schema,
                city,
                hits,
                avgCached,
                avgPrompt,
                avgLat,
                median,
                cachedLats,
                uncachedLats
              ) =>
            val hitRate = f"${hits * 100.0 / Iterations}%.1f%%"
            val latHit =
              if (cachedLats.nonEmpty) f"${cachedLats.sum.toDouble / cachedLats.size}%.0f"
              else "N/A"
            val latMiss =
              if (uncachedLats.nonEmpty)
                f"${uncachedLats.sum.toDouble / uncachedLats.size}%.0f"
              else "N/A"
            println(
              f"$pos%-8d $schema%-22s $city%-12s $hits%d/$Iterations%d      $hitRate%-10s ${f"$avgCached%.0f"}%-12s ${f"$avgPrompt%.0f"}%-12s ${f"$avgLat%.0f"}%-10s $median%-10d $latHit%-14s $latMiss%-14s"
            )
        }

        println("\n" + "=" * 130)
        println("CONCLUSIONS")
        println("=" * 130)
        byPosition.foreach { case (pos, schema, _, hits, _, _, _, _, _, _) =>
          println(
            f"  Call $pos ($schema%-20s): $hits%d/$Iterations%d cache hits (${hits * 100.0 / Iterations}%.1f%%)"
          )
        }

        val allHits = byPosition.map(_._4).sum
        val allTotal = byPosition.size * Iterations
        println(
          f"\n  Overall hit rate across all positions: ${allHits * 100.0 / allTotal}%.1f%% ($allHits/$allTotal)"
        )
        println("=" * 130)
      }
  }
}
