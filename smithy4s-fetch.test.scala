//> using test.dep com.disneystreaming::weaver-cats::0.8.4
//> using test.dep "tech.neander::smithy4s-deriving::0.0.2"
//> using test.dep com.disneystreaming.smithy4s::smithy4s-http4s::0.18.22
//> using test.dep org.http4s::http4s-ember-server::0.23.27
//> using test.dep org.http4s::http4s-ember-client::0.23.27
//> using testFramework "weaver.framework.CatsEffect"
//> using scala 3.4.2

package smithy4s_fetch.tests

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.comcast.ip4s.port
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import smithy4s.deriving.API
import smithy4s.http.HttpUriScheme
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s_fetch.SimpleRestJsonFetchClient
import weaver.{FunSuiteIO, IOSuite}

import scala.concurrent.duration.*
import scala.scalajs.js.Promise
import smithy4s.http.HttpUri
import org.scalajs.dom.URL

object UnitTest extends FunSuiteIO:
  val uri =
    smithy4s.http.HttpUri(
      scheme = HttpUriScheme.Https,
      path = Vector("hello", "world"),
      queryParams = Map(
        "k" -> Seq.empty,
        "k2" -> Seq("hello"),
        "k3" -> Seq("hello", "world", "!")
      ),
      host = "localhost",
      pathParams = None,
      port = Some(9999)
    )

  def enc(uri: HttpUri): String =
    smithy4s_fetch.SimpleRestJsonCodecs.fromSmithy4sHttpUri(uri)

  test("URI encoding"):
    expect.same(
      enc(uri),
      "https://localhost:9999/hello/world?k&k2=hello&k3=hello&k3=world&k3=!"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty)),
      "https://localhost:9999/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, scheme = HttpUriScheme.Http)),
      "http://localhost:9999/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, host = "hello.com")),
      "https://hello.com:9999/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, port = None)),
      "https://localhost/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, path = Vector.empty)),
      "https://localhost:9999/"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, path = Vector("1", "2", "3"))),
      "https://localhost:9999/1/2/3"
    )

  test("Base URI with no path prefix"):
    val result = smithy4s_fetch.SimpleRestJsonCodecs
      .toSmithy4sHttpUri(new URL("http://localhost"))
      .path

    expect(result.isEmpty)

  test("Base URI with no path prefix (with slash)"):
    val result = smithy4s_fetch.SimpleRestJsonCodecs
      .toSmithy4sHttpUri(new URL("http://localhost/"))
      .path

    expect(result.isEmpty)

  test("Base URI with path prefix"):
    expect.same(
      smithy4s_fetch.SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/prefix"))
        .path,
      IndexedSeq("prefix")
    )

  test("Base URI with no path prefix, including empty segments"):
    expect.same(
      smithy4s_fetch.SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/foo//bar//baz/"))
        .path,
      IndexedSeq("foo", "", "bar", "", "baz")
    )

  test("Base URI with path prefix, trailing slash doesn't matter"):
    expect.same(
      smithy4s_fetch.SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/foo/")),
      smithy4s_fetch.SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/foo"))
    )

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

    val serverUri = routesResource.flatMap: app =>
      EmberServerBuilder
        .default[IO]
        .withPort(port"0")
        .withHttpApp(app)
        .withShutdownTimeout(0.seconds)
        .build
        .map(_.baseUri)

    serverUri.flatMap: uri =>

      val http4sClient = EmberClientBuilder
        .default[IO]
        .build
        .flatMap: httpClient =>
          SimpleRestJsonBuilder(service)
            .client[IO](httpClient)
            .uri(uri)
            .resource
            .map(_.unliftService)

      val fetchClient =
        IO.pure(
          SimpleRestJsonFetchClient(
            promiseService,
            uri.renderString
          ).make.unliftService
        ).toResource

      (http4sClient, fetchClient).mapN((io, fetch) => Probe(uri, io, fetch))

  end sharedResource

  test("hello response"): res =>
    for
      ioResp <- res.ioClient.hello()
      fetchResp <- IO.fromFuture(IO(res.fetchClient.hello().toFuture))
    yield expect.same(ioResp, fetchResp)

  test("stub response"): res =>
    for
      ioResp <- res.ioClient.stub(IP("yo"), "bruh")
      fetchResp <- IO.fromFuture(
        IO(res.fetchClient.stub(IP("yo"), "bruh").toFuture)
      )
    yield expect.same(ioResp, fetchResp)

end IntegrationTest

import smithy4s.*, deriving.{given, *}, aliases.*

import scala.annotation.experimental // the derivation of API uses experimental metaprogramming features, at this time.

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
