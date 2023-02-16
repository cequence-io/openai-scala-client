package io.cequence.openaiscala.domain.settings

@Deprecated // Not needed anymore
case class ListFineTuneEventsSettings(

  // Whether to stream events for the fine-tune job. If set to true, events will be sent as data-only server-sent events as they become available. The stream will terminate with a data: [DONE] message when the job is finished (succeeded, cancelled, or failed).
  // If set to false, only events generated so far will be returned.
  // Defaults to false
  stream: Option[Boolean] = None
)