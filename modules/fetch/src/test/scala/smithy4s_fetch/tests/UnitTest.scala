package smithy4s_fetch.tests

import smithy4s_fetch.SimpleRestJsonCodecs
import smithy4s.http.HttpUriScheme
import smithy4s.http.HttpUri
import org.scalajs.dom.URL
import weaver.FunSuiteIO

object UnitTest extends FunSuiteIO {
  val uri =
    HttpUri(
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
    SimpleRestJsonCodecs.fromSmithy4sHttpUri(uri)

  test("URI encoding") {
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
  }

  test("Base URI with no path prefix") {
    val result = SimpleRestJsonCodecs
      .toSmithy4sHttpUri(new URL("http://localhost"))
      .path

    expect(result.isEmpty)
  }

  test("Base URI with no path prefix (with slash)") {
    val result = SimpleRestJsonCodecs
      .toSmithy4sHttpUri(new URL("http://localhost/"))
      .path

    expect(result.isEmpty)
  }

  test("Base URI with path prefix") {
    expect.same(
      SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/prefix"))
        .path,
      IndexedSeq("prefix")
    )
  }

  test("Base URI with no path prefix, including empty segments") {
    expect.same(
      SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/foo//bar//baz/"))
        .path,
      IndexedSeq("foo", "", "bar", "", "baz")
    )
  }

  test("Base URI with path prefix, trailing slash doesn't matter") {
    expect.same(
      SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/foo/")),
      SimpleRestJsonCodecs
        .toSmithy4sHttpUri(new URL("http://localhost/foo"))
    )
  }
}
