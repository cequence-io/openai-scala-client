package io.cequence.openaiscala.domain.graders

import io.cequence.wsclient.JsonUtil.enumFormat
import io.cequence.openaiscala.JsonFormats.{chatRoleFormat, reasoningEffortFormat}
import play.api.libs.json._

object JsonFormats {

  private implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit lazy val stringCheckOperationFormat: Format[StringCheckOperation] =
    enumFormat[StringCheckOperation](StringCheckOperation.values: _*)

  implicit lazy val stringGraderFormat: OFormat[StringGrader] =
    Json.format[StringGrader]

  implicit lazy val textSimilarityEvaluationMetricFormat
    : Format[TextSimilarityEvaluationMetric] =
    enumFormat[TextSimilarityEvaluationMetric](TextSimilarityEvaluationMetric.values: _*)

  implicit lazy val textSimilarityGraderFormat: OFormat[TextSimilarityGrader] =
    Json.format[TextSimilarityGrader]

  implicit lazy val samplingParamsFormat: OFormat[SamplingParams] =
    Json.format[SamplingParams]

  implicit lazy val scoreModelInputFormat: OFormat[GraderModelInput] =
    Json.format[GraderModelInput]

  implicit lazy val scoreModelGraderFormat: OFormat[ScoreModelGrader] =
    new OFormat[ScoreModelGrader] {
      def reads(json: JsValue): JsResult[ScoreModelGrader] = {
        for {
          input <- (json \ "input").validate[Seq[GraderModelInput]]
          model <- (json \ "model").validate[String]
          name <- (json \ "name").validate[String]
          range = (json \ "range").asOpt[Seq[Double]].getOrElse(Nil)
          samplingParams <- (json \ "sampling_params").validateOpt[SamplingParams]
        } yield ScoreModelGrader(input, model, name, range, samplingParams)
      }

      def writes(grader: ScoreModelGrader): JsObject = {
        var obj = Json.obj(
          "input" -> grader.input,
          "model" -> grader.model,
          "name" -> grader.name
        )
        if (grader.range.nonEmpty) {
          obj = obj + ("range" -> Json.toJson(grader.range))
        }
        grader.samplingParams.foreach { params =>
          obj = obj + ("sampling_params" -> Json.toJson(params))
        }
        obj
      }
    }

  implicit lazy val labelModelGraderFormat: OFormat[LabelModelGrader] =
    new OFormat[LabelModelGrader] {
      def reads(json: JsValue): JsResult[LabelModelGrader] = {
        for {
          input <- (json \ "input").validate[Seq[GraderModelInput]]
          labels = (json \ "labels").asOpt[Seq[String]].getOrElse(Nil)
          model <- (json \ "model").validate[String]
          name <- (json \ "name").validate[String]
          passingLabels = (json \ "passing_labels").asOpt[Seq[String]].getOrElse(Nil)
        } yield LabelModelGrader(input, labels, model, name, passingLabels)
      }

      def writes(grader: LabelModelGrader): JsObject = {
        var obj = Json.obj(
          "input" -> grader.input,
          "model" -> grader.model,
          "name" -> grader.name
        )
        if (grader.labels.nonEmpty) {
          obj = obj + ("labels" -> Json.toJson(grader.labels))
        }
        if (grader.passingLabels.nonEmpty) {
          obj = obj + ("passing_labels" -> Json.toJson(grader.passingLabels))
        }
        obj
      }
    }

  implicit lazy val pythonGraderFormat: OFormat[PythonGrader] =
    Json.format[PythonGrader]

  // MultiGrader needs recursive format handling
  private lazy val lazyMultiGraderFormat: OFormat[MultiGrader] =
    Json.format[MultiGrader]

  // GraderInputContent formats
  implicit lazy val imageDetailFormat: Format[ImageDetail] =
    enumFormat[ImageDetail](ImageDetail.values: _*)

  implicit lazy val audioInputFormat: OFormat[AudioInput] =
    Json.format[AudioInput]

  private implicit lazy val inputTextFormat: OFormat[GraderInputContent.InputText] =
    Json.format[GraderInputContent.InputText]

  private implicit lazy val outputTextFormat: OFormat[GraderInputContent.OutputText] =
    Json.format[GraderInputContent.OutputText]

  private implicit lazy val inputImageFormat: OFormat[GraderInputContent.InputImage] =
    Json.format[GraderInputContent.InputImage]

  private implicit lazy val inputAudioFormat: OFormat[GraderInputContent.InputAudio] =
    Json.format[GraderInputContent.InputAudio]

  implicit lazy val graderInputContentFormat: Format[GraderInputContent] =
    new Format[GraderInputContent] {
      def reads(json: JsValue): JsResult[GraderInputContent] = {
        json match {
          case JsString(text) => JsSuccess(GraderInputContent.TextString(text))
          case arr: JsArray =>
            arr
              .validate[Seq[GraderInputContent]](Reads.seq(graderInputContentFormat))
              .map(items => GraderInputContent.ContentArray(items))
          case obj: JsObject =>
            (obj \ "type").validate[String].flatMap {
              case "input_text"  => inputTextFormat.reads(obj)
              case "output_text" => outputTextFormat.reads(obj)
              case "input_image" => inputImageFormat.reads(obj)
              case "input_audio" => inputAudioFormat.reads(obj)
              case other         => JsError(s"Unsupported grader input content type: $other")
            }
          case _ => JsError("Expected string, array, or object for GraderInputContent")
        }
      }

      def writes(content: GraderInputContent): JsValue = {
        content match {
          case c: GraderInputContent.TextString =>
            JsString(c.text)
          case c: GraderInputContent.ContentArray =>
            JsArray(c.items.map(item => writes(item)))
          case c: GraderInputContent.InputText =>
            inputTextFormat.writes(c) ++ Json.obj("type" -> c.`type`)
          case c: GraderInputContent.OutputText =>
            outputTextFormat.writes(c) ++ Json.obj("type" -> c.`type`)
          case c: GraderInputContent.InputImage =>
            inputImageFormat.writes(c) ++ Json.obj("type" -> c.`type`)
          case c: GraderInputContent.InputAudio =>
            inputAudioFormat.writes(c) ++ Json.obj("type" -> c.`type`)
        }
      }
    }

  implicit lazy val graderFormat: Format[Grader] = new Format[Grader] {
    def reads(json: JsValue): JsResult[Grader] = {
      (json \ "type").validate[String].flatMap {
        case "string_check"    => stringGraderFormat.reads(json)
        case "text_similarity" => textSimilarityGraderFormat.reads(json)
        case "score_model"     => scoreModelGraderFormat.reads(json)
        case "label_model"     => labelModelGraderFormat.reads(json)
        case "python"          => pythonGraderFormat.reads(json)
        case "multi"           => lazyMultiGraderFormat.reads(json)
        case other             => JsError(s"Unsupported grader type: $other")
      }
    }

    def writes(grader: Grader): JsValue = {
      val jsObject: JsObject = grader match {
        case g: StringGrader         => stringGraderFormat.writes(g)
        case g: TextSimilarityGrader => textSimilarityGraderFormat.writes(g)
        case g: ScoreModelGrader     => scoreModelGraderFormat.writes(g)
        case g: LabelModelGrader     => labelModelGraderFormat.writes(g)
        case g: PythonGrader         => pythonGraderFormat.writes(g)
        case g: MultiGrader          => lazyMultiGraderFormat.writes(g)
      }

      jsObject ++ Json.obj("type" -> grader.`type`)
    }
  }
}
