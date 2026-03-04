package smithy4s_fetch

import org.scalajs.dom.{Headers, Request, RequestInfo, Response, URL}
import smithy4s.Blob
import smithy4s.client._
import smithy4s.codecs.BlobEncoder
import smithy4s.http.HttpUriScheme.{Http, Https}
import smithy4s.http.{
  CaseInsensitive,
  HttpDiscriminator,
  HttpMethod,
  HttpRequest,
  HttpUnaryClientCodecs,
  Metadata
}
import smithy4s.json.Json

import org.scalajs.dom.RequestInit
import scala.scalajs.js.Promise
import scala.scalajs.js.typedarray.Int8Array
import scalajs.js.JSConverters._
import smithy4s.schema.FieldFilter

private[smithy4s_fetch] object SimpleRestJsonCodecs
    extends SimpleRestJsonCodecs(1024, FieldFilter.Default, false)

private[smithy4s_fetch] case class SimpleRestJsonCodecs(
    maxArity: Int,
    fieldFilter: FieldFilter,
    hostPrefixInjection: Boolean
) {
  private val hintMask =
    alloy.SimpleRestJson.protocol.hintMask

  def unsafeFromSmithy4sHttpMethod(
      method: smithy4s.http.HttpMethod
  ): org.scalajs.dom.HttpMethod = {
    import smithy4s.http.HttpMethod._
    import org.scalajs.dom.{HttpMethod => FetchMethod}
    method match {
      case GET       => FetchMethod.GET
      case PUT       => FetchMethod.PUT
      case POST      => FetchMethod.POST
      case DELETE    => FetchMethod.DELETE
      case PATCH     => FetchMethod.PATCH
      case OTHER(nm) => nm.asInstanceOf[FetchMethod]
    }
  }

  def toHeaders(smithyHeaders: Map[CaseInsensitive, Seq[String]]): Headers = {
    val h = new Headers()
    smithyHeaders.foreach { case (name, values) =>
      values.foreach { value =>
        h.append(name.toString, value)
      }
    }
    h
  }

  def fromSmithy4sHttpUri(uri: smithy4s.http.HttpUri): String = {
    val qp = uri.queryParams
    val protocol = uri.scheme match {
      case Http  => "http"
      case Https => "https"
    }
    val hostName = uri.host
    val port =
      uri.port
        .filterNot(p => uri.host.endsWith(s":$p"))
        .map(":" + _.toString)
        .getOrElse("")

    val path = "/" + uri.path.mkString("/")
    val query =
      if (qp.isEmpty) ""
      else {
        var b = "?"
        qp.zipWithIndex.foreach { case ((key, values), idx) =>
          if (idx != 0) b += "&"
          b += key
          for (i <- 0 until values.length) {
            val value = values(i)
            if (i == 0) b += "=" + value
            else b += s"&$key=$value"
          }
        }
        b
      }

    s"$protocol://$hostName$port$path$query"
  }

  def toSmithy4sHttpResponse(
      resp: Response
  ): Promise[smithy4s.http.HttpResponse[Blob]] = {
    resp
      .arrayBuffer()
      .`then`[smithy4s.http.HttpResponse[Blob]] { body =>
        val headers = Map.newBuilder[CaseInsensitive, Seq[String]]

        resp.headers.foreach {
          case arr if arr.size >= 2 =>
            val header = arr(0)
            val values = arr.tail.toSeq
            headers += CaseInsensitive(header) -> values
          case _ =>
        }

        smithy4s.http.HttpResponse(
          resp.status,
          headers.result(),
          Blob(new Int8Array(body).toArray)
        )
      }
  }

  def fromSmithy4sHttpRequest(
      req: smithy4s.http.HttpRequest[Blob]
  ): RequestInfo = {
    val m = unsafeFromSmithy4sHttpMethod(req.method)
    val h = toHeaders(req.headers)
    val ri = new RequestInit {}
    if (req.body.size != 0) {
      val arr = new Int8Array(req.body.size)
      arr.set(
        req.body.toArray.toJSArray,
        0
      )
      ri.body = arr
      h.append("Content-Length", req.body.size.toString)
    }

    ri.method = m
    ri.headers = h

    new Request(fromSmithy4sHttpUri(req.uri), ri)
  }

  def toSmithy4sHttpUri(
      uri: URL,
      pathParams: Option[smithy4s.http.PathParams] = None
  ): smithy4s.http.HttpUri = {
    import smithy4s.http._
    val uriScheme = uri.protocol match {
      case "https:" => HttpUriScheme.Https
      case "http:"  => HttpUriScheme.Http
      case _ =>
        throw new UnsupportedOperationException(
          s"Protocol `${uri.protocol}` is not supported"
        )
    }

    val pathSegments = uri.pathname.tail match {
      case ""    => IndexedSeq.empty[String]
      case other => other.split("/").toIndexedSeq
    }

    HttpUri(
      uriScheme,
      uri.host,
      uri.port.toIntOption,
      pathSegments,
      uri.searchParams
        .entries()
        .toIterator
        .toSeq
        .groupMap(_._1)(_._2)
        .toMap,
      pathParams
    )
  }

  val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter
        .withHintMask(hintMask)
        .withMaxArity(maxArity)
        .withFieldFilter(fieldFilter)
    )

  val payloadEncoders: BlobEncoder.Compiler =
    jsonCodecs.encoders

  val payloadDecoders =
    jsonCodecs.decoders

  val errorHeaders = List(
    smithy4s.http.errorTypeHeader
  )

  def makeClientCodecs(
      uri: URL
  ): UnaryClientCodecs.Make[Promise, RequestInfo, Response] = {
    val baseRequest = HttpRequest(
      HttpMethod.POST,
      toSmithy4sHttpUri(uri, None),
      Map.empty,
      Blob.empty
    )

    HttpUnaryClientCodecs.builder
      .withBodyEncoders(payloadEncoders)
      .withSuccessBodyDecoders(payloadDecoders)
      .withErrorBodyDecoders(payloadDecoders)
      .withErrorDiscriminator(resp =>
        Promise.resolve[HttpDiscriminator](
          HttpDiscriminator.fromResponse(errorHeaders, resp)
        )
      )
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(
        Metadata.Encoder.withFieldFilter(
          fieldFilter
        )
      )
      .withBaseRequest(_ => Promise.resolve[HttpRequest[Blob]](baseRequest))
      .withRequestMediaType("application/json")
      .withRequestTransformation(req =>
        Promise.resolve[RequestInfo](fromSmithy4sHttpRequest(req))
      )
      .withResponseTransformation[Response](resp =>
        Promise.resolve[smithy4s.http.HttpResponse[Blob]](
          toSmithy4sHttpResponse(resp)
        )
      )
      .withHostPrefixInjection(hostPrefixInjection)
      .build()
  }
}
