package matsuri2020.yabumoto.utility.akkastreams

import akka.actor.ActorSystem
import akka.stream.Materializer

object Application {
  private val actorSystem: ActorSystem        = ActorSystem()
  private val actorMaterializer: Materializer = Materializer.createMaterializer(actorSystem)

  object Implicits {
    implicit def system: ActorSystem        = actorSystem
    implicit def materializer: Materializer = actorMaterializer
  }
}
