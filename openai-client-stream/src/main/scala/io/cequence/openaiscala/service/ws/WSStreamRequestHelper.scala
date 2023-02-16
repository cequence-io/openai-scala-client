package io.cequence.openaiscala.service.ws

import akka.NotUsed
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import io.cequence.openaiscala.{OpenAIScalaClientException, OpenAIScalaClientTimeoutException, OpenAIScalaClientUnknownHostException}
import play.api.libs.json.{JsNull, JsObject, JsString, JsValue, Json}
import play.api.libs.ws.JsonBodyWritables._

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Stream request support specifically tailored for OpenAI API.
 *
 * @since Feb 2023
 */
trait WSStreamRequestHelper {
  this: WSRequestHelper =>

  private val itemPrefix = "data: "
  private val endOfStreamToken = "[DONE]"

  private implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json()

  private implicit val jsonMarshaller: Unmarshaller[ByteString, JsValue] =
    Unmarshaller.strict[ByteString, JsValue] { byteString =>
      val data = byteString.utf8String.stripPrefix(itemPrefix)
      if (data.equals(endOfStreamToken)) JsString(endOfStreamToken) else Json.parse(data)
    }

  protected def execJsonStreamAux(
    endPoint: PEP#Value,
    method: String,
    endPointParam: Option[String] = None,
    params: Seq[(PT#Value, Option[Any])] = Nil,
    bodyParams: Seq[(PT#Value, Option[JsValue])] = Nil)(
    implicit materializer: Materializer
  ): Source[JsValue, NotUsed] = {
    val source = execStreamRequestAux(
      endPoint,
      method,
      endPointParam,
      params,
      bodyParams,
      Framing.delimiter(ByteString("\n\n"), 1000, allowTruncation = true),
      {
        case e: JsonParseException => throw new OpenAIScalaClientException(s"$serviceName.$endPoint: 'Response is not a JSON. ${e.getMessage}.")
        case e: FramingException => throw new OpenAIScalaClientException(s"$serviceName.$endPoint: 'Response is not a JSON. ${e.getMessage}.")
      }
    )

    // take until you encounter the end of stream marked with DONE
    source.takeWhile(_ != JsString(endOfStreamToken))
  }

  protected def execStreamRequestAux[T](
    endPoint: PEP#Value,
    method: String,
    endPointParam: Option[String],
    params: Seq[(PT#Value, Option[Any])],
    bodyParams: Seq[(PT#Value, Option[JsValue])],
    framing: Flow[ByteString, ByteString, NotUsed],
    recoverBlock: PartialFunction[Throwable, T])(
    implicit um: Unmarshaller[ByteString, T], materializer: Materializer
  ): Source[T, NotUsed] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)

    val requestWithBody = if (bodyParams.nonEmpty) {
      val bodyParamsX = bodyParams.collect { case (fieldName, Some(jsValue)) => (fieldName.toString, jsValue) }
      request.withBody(JsObject(bodyParamsX))
    } else
      request

    val source =
      requestWithBody.withMethod(method).stream().map { response =>
        response
          .bodyAsSource
          .via(framing)
          .mapAsync(1)(bytes => Unmarshal(bytes).to[T])  // unmarshal one by one
          .recover {
            case e: TimeoutException => throw new OpenAIScalaClientTimeoutException(s"$serviceName.$endPoint timed out: ${e.getMessage}.")
            case e: UnknownHostException => throw new OpenAIScalaClientUnknownHostException(s"$serviceName.$endPoint cannot resolve a host name: ${e.getMessage}.")
          }
          .recover(recoverBlock) // extra recover
      }

    Source.futureSource(source).mapMaterializedValue(_ => NotUsed)
  }
}
