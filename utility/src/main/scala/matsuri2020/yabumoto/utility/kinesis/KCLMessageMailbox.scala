package matsuri2020.yabumoto.utility.kinesis

import akka.actor.ActorSystem
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._

import matsuri2020.yabumoto.utility.logging._

class KCLEventMailBox(queue: SourceQueueWithComplete[ByteString]) {
  import matsuri2020.yabumoto.utility.akkastreams.Application.Implicits._
  import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

  /**
    * レコードを登録する
    */
  def offerAll(events: Seq[ByteString]): Long = {
    offer(events: _*)
  }

  /**
    * レコードを登録する
    */
  def offer(events: ByteString*): Long = {
    events.map(entry(_)).sum
  }

  private def entry(record: ByteString): Long = queue.synchronized {
    val offerCount = queue.offer(record).flatMap {
      case QueueOfferResult.Enqueued => Future.successful(1L)
      case QueueOfferResult.Dropped =>
        akka.pattern.after(Duration(1, MILLISECONDS), system.scheduler)(Future { entry(record) })
      case QueueOfferResult.Failure(ex) =>
        logger.warn("kcl event: queue offer error. records is dropped.", ex)
        Future.successful(0L)
      case QueueOfferResult.QueueClosed =>
        logger.error("kcl event: queue closed before to receive all data.")
        Future.failed(new Exception("queue closed before to receive all data."))
    }
    Await.result(offerCount, Duration.Inf)
  }
}
