package matsuri2020.yabumoto.utility.akkastreams

object Threads {
  import Application.Implicits._

  private val blockingIoDispatcherName = "akka.actor.default-blocking-io-dispatcher"

  implicit def workerDispatcher     = system.dispatcher
  implicit def blockingIoDispatcher = system.dispatchers.lookup(blockingIoDispatcherName)
}
