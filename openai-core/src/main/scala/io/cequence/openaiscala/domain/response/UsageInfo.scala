package io.cequence.openaiscala.domain.response

case class UsageInfo(
  prompt_tokens: Int,
  total_tokens: Int,
  completion_tokens: Option[Int],
  prompt_tokens_details: Option[PromptTokensDetails] = None,
  completion_tokens_details: Option[CompletionTokenDetails] = None
  //  prompt_cache_hit_tokens: Option[Int],
  //  prompt_cache_miss_tokens: Option[Int]
)

object UsageInfo {

  /**
   * The usage of two requests added up (e.g. the rounds of one joined stream): every count is
   * summed; an optional count or details block stays absent only when both sides lack it.
   */
  def sum(
    a: UsageInfo,
    b: UsageInfo
  ): UsageInfo =
    UsageInfo(
      prompt_tokens = a.prompt_tokens + b.prompt_tokens,
      total_tokens = a.total_tokens + b.total_tokens,
      completion_tokens = sumOpt(a.completion_tokens, b.completion_tokens),
      prompt_tokens_details = (a.prompt_tokens_details, b.prompt_tokens_details) match {
        case (Some(x), Some(y)) =>
          Some(
            PromptTokensDetails(
              cached_tokens = x.cached_tokens + y.cached_tokens,
              audio_tokens = sumOpt(x.audio_tokens, y.audio_tokens)
            )
          )
        case (x, y) => x.orElse(y)
      },
      completion_tokens_details =
        (a.completion_tokens_details, b.completion_tokens_details) match {
          case (Some(x), Some(y)) =>
            Some(
              CompletionTokenDetails(
                reasoning_tokens = sumOpt(x.reasoning_tokens, y.reasoning_tokens),
                accepted_prediction_tokens =
                  sumOpt(x.accepted_prediction_tokens, y.accepted_prediction_tokens),
                rejected_prediction_tokens =
                  sumOpt(x.rejected_prediction_tokens, y.rejected_prediction_tokens)
              )
            )
          case (x, y) => x.orElse(y)
        }
    )

  private def sumOpt(
    a: Option[Int],
    b: Option[Int]
  ): Option[Int] =
    if (a.isEmpty && b.isEmpty) None else Some(a.getOrElse(0) + b.getOrElse(0))
}

case class CompletionTokenDetails(
  reasoning_tokens: Option[Int] = None,
  accepted_prediction_tokens: Option[Int] = None,
  rejected_prediction_tokens: Option[Int] = None
)

case class PromptTokensDetails(
  cached_tokens: Int,
  audio_tokens: Option[Int]
)
