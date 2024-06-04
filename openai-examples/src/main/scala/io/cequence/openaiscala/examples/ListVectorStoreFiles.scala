package io.cequence.openaiscala.examples

import scala.concurrent.Future

object ListVectorStoreFiles extends Example {

  override protected def run =
    for {
      vectorStores <- service.listVectorStores()

      vectorStoreChunks = vectorStores.sliding(10, 10).toList
      _ = vectorStoreChunks.map(_.map(x => (x.id, x.name))).foreach(println)
      files <- Future.traverse(vectorStoreChunks) { vectorStoresChunk =>
        Future.traverse(vectorStoresChunk) { vectorStore =>
          service.listVectorStoreFiles(vectorStore.id).map(file => (vectorStore.name, file))
        }
      }

    } yield {
      files.foreach(println)
    }
}
