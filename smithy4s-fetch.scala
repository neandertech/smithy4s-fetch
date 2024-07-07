package smithy4s_fetch

import org.scalajs.dom.{Fetch, Headers, Request, RequestInfo, Response, URL}
import smithy4s.Endpoint.Middleware
import smithy4s.capability.MonadThrowLike
import smithy4s.client.*
import smithy4s.codecs.BlobEncoder
import smithy4s.http.HttpUriScheme.{Http, Https}
import smithy4s.http.{
  CaseInsensitive,
  HttpMethod,
  HttpRequest,
  HttpUnaryClientCodecs,
  Metadata
}
import smithy4s.json.Json
import smithy4s.{Blob, Endpoint}

import scala.scalajs.js.Promise
import scala.scalajs.js.typedarray.Int8Array

import scalajs.js.JSConverters.*
import smithy4s.http.HttpDiscriminator
import org.scalajs.dom.RequestInit

class SimpleRestJsonFetchClient[
    Alg[_[_, _, _, _, _]]
] private[smithy4s_fetch] (
    service: smithy4s.Service[Alg],
    uri: URL,
    middleware: Endpoint.Middleware[SimpleRestJsonFetchClient.Client],
    codecs: SimpleRestJsonCodecs
) {

  def withMaxArity(maxArity: Int): SimpleRestJsonFetchClient[Alg] =
    changeCodecs(_.copy(maxArity = maxArity))

  def withExplicitDefaultsEncoding(
      explicitDefaultsEncoding: Boolean
  ): SimpleRestJsonFetchClient[Alg] =
    changeCodecs(_.copy(explicitDefaultsEncoding = explicitDefaultsEncoding))

  def withHostPrefixInjection(
      hostPrefixInjection: Boolean
  ): SimpleRestJsonFetchClient[Alg] =
    changeCodecs(_.copy(hostPrefixInjection = hostPrefixInjection))

  def make: Alg[[I, E, O, SI, SO] =>> Promise[O]] =
    service.impl[Promise](
      UnaryClientCompiler[
        Alg,
        Promise,
        SimpleRestJsonFetchClient.Client,
        RequestInfo,
        Response
      ](
        service = service,
        toSmithy4sClient = SimpleRestJsonFetchClient.lowLevelClient(_),
        client = Fetch.fetch(_),
        middleware = middleware,
        makeClientCodecs = codecs.makeClientCodecs(uri),
        isSuccessful = resp => resp.ok
      )
    )

  private def changeCodecs(
      f: SimpleRestJsonCodecs => SimpleRestJsonCodecs
  ): SimpleRestJsonFetchClient[Alg] =
    new SimpleRestJsonFetchClient(
      service,
      uri,
      middleware,
      f(codecs)
    )

}

object SimpleRestJsonFetchClient {
  type Client = RequestInfo => Promise[Response]

  def apply[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg],
      url: String
  ) =
    new SimpleRestJsonFetchClient(
      service = service,
      uri = new URL(url),
      codecs = SimpleRestJsonCodecs,
      middleware = Endpoint.Middleware.noop
    )

  private def lowLevelClient(fetch: Client) =
    new UnaryLowLevelClient[Promise, RequestInfo, Response] {
      override def run[Output](request: RequestInfo)(
          responseCB: Response => Promise[Output]
      ): Promise[Output] =
        fetch(request).`then`(resp => responseCB(resp))
    }
}

private[smithy4s_fetch] object SimpleRestJsonCodecs
    extends SimpleRestJsonCodecs(1024, false, false)

