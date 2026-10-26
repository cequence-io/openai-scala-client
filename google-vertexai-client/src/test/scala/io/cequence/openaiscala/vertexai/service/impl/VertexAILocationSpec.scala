package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.vertexai.service.VertexAILocation
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VertexAILocationSpec extends AnyWordSpec with Matchers {

  "VertexAILocation" should {

    "resolve no location (or a blank one) to global" in {
      VertexAILocation.resolve(None) shouldBe "global"
      VertexAILocation.resolve(Some("  ")) shouldBe "global"
      VertexAILocation.resolve(Some(" europe-west4 ")) shouldBe "europe-west4"
    }

    "serve global from the global host" in {
      VertexAILocation.apiHost("global") shouldBe "aiplatform.googleapis.com"
    }

    "serve the eu / us jurisdictions from their rep hosts" in {
      VertexAILocation.apiHost("eu") shouldBe "aiplatform.eu.rep.googleapis.com"
      VertexAILocation.apiHost("us") shouldBe "aiplatform.us.rep.googleapis.com"
    }

    "serve a region from its regional host" in {
      VertexAILocation.apiHost(
        "europe-west4"
      ) shouldBe "europe-west4-aiplatform.googleapis.com"
      VertexAILocation.apiHost("us-central1") shouldBe "us-central1-aiplatform.googleapis.com"
    }

    "give the batch service the same host and the location in the path" in {
      VertexAIBatchPredictionServiceImpl
        .siteBinding("p", "eu")
        .coreUrl shouldBe "https://aiplatform.eu.rep.googleapis.com/v1/projects/p/locations/eu/"
      VertexAIBatchPredictionServiceImpl
        .siteBinding("p", "global")
        .coreUrl shouldBe "https://aiplatform.googleapis.com/v1/projects/p/locations/global/"
    }
  }
}
