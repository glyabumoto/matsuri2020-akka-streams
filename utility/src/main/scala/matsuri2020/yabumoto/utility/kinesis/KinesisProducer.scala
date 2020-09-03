package matsuri2020.yabumoto.utility.kinesis

import akka.actor.ActorSystem
import akka._
import akka.stream._
import akka.stream.scaladsl._

import scala.jdk.FutureConverters._
import software.amazon.awssdk.services.kinesis.model._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

import matsuri2020.yabumoto.utility.logging._

class KinesisProducer(streamName: String, setting: KinesisProducerSetting) {
  private val ONE_SECOND = Duration(1, SECONDS)

  private val incomingQueueSource =
    Source.queue[KinesisStreamObject](setting.bufferCount, OverflowStrategy.backpressure)
  private val retryQueueSource = Source.queue[KinesisStreamObject](setting.bufferCount, OverflowStrategy.backpressure)
  private var isClosing        = false
  private val MAX_TRY_COUNT    = 10

  /**
    * Kinesisへの送付
    */
  private val sendFlow = Flow[KinesisStreamObject]
    .via(PutRecordsRequestEntryBuffer(setting.maxRecordsPerRequest, setting.maxBytesPerRequest))
    .throttle(setting.maxRequestPerSecond, ONE_SECOND)
    .throttle(setting.maxRecordsPerSecond, ONE_SECOND, _.length)
    .throttle(setting.maxBytesPerSecond, ONE_SECOND, _.map(_.byteLength).sum)
    .mapAsync(setting.parallelism)(send(_))

  private def send(records: Seq[KinesisStreamObject]) = {
    import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

    val request = PutRecordsRequest
      .builder()
      .streamName(streamName)
      .records(records.map(_.toEntry).toList.asJava)
      .build()

    AWS.kinesis
      .putRecords(request)
      .asScala
      .map { response =>
        val retryRecords =
          response
            .records()
            .asScala
            .zip(records)
            .flatMap {
              case (result, record) if (Option(result.errorCode()).isDefined && canRetry(record, result)) =>
                Option(record.toRetryRecord)
              case _ => None
            }
        val successCount = records.length - response.failedRecordCount()
        retryRecords.toSeq -> successCount
      }
      .recover {
        case NonFatal(ex) =>
          logger.warn("kinesis producer warning: put records error", ex)
          records -> 0
      }
  }

  def sendRecovery(records: Seq[KinesisStreamObject]): Future[Int] = {
    import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

    send(records).flatMap {
      case (records, result) if (records.nonEmpty) =>
        sendRecovery(records).map(_ + result)
      case (_, result) => Future.successful(result)
    }
  }

  private def canRetry(record: KinesisStreamObject, result: PutRecordsResultEntry): Boolean = {
    def errorMessage = s"[${Option(result.errorCode()).mkString}]${Option(result.errorMessage()).mkString}"
    if (record.tryCount < MAX_TRY_COUNT) {
      true
    } else {
      logger.warn(s"kinesis record error: record reach max try count:${record.tryCount}. ${errorMessage}")
      false
    }
  }

  /**
    * リトライオファー
    */
  private val offerRetry = {
    import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

    Flow[(Seq[KinesisStreamObject], Int)]
      .mapAsync(setting.offerThreadCount) {
        case (failedRecords, successCount) =>
          entryRetry(failedRecords).map { lastRecovered =>
            successCount + lastRecovered
          }
      }
  }

  /**
    * ログ出力
    */
  private val counterSink = {
    var counter = 0
    Sink
      .foreach[Int] { incoming =>
        counter += incoming
        if (setting.loggingThreshold <= counter) {
          logger.info(s"kinesis producer: send ${counter} records to ${streamName}")
          counter = 0
        }
        Done.getInstance()
      }
  }

  private val (incomingQueue, retryQueue, matValue) = {
    import matsuri2020.yabumoto.utility.akkastreams.Application.Implicits._

    val graph =
      RunnableGraph.fromGraph(GraphDSL.create(incomingQueueSource, retryQueueSource, counterSink)((_, _, _)) {
        implicit builder => (queue, retryQueue, counter) =>
          {
            import GraphDSL.Implicits._
            val merge = builder.add(Merge[KinesisStreamObject](2))

            queue ~> merge.in(0)
            retryQueue ~> merge.in(1)

            merge.out ~> sendFlow ~> offerRetry ~> counter

            ClosedShape
          }
      })
    graph.run()
  }

