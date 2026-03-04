package smithy4s_fetch.tests

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import com.comcast.ip4s.port
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import smithy4s.deriving.API
import smithy4s.http.HttpUriScheme
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s_fetch.SimpleRestJsonFetchClient
import weaver.IOSuite

import scala.concurrent.duration._
import scala.scalajs.js.Promise
import smithy4s.http.HttpUri
import org.scalajs.dom.URL

import smithy4s._, deriving.{given, _}, aliases._

import scala.annotation.experimental

trait Routes[F[_]]:
  def hello(): F[IP]
  def stub(ip: IP, name: String): F[StubResponse]

@experimental
@simpleRestJson
class IOService() extends Routes[IO] derives API:
  @readonly
  @httpGet("/httpbin/ip")
  override def hello(): IO[IP] = IO.pure(IP("127.0.0.1"))

  @httpDelete("/httpbin/delete")
  override def stub(
      ip: IP,
      @httpQuery("username") name: String
  ): IO[StubResponse] =
    IO.pure(
      StubResponse(s"hello, $name", "http://localhost", Map.empty, myIP = ip)
    )

@experimental
trait PromiseService extends Routes[Promise] derives API:
  @readonly
  @httpGet("/httpbin/ip")
  override def hello(): Promise[IP]

  @httpDelete("/httpbin/delete")
  override def stub(
      ip: IP,
      @httpQuery("username") name: String
  ): Promise[StubResponse]

case class IP(origin: String) derives Schema
case class StubResponse(
    origin: String,
    url: String,
    headers: Map[String, String],
    myIP: IP
) derives Schema

@annotation.experimental
object IntegrationTest extends IOSuite:
  val service = API.service[IOService]
  val promiseService = API.service[PromiseService]

  val routesResource =
    SimpleRestJsonBuilder
      .routes(IOService().liftService[IO])
      .resource
      .map(_.orNotFound)

  case class Probe(
      serverUri: Uri,
      ioClient: IOService,
      fetchClient: PromiseService
  )

  override type Res = Probe
  override def sharedResource: Resource[IO, Res] =

    val serverUri = routesResource.flatMap { app =>
      EmberServerBuilder
        .default[IO]
        .withPort(port"0")
        .withHttpApp(app)
        .withShutdownTimeout(0.seconds)
        .build
        .map(_.baseUri)
    }

    serverUri.flatMap { uri =>

      val http4sClient = EmberClientBuilder
        .default[IO]
        .build
        .flatMap { httpClient =>
          SimpleRestJsonBuilder(service)
            .client[IO](httpClient)
            .uri(uri)
            .resource
            .map(_.unliftService)
        }

      val fetchClient =
        IO.pure(
          SimpleRestJsonFetchClient(
            promiseService,
            uri.renderString
          ).make.unliftService
        ).toResource

      (http4sClient, fetchClient).mapN((io, fetch) => Probe(uri, io, fetch))
    }

  end sharedResource

  test("hello response") { res =>
    for {
      ioResp <- res.ioClient.hello()
      fetchResp <- IO.fromFuture(IO(res.fetchClient.hello().toFuture))
    } yield expect.same(ioResp, fetchResp)
  }

  test("stub response") { res =>
    for {
      ioResp <- res.ioClient.stub(IP("yo"), "bruh")
      fetchResp <- IO.fromFuture(
        IO(res.fetchClient.stub(IP("yo"), "bruh").toFuture)
      )
    } yield expect.same(ioResp, fetchResp)
  }

end IntegrationTest
