package matsuri2020.yabumoto.applications.kinesispublisher

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

import akka._
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import matsuri2020.yabumoto.entity._
import matsuri2020.yabumoto.utility.akkastreams.Application
import matsuri2020.yabumoto.utility.kinesis._
import matsuri2020.yabumoto.utility.logging._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

object Main extends App {
  import Application.Implicits._
  import PurchaseLog.jsonFormat._

  def item = Item(
    s"item_${Random.between(1, 1000000)}",
    Random.between(1000, 10000),
    Random.between(1, 10)
  )

  def purchaseLog = PurchaseLog(
    UUID.randomUUID().toString,
    (1 to Random.nextInt(10)).map(_ => item),
    LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
  )

  val producer = KinesisProducer("scalamatsuri", KinesisProducerSetting.fromShardCount(1))

  val result = Source
    .repeat(NotUsed.getInstance())
    .map(_ => ByteString.fromString(purchaseLog.toJson.compactPrint))
    .map(KinesisStreamObject(UUID.randomUUID().toString, _))
    .toMat(producer)(Keep.right)
    .run()

  Await.ready(result, Duration.Inf)
}