  private def entryIncoming(record: KinesisStreamObject)(implicit ec: ExecutionContext) = {
    entry(incomingQueue)(record)
  }

  private def entryRetry(records: Seq[KinesisStreamObject]): Future[Int] = {
    import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

    if (isClosing) {
      sendRecovery(records)
    } else {
      records
        .foldLeft(Future.successful(())) { (future, record) =>
          future.flatMap(_ => entry(retryQueue)(record))
        }
        .map(_ => 0)
        .recoverWith {
          case NonFatal(ex) =>
            logger.debug("kinesis producer: queue error before retry", ex)
            sendRecovery(records)
        }
    }
  }

  private def entry(
      queue: SourceQueueWithComplete[KinesisStreamObject]
  )(record: KinesisStreamObject)(implicit ec: ExecutionContext): Future[Unit] = {
    queue.offer(record).flatMap {
      case QueueOfferResult.Enqueued => Future.successful(())
      case QueueOfferResult.Dropped =>
        entry(queue)(record)
      case QueueOfferResult.QueueClosed =>
        Future.failed(new Exception(s"kinesis producer closed for ${streamName}"))
      case unknown =>
        Future.failed(new Exception(s"kinesis producer error for ${streamName}. status=${unknown}"))
    }
  }

  def createSink(): Sink[KinesisStreamObject, Future[Done]] = {
    import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

    Flow[KinesisStreamObject]
      .mapAsync(1)(entryIncoming(_))
      .toMat(Sink.ignore)(Keep.right)
      .mapMaterializedValue { future =>
        future.flatMap { _ =>
          logger.info("kinesis producer: complete push to queue")
          isClosing = true
          incomingQueue.complete()
          retryQueue.complete()
          matValue
        }
      }
  }
}

object KinesisProducer {
  def apply(
      streamName: String,
      setting: KinesisProducerSetting
  )(implicit system: ActorSystem, materializer: Materializer): Sink[KinesisStreamObject, Future[Done]] = {
    val producer = new KinesisProducer(streamName, setting)
    producer.createSink()
  }
}

case class KinesisProducerSetting(
    parallelism: Int,
    maxRequestPerSecond: Int,
    maxRecordsPerSecond: Int,
    maxBytesPerSecond: Int,
    maxRecordsPerRequest: Int,
    maxBytesPerRequest: Int,
    bufferCount: Int,
    offerThreadCount: Int,
    loggingThreshold: Int
)

object KinesisProducerSetting {
  val OVER_THROTTLE_RATE               = 1.25
  val MAX_RECORDS_PER_REQUEST          = 500
  val MAX_BYTES_PER_REQUEST            = 1024 * 1024
  val MAX_RECORDS_PER_SHARD_PER_SECOND = 1000 * OVER_THROTTLE_RATE
  val MAX_BYTES_PER_SHARD_PER_SECOND   = 1024 * 1024 * OVER_THROTTLE_RATE
  val MAX_REQUEST_PER_SECOND           = 10
  val MAX_RETRY_COUNT                  = 10000
  val MAX_OFFER_THREAD                 = 20
  val DEFAULT_BUFFER_LENGTH            = 10000
  val DEFAULT_LOGGING_INTERVAL         = 1000000
  val DEFAULT_OFFER_THREAD_COUNT       = 20
  val LOGGING_THRESHOLD                = 1000000

  def fromShardCount(shardCount: Int) = KinesisProducerSetting(
    DEFAULT_OFFER_THREAD_COUNT,
    shardCount * MAX_REQUEST_PER_SECOND,
    (shardCount * MAX_RECORDS_PER_SHARD_PER_SECOND).toInt,
    (shardCount * MAX_BYTES_PER_SHARD_PER_SECOND).toInt,
    MAX_RECORDS_PER_REQUEST,
    MAX_BYTES_PER_REQUEST,
    shardCount * DEFAULT_BUFFER_LENGTH,
    shardCount * DEFAULT_OFFER_THREAD_COUNT,
    LOGGING_THRESHOLD
  )
}
