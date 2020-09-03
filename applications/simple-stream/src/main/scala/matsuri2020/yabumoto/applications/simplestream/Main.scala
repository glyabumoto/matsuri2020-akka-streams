package matsuri2020.yabumoto.applications.simplestream

import java.nio.ByteBuffer
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

import akka.stream.scaladsl._
import akka.util.ByteString
import matsuri2020.yabumoto.entity.{LogType, PurchaseLog, UserLog}
import matsuri2020.yabumoto.utility.akkastreams.Application
import matsuri2020.yabumoto.utility.kinesis._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  import Application.Implicits._
  import PurchaseLog.jsonFormat._
  import UserLog.jsonFormat._

  val kclSource = KCLSource("scalamatsuri")
  val producer = Flow[UserLog]
    .map(_.toJson.compactPrint)
    .map(ByteString.fromString(_))
    .map(KinesisStreamObject(UUID.randomUUID().toString, _))
    .toMat(KinesisProducer("scalamatsuri2", KinesisProducerSetting.fromShardCount(1)))(Keep.right)
  val extract = Flow.fromFunction[ByteString, PurchaseLog](_.utf8String.parseJson.convertTo[PurchaseLog])
  val convert = Flow[PurchaseLog].mapConcat { log =>
    log.items.map { item =>
      UserLog(
        log.userId,
        log.time,
        LogType.PURCHASE,
        Option(item.itemId),
        Option(item.price),
        Option(item.quantity),
        LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
      )
    }
  }

  val result =
    kclSource
      .via(extract)
      .via(convert)
      .toMat(producer)(Keep.right)
      .run()

  Await.ready(result, Duration.Inf)
}
