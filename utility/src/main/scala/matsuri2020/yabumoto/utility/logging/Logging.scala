package matsuri2020.yabumoto.utility.logging

import org.slf4j.LoggerFactory

trait Logging {
  val logger = LoggerFactory.getLogger("APP")
}

object Logging extends Logging
