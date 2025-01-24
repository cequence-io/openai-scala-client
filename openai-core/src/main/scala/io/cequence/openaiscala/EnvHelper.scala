package io.cequence.openaiscala

trait EnvHelper {

  protected def getEnvValue(key: String): String =
    Option(System.getenv(key)).getOrElse(
      throw new IllegalStateException(
        s"${key} environment variable expected but not set. Alternatively, you can pass the value explicitly to the factory method."
      )
    )
}
