package matsuri2020.yabumoto.utility.kinesis

import akka.util.ByteString
import software.amazon.kinesis.lifecycle.events._
import software.amazon.kinesis.processor._
import matsuri2020.yabumoto.utility.logging._
import software.amazon.kinesis.exceptions.ShutdownException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class KCLWorker(mailBox: KCLEventMailBox) extends ShardRecordProcessor {
  import matsuri2020.yabumoto.utility.akkastreams.Application.Implicits._

  private var shardId: Option[String] = None

  override def initialize(initializationInput: InitializationInput) = {
    shardId = Option(initializationInput.shardId())
    logger.info(s"kcl event:start KCL shard:${initializationInput.shardId()}")
  }

  override def processRecords(processRecordsInput: ProcessRecordsInput): Unit = {
    val records = processRecordsInput
      .records()
      .asScala
      .map(_.data())
      .map(ByteString.fromByteBuffer(_))
      .toSeq

    if (records.length > 0) {
      try {
        mailBox.offerAll(records)
      } catch {
        case NonFatal(ex) =>
          logger.warn("kcl event:processRecords error", ex)
      }
    }
  }

  override def leaseLost(leaseLostInput: LeaseLostInput): Unit = {
    logger.info(s"kcl event: leaseLost by ${shardId.mkString}")
    shardId = None
  }

  override def shardEnded(shardEndedInput: ShardEndedInput): Unit = {
    check(shardEndedInput.checkpointer())
    logger.info(s"kcl event: shardEnded by ${shardId.mkString}")
  }

  override def shutdownRequested(shutdownRequestedInput: ShutdownRequestedInput): Unit = {
    check(shutdownRequestedInput.checkpointer())
    logger.info(s"kcl event: shutdownRequested by ${shardId.mkString}")
  }

  def shutdown() = {
    logger.info("kcl event: shutdown worker by ${shardId.mkString}")
  }

  def check(checkPointer: RecordProcessorCheckpointer): Future[Int] = {
    import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher
    implicit val scheduler = system.scheduler

    def checkpoint(): Future[Int] = {
      val future = Future {
        checkPointer.checkpoint()
      }
      future
        .map(_ => 1)
        .recover {
          case ex: ShutdownException =>
            logger.warn(s"can't update checkpoint:${checkPointer.checkpointer().operation()}", ex)
            0
        }
    }

    akka.pattern
      .retry(checkpoint _, 10, Duration(1, SECONDS))
      .recover {
        case NonFatal(ex) =>
          logger.warn(
            s"can't update checkpoint:retry max count. can't update checkpoint:${checkPointer.checkpointer().operation()}",
            ex
          )
          0
      }
  }
}
