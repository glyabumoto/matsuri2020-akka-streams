package matsuri2020.yabumoto.entity

case class UserLog(
    userId: String,
    time: Long,
    logType: String,
    itemId: Option[String],
    price: Option[Int],
    quantity: Option[Int],
    createdTime: Long
)

object UserLog {
  import matsuri2020.yabumoto.utility.json.SnakyFieldSprayJsonSupport._

  object jsonFormat {
    implicit val userLogFormat = jsonFormat7(UserLog.apply)
  }
}

object LogType {
  val PURCHASE = "purchase"
}
