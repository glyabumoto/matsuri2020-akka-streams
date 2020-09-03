package matsuri2020.yabumoto.utility.monitoring

import akka.Done
import matsuri2020.yabumoto.utility.kinesis.AWS
import software.amazon.awssdk.services.cloudwatch.model._

import scala.concurrent._
import scala.jdk.FutureConverters._

object CloudWatchPerformanceMetrics {
  def apply(application: String, index: Int, name: String, bufferCapacity: Int): CloudWatchPerformanceMetrics =
    new CloudWatchPerformanceMetrics(application, index, name, bufferCapacity)
}

class CloudWatchPerformanceMetrics(application: String, index: Int, name: String, bufferCapacity: Int)
    extends PerformanceMetrics {
  import matsuri2020.yabumoto.utility.akkastreams.Threads.blockingIoDispatcher

  val NAMESPACE  = "streaming-performance"
  val cloudWatch = AWS.cloudwatch

  val dimensions = Seq(
    "application" -> application,
    "index"       -> index.toString,
    "name"        -> name
  ).map {
    case (name, value) =>
      Dimension
        .builder()
        .name(name)
        .value(value)
        .build()
  }

  // CloudWatchへの送付処理
  def push(bufferUsed: Int, throughput: Long): Future[Done] = {
    val data = Seq(
      ("bufferUsed", bufferUsed.toDouble, StandardUnit.COUNT),
      ("bufferCapacity", bufferCapacity.toDouble, StandardUnit.COUNT),
      ("bufferUtilization", bufferUsed.toDouble / bufferCapacity.toDouble * 100.0, StandardUnit.PERCENT),
      ("throughput", throughput.toDouble, StandardUnit.COUNT)
    )

    val metricDatumList = data.map {
      case (key, value, unit) =>
        MetricDatum
          .builder()
          .metricName(key)
          .dimensions(dimensions: _*)
          .unit(unit)
          .value(value)
          .build()
    }

    val request = PutMetricDataRequest
      .builder()
      .namespace(NAMESPACE)
      .metricData(metricDatumList: _*)
      .build()

    cloudWatch
      .putMetricData(request)
      .asScala
      .map(_ => Done.getInstance())
  }
}
