import scala.scalajs.js.Promise
import smithy4s.capability.MonadThrowLike
import scalajs.js.JSConverters._
import scala.scalajs.js.|
import scala.scalajs.js.Thenable

package object smithy4s_fetch {

  implicit val monadThrowLikeForPromise: MonadThrowLike[Promise] =
    new MonadThrowLike[Promise] {
      override def map[A, B](fa: Promise[A])(f: A => B): Promise[B] =
        fa.`then`((a: A) => f(a): (B | Thenable[B]))

      override def flatMap[A, B](
          fa: Promise[A]
      )(
          f: A => Promise[B]
      ): Promise[B] = fa.`then`((a: A) => f(a): (B | Thenable[B]))

      override def handleErrorWith[A](
          fa: Promise[A]
      )(
          f: Throwable => Promise[A]
      ): Promise[A] = fa.`catch`(
        scala.scalajs.js
          .defined {
            {
              case e: Throwable => f(e)
              case _            => fa // re-raise
            }: scala.scalajs.js.Function1[
              Any,
              A | Thenable[A]
            ]
          }
      )

      override def pure[A](a: A): Promise[A] = Promise.resolve[A](a)

      override def raiseError[A](e: Throwable): Promise[A] = Promise.reject(e)

      override def zipMapAll[A](
          seq: IndexedSeq[Promise[Any]]
      )(
          f: IndexedSeq[Any] => A
      ): Promise[A] = Promise
        .all(seq.toJSIterable)
        .`then`((res: scala.scalajs.js.Array[Any]) =>
          f(res.toIndexedSeq): (A | Thenable[A])
        )

      override def zipMap[A, B, C](
          fa: Promise[A],
          fb: Promise[B]
      )(
          f: (A, B) => C
      ): Promise[C] = Promise
        .all[Either[A, B]](
          Seq(
            fa.`then`((a: A) =>
              Left(a): (Either[A, B] | Thenable[Either[A, B]])
            ),
            fb.`then`((b: B) =>
              Right(b): (Either[A, B] | Thenable[Either[A, B]])
            )
          ).toJSIterable
        )
        .`then` { (arr: scala.scalajs.js.Array[Either[A, B]]) =>
          ((arr(0), arr(1)) match {
            case (Left(x), Right(y)) => f(x, y)
            case (Right(y), Left(x)) => f(x, y)
            case _                   => ???
          }): (C | Thenable[C])
        }
    }

}
