package matsuri2020.yabumoto.applications.streammonitoring

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import matsuri2020.yabumoto.utility.akkastreams.Application
import matsuri2020.yabumoto.utility.logging._
import matsuri2020.yabumoto.utility.monitoring._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Main extends App {
  import Application.Implicits._

  val decider: Supervision.Decider = {
    case NonFatal(ex) =>
      logger.warn("streaming error", ex)
      Supervision.Resume
    case ex =>
      logger.warn("streaming fatal error", ex)
      Supervision.Stop
  }

  def monitor[A](index: Int, name: String, capacity: Int): Flow[A, A, NotUsed] = {
    val metrics = CloudWatchPerformanceMetrics("scalamatsuri2020", index, name, capacity)
    BufferMetrics(metrics, capacity)
  }

  val sourceInterval  = Duration(30, MILLISECONDS)
  val processInterval = Duration(31, MILLISECONDS)

  Source
    .tick(sourceInterval, sourceInterval, 1)
    .via(monitor(1, "process1", 1000))
    .map(num => num + 100)
    .via(monitor(2, "process2", 1000))
    .mapAsync(1) { num =>
      import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher
      akka.pattern.after(processInterval, system.scheduler)(Future.successful(num))
    }
    .via(monitor(3, "process3", 1000))
    .to(Sink.ignore)
    .run()
}