private[smithy4s_fetch] case class SimpleRestJsonCodecs(
    maxArity: Int,
    explicitDefaultsEncoding: Boolean,
    hostPrefixInjection: Boolean
) {
  private val hintMask =
    alloy.SimpleRestJson.protocol.hintMask

  def unsafeFromSmithy4sHttpMethod(
      method: smithy4s.http.HttpMethod
  ): org.scalajs.dom.HttpMethod =
    import smithy4s.http.HttpMethod.*
    import org.scalajs.dom.HttpMethod as FetchMethod
    method match
      case GET       => FetchMethod.GET
      case PUT       => FetchMethod.PUT
      case POST      => FetchMethod.POST
      case DELETE    => FetchMethod.DELETE
      case PATCH     => FetchMethod.PATCH
      case OTHER(nm) => nm.asInstanceOf[FetchMethod]

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
    val protocol = {
      uri.scheme match
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
      if qp.isEmpty then ""
      else
        var b = "?"
        qp.zipWithIndex.map:
          case ((key, values), idx) =>
            if idx != 0 then b += "&"
            b += key
            for
              i <- 0 until values.length
              value = values(i)
            do
              if i == 0 then b += "=" + value
              else b += s"&$key=$value"

        b

    s"$protocol://$hostName$port$path$query"
  }

  def toSmithy4sHttpResponse(
      resp: Response
  ): Promise[smithy4s.http.HttpResponse[Blob]] = {
    resp
      .arrayBuffer()
      .`then`: body =>
        val headers = Map.newBuilder[CaseInsensitive, Seq[String]]

        resp.headers.foreach:
          case arr if arr.size >= 2 =>
            val header = arr(0)
            val values = arr.tail.toSeq
            headers += CaseInsensitive(header) -> values
          case _ =>

        smithy4s.http.HttpResponse(
          resp.status,
          headers.result(),
          Blob(new Int8Array(body).toArray)
        )

  }

  def fromSmithy4sHttpRequest(
      req: smithy4s.http.HttpRequest[Blob]
  ): Request = {
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
    import smithy4s.http.*
    val uriScheme = uri.protocol match {
      case "https:" => HttpUriScheme.Https
      case "http:"  => HttpUriScheme.Http
      case _ =>
        throw UnsupportedOperationException(
          s"Protocol `${uri.protocol}` is not supported"
        )
    }

    HttpUri(
      uriScheme,
      uri.host,
      uri.port.toIntOption,
      uri.pathname
        // drop the guaranteed leading slash, so that we don't produce an empty segment for it
        .tail
        // splitting an empty path would produce a single element, so we special-case to empty
        .match {
          case ""    => IndexedSeq.empty
          case other => other.split("/")
        },
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
        .withExplicitDefaultsEncoding(explicitNulls = true)
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
        Promise.resolve(HttpDiscriminator.fromResponse(errorHeaders, resp))
      )
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(
        Metadata.Encoder.withExplicitDefaultsEncoding(
          explicitDefaultsEncoding
        )
      )
      .withBaseRequest(_ => Promise.resolve(baseRequest))
      .withRequestMediaType("application/json")
      .withRequestTransformation(req =>
        Promise.resolve(fromSmithy4sHttpRequest(req))
      )
      .withResponseTransformation[Response](resp =>
        Promise.resolve(toSmithy4sHttpResponse(resp))
      )
      .withHostPrefixInjection(hostPrefixInjection)
      .build()

  }
}

given MonadThrowLike[Promise] with
  override def map[A, B](fa: Promise[A])(f: A => B): Promise[B] = fa.`then`(f)

  override def flatMap[A, B](fa: Promise[A])(f: A => Promise[B]): Promise[B] =
    fa.`then`(f)

  override def handleErrorWith[A](fa: Promise[A])(
      f: Throwable => Promise[A]
  ): Promise[A] = fa.`catch`:
    case ex: Throwable => f(ex) // TODO: does this make sense?

  override def pure[A](a: A): Promise[A] = Promise.resolve(a)

  override def raiseError[A](e: Throwable): Promise[A] = Promise.reject(e)

  override def zipMapAll[A](seq: IndexedSeq[Promise[Any]])(
      f: IndexedSeq[Any] => A
  ): Promise[A] =
    Promise.all(seq.toJSIterable).`then`(res => Promise.resolve(f(res.toArray)))

  override def zipMap[A, B, C](fa: Promise[A], fb: Promise[B])(
      f: (A, B) => C
  ): Promise[C] = Promise
    .all[Either[A, B]](
      Seq(fa.`then`(Left(_)), fb.`then`(Right(_))).toJSIterable
    )
    .`then`: arr =>
      (arr(0), arr(1)) match
        case (Left(x), Right(y)) => f(x, y)
        case (Right(y), Left(x)) => f(x, y)
        case _                   => ???
