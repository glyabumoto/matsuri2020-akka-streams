package matsuri2020.yabumoto.entity

import matsuri2020.yabumoto.utility.json.SnakyFieldSprayJsonSupport._

case class PurchaseLog(
    userId: String,
    items: Seq[Item],
    time: Long
)

object PurchaseLog {
  object jsonFormat {
    import Item.jsonFormat._
    implicit val purchaseLogJsonFormat = jsonFormat3(PurchaseLog.apply)
  }
}
