package io.cequence.openaiscala.vertexai

import io.cequence.openaiscala.vertexai.domain.{
  BatchJobCompletionStats,
  BatchJobError,
  BatchJobInput,
  BatchJobOutput,
  BatchJobOutputInfo,
  BatchPredictionJob,
  JobState,
  ListBatchPredictionJobsResponse
}
import io.cequence.wsclient.JsonUtil.enumFormat
import play.api.libs.json._

object JsonFormats extends JsonFormats

/**
 * Play JSON formats for the (REST-based) Vertex AI batch prediction API. The Vertex REST API
 * uses camelCase field names and serializes int64 values as strings.
 */
trait JsonFormats {

  implicit lazy val jobStateFormat: Format[JobState] = enumFormat(JobState.values: _*)

  // proto-JSON serializes int64 as a string - tolerate both shapes
  private lazy val longFromStringOrNumberReads: Reads[Long] = Reads {
    case JsNumber(n) => JsSuccess(n.toLong)
    case JsString(s) =>
      try JsSuccess(s.toLong)
      catch {
        case _: NumberFormatException => JsError(s"Cannot parse a long from '$s'.")
      }
    case other => JsError(s"Expected a number or a numeric string, got: $other")
  }

  implicit lazy val batchJobErrorReads: Reads[BatchJobError] = Reads { json =>
    JsSuccess(
      BatchJobError(
        code = (json \ "code").asOpt[Int],
        message = (json \ "message").asOpt[String]
      )
    )
  }

  implicit lazy val batchJobOutputInfoReads: Reads[BatchJobOutputInfo] = Reads { json =>
    JsSuccess(
      BatchJobOutputInfo(
        gcsOutputDirectory = (json \ "gcsOutputDirectory").asOpt[String],
        bigqueryOutputDataset = (json \ "bigqueryOutputDataset").asOpt[String],
        bigqueryOutputTable = (json \ "bigqueryOutputTable").asOpt[String]
      )
    )
  }

  implicit lazy val batchJobCompletionStatsReads: Reads[BatchJobCompletionStats] = Reads {
    json =>
      def longAt(field: String): Option[Long] =
        (json \ field).asOpt(longFromStringOrNumberReads)

      JsSuccess(
        BatchJobCompletionStats(
          successfulCount = longAt("successfulCount"),
          failedCount = longAt("failedCount"),
          incompleteCount = longAt("incompleteCount")
        )
      )
  }

  implicit lazy val batchJobInputReads: Reads[BatchJobInput] = Reads { json =>
    (json \ "gcsSource" \ "uris")
      .asOpt[Seq[String]]
      .map(uris => JsSuccess(BatchJobInput.Gcs(uris): BatchJobInput))
      .orElse(
        (json \ "bigquerySource" \ "inputUri")
          .asOpt[String]
          .map(uri => JsSuccess(BatchJobInput.BigQuery(uri): BatchJobInput))
      )
      .getOrElse(JsError(s"Cannot parse a batch job input config from: $json"))
  }

  implicit lazy val batchJobOutputReads: Reads[BatchJobOutput] = Reads { json =>
    (json \ "gcsDestination" \ "outputUriPrefix")
      .asOpt[String]
      .map(prefix => JsSuccess(BatchJobOutput.Gcs(prefix): BatchJobOutput))
      .orElse(
        (json \ "bigqueryDestination" \ "outputUri")
          .asOpt[String]
          .map(uri => JsSuccess(BatchJobOutput.BigQuery(uri): BatchJobOutput))
      )
      .getOrElse(JsError(s"Cannot parse a batch job output config from: $json"))
  }

  implicit lazy val batchPredictionJobReads: Reads[BatchPredictionJob] = Reads { json =>
    (json \ "name").validate[String].map { name =>
      BatchPredictionJob(
        name = name,
        displayName = (json \ "displayName").asOpt[String],
        model = (json \ "model").asOpt[String],
        modelVersionId = (json \ "modelVersionId").asOpt[String],
        state = (json \ "state").asOpt[JobState],
        error = (json \ "error").asOpt[BatchJobError],
        input = (json \ "inputConfig").asOpt[BatchJobInput],
        output = (json \ "outputConfig").asOpt[BatchJobOutput],
        outputInfo = (json \ "outputInfo").asOpt[BatchJobOutputInfo],
        completionStats = (json \ "completionStats").asOpt[BatchJobCompletionStats],
        createTime = (json \ "createTime").asOpt[String],
        startTime = (json \ "startTime").asOpt[String],
        endTime = (json \ "endTime").asOpt[String],
        updateTime = (json \ "updateTime").asOpt[String],
        labels = (json \ "labels").asOpt[Map[String, String]].getOrElse(Map.empty)
      )
    }
  }

  implicit lazy val listBatchPredictionJobsResponseReads
    : Reads[ListBatchPredictionJobsResponse] = Reads { json =>
    JsSuccess(
      ListBatchPredictionJobsResponse(
        batchPredictionJobs =
          (json \ "batchPredictionJobs").asOpt[Seq[BatchPredictionJob]].getOrElse(Nil),
        nextPageToken = (json \ "nextPageToken").asOpt[String]
      )
    )
  }
}
