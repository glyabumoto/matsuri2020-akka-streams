package matsuri2020.yabumoto.utility.kinesis

import java.util.UUID

import akka.actor.ActorSystem
import software.amazon.kinesis.common.ConfigsBuilder
import software.amazon.kinesis.coordinator.Scheduler
import software.amazon.kinesis.processor.{ShardRecordProcessor, ShardRecordProcessorFactory}
import software.amazon.kinesis.retrieval.polling.PollingConfig

import scala.collection.mutable.ArrayBuffer

class KCLSystem(mailBox: KCLEventMailBox)(streamName: String, useExpandedFanOut: Boolean = false)(
    implicit system: ActorSystem
) {
  private val workers  = ArrayBuffer[KCLWorker]()
  private val uniqueId = UUID.randomUUID().toString

  val configsBuilder = new ConfigsBuilder(
    streamName,
    s"akka-streams-test",
    AWS.kinesis,
    AWS.dynamodb,
    AWS.cloudwatch,
    uniqueId,
    new ShardRecordProcessorFactory {
      override def shardRecordProcessor(): ShardRecordProcessor = {
        val worker = new KCLWorker(mailBox)
        workers += worker
        worker
      }
    }
  )

  def retrievalConfig =
    if (useExpandedFanOut) configsBuilder.retrievalConfig()
    else configsBuilder.retrievalConfig().retrievalSpecificConfig(new PollingConfig(streamName, AWS.kinesis))

  lazy val scheduler = new Scheduler(
    configsBuilder.checkpointConfig(),
    configsBuilder.coordinatorConfig(),
    configsBuilder.leaseManagementConfig(),
    configsBuilder.lifecycleConfig(),
    configsBuilder.metricsConfig(),
    configsBuilder.processorConfig(),
    retrievalConfig
  )

  def start() = {
    val schedulerThread = new Thread(scheduler)
    schedulerThread.setDaemon(true)
    schedulerThread.start()
  }

  def shutdown() = {
    scheduler.shutdown()
    workers.foreach(_.shutdown())
  }
}
