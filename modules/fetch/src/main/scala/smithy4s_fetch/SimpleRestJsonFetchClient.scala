package smithy4s_fetch

import org.scalajs.dom.{RequestInfo, Response}
import smithy4s.client._
import smithy4s.schema.FieldFilter
import smithy4s.Endpoint

import scala.scalajs.js.Promise

class SimpleRestJsonFetchClient[Alg[_[_, _, _, _, _]]] private[smithy4s_fetch] (
    val service: smithy4s.Service[Alg],
    uri: org.scalajs.dom.URL,
    middleware: Endpoint.Middleware[SimpleRestJsonFetchClient.Client],
    codecs: SimpleRestJsonCodecs
) {

  def withMaxArity(maxArity: Int): SimpleRestJsonFetchClient[Alg] =
    changeCodecs(
      _.copy(maxArity = maxArity)
    )

  def withFieldFilter(
      fieldFilter: FieldFilter
  ): SimpleRestJsonFetchClient[Alg] = changeCodecs(
    _.copy(fieldFilter = fieldFilter)
  )

  def withHostPrefixInjection(
      hostPrefixInjection: Boolean
  ): SimpleRestJsonFetchClient[Alg] = changeCodecs(
    _.copy(hostPrefixInjection = hostPrefixInjection)
  )

  def make: Alg[smithy4s.kinds.Kind1[Promise]#toKind5] = {
    val c: service.FunctorEndpointCompiler[Promise] =
      UnaryClientCompiler[
        Alg,
        Promise,
        SimpleRestJsonFetchClient.Client,
        RequestInfo,
        Response
      ](
        service = service,
        client = org.scalajs.dom.Fetch.fetch(_),
        // Note: on 2.13, moving this up breaks compilation
        toSmithy4sClient = SimpleRestJsonFetchClient.lowLevelClient(_),
        makeClientCodecs = codecs.makeClientCodecs(uri),
        middleware = middleware,
        isSuccessful = resp => resp.ok
      )

    service.impl[Promise](
      c
    )
  }

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
  type SuspendedPromise[A] = () => Promise[A]

  type Client = RequestInfo => Promise[Response]

  def apply[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg],
      url: String
  ) =
    new SimpleRestJsonFetchClient[Alg](
      service = service,
      uri = new org.scalajs.dom.URL(url),
      codecs = SimpleRestJsonCodecs,
      middleware = Endpoint.Middleware.noop
    )

  private[smithy4s_fetch] def lowLevelClient(fetch: Client) =
    new UnaryLowLevelClient[Promise, RequestInfo, Response] {
      override def run[Output](
          request: RequestInfo
      )(
          responseCB: Response => Promise[Output]
      ): Promise[Output] =
        fetch(request).`then`[Output](resp => responseCB(resp))
    }

}
