package io.cequence.openaiscala.domain.response

/**
 * Used only for debugging... can be removed later on
 */
trait ResponseStringMarshaller {

  def fineTuneToString(fileInfo: FineTuneJob): String =
    s"""File-tune Job
       |-id: ${fileInfo.id}
       |-model: ${fileInfo.model}
       |-created at: ${fileInfo.created_at.toString}
       |-fine-tune model: ${fileInfo.fine_tuned_model.getOrElse("N/A")}
       |-organization id: ${fileInfo.organization_id}
       |-status: ${fileInfo.status}
       |-training file: ${fileInfo.training_file}
       |-validation file: ${fileInfo.validation_file.getOrElse("N/A")}
       |-result files: ${fileInfo.result_files.mkString(",")}
       |-error: ${fileInfo.error.getOrElse("N/A")}
     """.stripMargin

  def fileInfoToString(fileInfo: FileInfo): String =
    s"""File Info
       |-id: ${fileInfo.id}
       |-filename: ${fileInfo.filename}
       |-bytes: ${fileInfo.bytes}
       |-created at: ${fileInfo.created_at.toString}
       |-status: ${fileInfo.status}
       |-status_details: ${fileInfo.status_details.getOrElse("N/A")}
       |-purpose: ${fileInfo.purpose}
     """.stripMargin

  def imageToString(image: ImageInfo): String =
    s"""Image
       |-created: ${image.created.toString}
       |-data: ${image.data.map(_.mkString(", ")).mkString("; ")}
     """.stripMargin

  def embeddingToString(embedding: EmbeddingResponse): String =
    s"""Completion
       |-model: ${embedding.model}
       |-data: ${embedding.data.map(embeddingInfoToString).mkString("\n")}
       |-usage: ${usageToString(embedding.usage)},
     """.stripMargin

  def embeddingInfoToString(embeddingInfo: EmbeddingInfo): String =
    s"""Embedding Info
       |-index: ${embeddingInfo.index}
       |-embedding: ${embeddingInfo.embedding.mkString(", ")}
     """.stripMargin

  def moderationToString(edit: ModerationResponse): String =
    s"""Moderation
       |-id: ${edit.id}
       |-model: ${edit.model}
       |-results: ${edit.results.map(moderationResultToString).mkString("\n")}
     """.stripMargin

  def moderationResultToString(moderationResult: ModerationResult): String =
    s"""Moderation Result
       |-categories: ${moderationCategoriesToString(
        moderationResult.categories
      )}
       |-category scores: ${moderationCategoryScoresToString(
        moderationResult.category_scores
      )}
       |-flagged: ${moderationResult.flagged}
     """.stripMargin

  def moderationCategoriesToString(moderationCategories: ModerationCategories): String =
    s"""Moderation Categories
       |-hate: ${moderationCategories.hate}
       |-hate threatening: ${moderationCategories.hate_threatening}
       |-self harm: ${moderationCategories.self_harm}
       |-sexual: ${moderationCategories.sexual}
       |-sexual minors: ${moderationCategories.sexual_minors}
       |-violence: ${moderationCategories.violence}
       |-violence_graphic: ${moderationCategories.violence_graphic}
     """.stripMargin

  def moderationCategoryScoresToString(
    moderationCategoryScores: ModerationCategoryScores
  ): String =
    s"""Moderation Category Scores
       |-hate: ${moderationCategoryScores.hate}
       |-hate threatening: ${moderationCategoryScores.hate_threatening}
       |-self harm: ${moderationCategoryScores.self_harm}
       |-sexual: ${moderationCategoryScores.sexual}
       |-sexual minors: ${moderationCategoryScores.sexual_minors}
       |-violence: ${moderationCategoryScores.violence}
       |-violence_graphic: ${moderationCategoryScores.violence_graphic}
     """.stripMargin

  def editToString(edit: TextEditResponse): String =
    s"""Completion
       |-created: ${edit.created.toString}
       |-usage: ${usageToString(edit.usage)}
       |-choices: ${edit.choices.map(editChoiceToString).mkString("\n")}
     """.stripMargin

  def editChoiceToString(choice: TextEditChoiceInfo): String =
    s"""Choice
       |-index: ${choice.index}
       |-text: ${choice.text}
       |-logprobs: ${choice.logprobs.map(logprobsToString).getOrElse("N/A")}
     """.stripMargin

  def completionToString(completion: TextCompletionResponse): String =
    s"""Completion
       |-id: ${completion.id}
       |-model: ${completion.model}
       |-created" ${completion.created.toString}
       |-usage: ${completion.usage.map(usageToString).getOrElse("N/A")}
       |-choices: ${completion.choices.map(completionChoiceToString).mkString("\n")}
     """.stripMargin

  def completionChoiceToString(choice: TextCompletionChoiceInfo): String =
    s"""Choice
       |-index: ${choice.index}
       |-text: ${choice.text}
       |-logprobs: ${choice.logprobs.map(logprobsToString).getOrElse("N/A")}
       |-finish reason: ${choice.finish_reason}
     """.stripMargin

  def logprobsToString(logProb: LogprobsInfo): String =
    s"""Logprobs
       |-tokens: ${logProb.tokens.mkString(", ")}
       |-token_logprobs: ${logProb.token_logprobs.mkString(", ")}
       |-top_logprobs: ${logProb.top_logprobs.map(_.mkString(",")).mkString("; ")}
       |-text_offset: ${logProb.text_offset.mkString(", ")}
     """.stripMargin

  def usageToString(usage: UsageInfo): String =
    s"""Usage
       |-prompt tokens: ${usage.prompt_tokens}
       |-completion tokens: ${usage.completion_tokens.getOrElse("N/A")}
       |-total tokens: ${usage.total_tokens}
     """.stripMargin

  def usageToString(usage: EmbeddingUsageInfo): String =
    s"""Usage
       |-prompt tokens: ${usage.prompt_tokens}
       |-total tokens: ${usage.total_tokens}
     """.stripMargin
}
