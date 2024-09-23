package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.VectorStoreFile

import scala.concurrent.Future

object ListVectorStoreFiles extends Example {

  override protected def run: Future[Any] =
    for {
      vectorStores <- service.listVectorStores()

      vectorStoreChunks = vectorStores.sliding(10, 10).toList
      vsAndFiles <- Future.traverse(vectorStoreChunks) { vectorStoresChunk =>
        Future.traverse(vectorStoresChunk) { vectorStore =>
          service
            .listVectorStoreFiles(vectorStore.id)
            .map((files: Seq[VectorStoreFile]) => (vectorStore, files))
        }
      }

    } yield {
      vsAndFiles.flatten.foreach { case (vs, files) =>
        println(s"Vector Store: ${vs.name}[${vs.id}] (${files.length} files)")
        files.foreach { file =>
          println(s"  - ${file.id} (${file.`object`})")
        }
      }
    }
}
