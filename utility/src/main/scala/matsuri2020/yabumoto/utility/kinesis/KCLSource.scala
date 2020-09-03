package matsuri2020.yabumoto.utility.kinesis

import akka.NotUsed
import akka.stream.scaladsl._
import akka.stream._
import akka.stream.stage._
import akka.util.ByteString

import scala.concurrent._
import scala.util.control.NonFatal
import matsuri2020.yabumoto.utility.logging._

class KCLSource(
    bufferSize: Int,
    streamName: String,
    useExpandedFanOut: Boolean = false
) extends GraphStage[SourceShape[Future[Option[ByteString]]]] {
  import matsuri2020.yabumoto.utility.akkastreams.Application.Implicits._

  private val decider: Supervision.Decider = {
    case NonFatal(ex) =>
      logger.error(s"kcl record error", ex)
      Supervision.Resume
    case ex =>
      logger.error(s"kcl record fatal error", ex)
      Supervision.Stop
  }

  val out: Outlet[Future[Option[ByteString]]] = Outlet("KCLSource")

  override def shape: SourceShape[Future[Option[ByteString]]] = SourceShape(out)

  private val (sourceQueue, sinkQueue) =
    Source
      .queue[ByteString](bufferSize, OverflowStrategy.backpressure)
      .toMat(Sink.queue[ByteString]())(Keep.both)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .run()

  val mailBox = new KCLEventMailBox(sourceQueue)

  val kclSystem = new KCLSystem(mailBox)(streamName, useExpandedFanOut)
  system.registerOnTermination { shutdown() }

  def shutdown() = {
    kclSystem.shutdown()
    sourceQueue.complete()
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      override def preStart(): Unit = {
        kclSystem.start()
      }

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            push(out, sinkQueue.pull())
          }
          override def onDownstreamFinish(cause: Throwable): Unit = {
            shutdown()
            sinkQueue.cancel()
            super.onDownstreamFinish(cause)
          }
        }
      )
    }
}

object KCLSource {
  /**
    * KCLV2を利用したSourceを作成する
    */
  def apply(
      streamName: String,
      useExpandedFanOut: Boolean = false,
      bufferSize: Int = 50000
  ): Source[ByteString, NotUsed] = {
    val sourceGraph = new KCLSource(bufferSize, streamName, useExpandedFanOut)
    Source
      .fromGraph(sourceGraph)
      .mapAsync(1)(r => r)
      .mapConcat(_.toList)
  }
}
