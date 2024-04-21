package io.cequence.openaiscala.v2.service

trait CloseableService {

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  def close(): Unit
}
