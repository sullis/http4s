package org.http4s
package server

import java.net.URL

import cats.effect.IO
import org.http4s.dsl.Http4sDsl
import org.specs2.specification.AfterAll

import scala.concurrent.ExecutionContext.global
import scala.io.Source

trait ServerContext extends Http4sDsl[IO] with AfterAll {
  def builder: ServerBuilder[IO]

  val server = builder
    .bindAny()
    .withExecutionContext(global)
    .mountService(HttpService {
      case GET -> Root / "thread" / "routing" =>
        val thread = Thread.currentThread.getName
        Ok(thread)

      case GET -> Root / "thread" / "effect" =>
        IO(Thread.currentThread.getName).flatMap(Ok(_))
    })
    .start
    .unsafeRunSync()

  def afterAll = server.shutdownNow()
}

trait ServerSpec extends Http4sSpec with ServerContext {
  def get(path: String): IO[String] = IO {
    Source.fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path")).getLines.mkString
  }

  "A server" should {
    val globalExecutorThreadPrefix = BuildInfo.scalaVersion match {
      case v if v.startsWith("2.11.") => "ForkJoinPool-"
      case _ => "scala-execution-context-global-"
    }

    "route requests on the service executor" in {
      get("/thread/routing").unsafeRunSync must startWith(globalExecutorThreadPrefix)
    }

    "execute the service task on the service executor" in {
      get("/thread/effect").unsafeRunSync must startWith(globalExecutorThreadPrefix)
    }
  }
}
