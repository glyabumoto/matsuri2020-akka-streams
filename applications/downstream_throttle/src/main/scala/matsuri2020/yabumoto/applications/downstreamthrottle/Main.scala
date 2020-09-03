package matsuri2020.yabumoto.applications.downstreamthrottle

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import akka.util.ByteString
import matsuri2020.yabumoto.utility.logging._
import matsuri2020.yabumoto.utility.akkastreams.Application
import matsuri2020.yabumoto.utility.kinesis._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

object Main extends App {

  import Application.Implicits._
  import matsuri2020.yabumoto.utility.akkastreams.Threads.workerDispatcher

  val http               = Http()
  val separator          = ByteString.fromString(Properties.lineSeparator)
  val separatorBytes     = separator.length
  val MAX_FILE_SIZE      = 10 * 1024 * 1024
  val MAX_BYTES_PER_HOUR = 100 * 1024 * 1024

  val kclSource = KCLSource("scalamatsuri")
  val buffering = Flow[ByteString]
    .batchWeighted[ByteString](MAX_FILE_SIZE, _.length, seed => seed)(_ ++ separator ++ _)
    .throttle(MAX_BYTES_PER_HOUR, Duration(1, HOURS), _.length)
  val send = Flow[ByteString]
    .mapAsync(1) { data =>
      val formData = Multipart.FormData(
        Source.single(
          Multipart.FormData.BodyPart("file",
                                      HttpEntity(ContentTypes.`application/octet-stream`, data),
                                      Map("filename" -> "upload_file.txt"))
        )
      )
      Marshal(formData).to[RequestEntity]
    }
    .mapAsync(1) { entity =>
      val request = HttpRequest(
        method = HttpMethods.POST,
        entity = entity
      )
      http.singleRequest(request)
    }
    .mapAsync(1)(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
  val logging = Sink.foreach[ByteString] { body =>
    logger.info(body.utf8String)
  }

  val request = kclSource
    .via(buffering)
    .via(send)
    .toMat(logging)(Keep.right)
    .run()
  Await.ready(request, Duration.Inf)
}
