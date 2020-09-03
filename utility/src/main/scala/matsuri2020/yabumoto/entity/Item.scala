package matsuri2020.yabumoto.entity

import matsuri2020.yabumoto.utility.json.SnakyFieldSprayJsonSupport._

case class Item(
    itemId: String,
    price: Int,
    quantity: Int
)

object Item {
  object jsonFormat {
    implicit val itemJsonFormat = jsonFormat3(Item.apply)
  }
}
