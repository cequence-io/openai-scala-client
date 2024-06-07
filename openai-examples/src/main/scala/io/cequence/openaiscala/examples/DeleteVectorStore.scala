package io.cequence.openaiscala.examples

import scala.concurrent.Future
object DeleteVectorStore extends Example {

  override protected def run: Future[Unit] =
    for {
      vectorStore <- service.deleteVectorStore(
//        "vs_yEz2JibrItk5Ll1AeZnarv1Z"
//        "vs_LY7eWXhQU2X8UvRFmrrkVO5l"
//        "vs_1IUP2xNYDN5VzrvlEFxCUOgx",
//        "vs_KSD2Kx8p3uvarEP2NmmWSlcc",
//        "vs_VYghZou2vlbXKku6ooXFrOJg"
        "vs_XKNtw4o7PmCoEdlfrslOjwSO"
      )
    } yield println(vectorStore)
}
