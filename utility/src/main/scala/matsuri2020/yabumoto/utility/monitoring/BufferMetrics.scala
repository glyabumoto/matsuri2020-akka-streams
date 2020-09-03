package matsuri2020.yabumoto.utility.monitoring

import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

class BufferMetrics[A](metrics: PerformanceMetrics, bufferSize: Int = 1000)(implicit system: ActorSystem)
    extends GraphStage[FlowShape[A, A]] {
  import matsuri2020.yabumoto.utility.akkastreams.Threads.blockingIoDispatcher

  val in                              = Inlet[A]("BufferMetrics.in")
  val out                             = Outlet[A]("BufferMetrics.out")
  override def shape: FlowShape[A, A] = FlowShape(in, out)
  val interval                        = Duration(1, MINUTES)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val buffer            = mutable.Queue[A]()
    def bufferFull        = buffer.size == bufferSize
    var downstreamWaiting = false

    @volatile
    var throughputCounter: Long = 0L

    // ログの定期実行
    val cancelable = system.scheduler.scheduleAtFixedRate(interval, interval) {
      new Runnable {
        override def run(): Unit = {
          // 処理データ件数を取得してカウンタをリセット
          val throughput = throughputCounter
          throughputCounter -= throughput

          metrics.push(buffer.size, throughput)
        }
      }
    }

    override def preStart(): Unit = {
      pull(in)
    }

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          buffer.enqueue(elem)
          if (downstreamWaiting) {
            downstreamWaiting = false
            val bufferedElem = buffer.dequeue()
            push(out, bufferedElem)
            throughputCounter += 1
          }
          if (!bufferFull) {
            pull(in)
          }
        }

        override def onUpstreamFinish(): Unit = {
          if (buffer.nonEmpty) {
            emitMultiple(out, buffer.iterator)
          }
          cancelable.cancel()
          completeStage()
        }
      }
    )

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = {
          if (buffer.isEmpty) {
            downstreamWaiting = true
          } else {
            downstreamWaiting = false
            val elem = buffer.dequeue()
            push(out, elem)
            throughputCounter += 1
          }
          if (!bufferFull && !hasBeenPulled(in)) {
            pull(in)
          }
        }
      }
    )
  }
}

object BufferMetrics {
  def apply[A](metrics: PerformanceMetrics, bufferSize: Int = 1000)(
      implicit system: ActorSystem
  ): Flow[A, A, NotUsed] = {
    Flow.fromGraph(new BufferMetrics[A](metrics, bufferSize))
  }
}

trait PerformanceMetrics {
  def push(bufferUsed: Int, throughput: Long): Future[Done]
}
