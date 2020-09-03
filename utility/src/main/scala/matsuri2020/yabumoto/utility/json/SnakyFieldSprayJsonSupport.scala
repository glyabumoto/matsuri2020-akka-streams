package matsuri2020.yabumoto.utility.json

import com.google.common.base.CaseFormat
import spray.json.DefaultJsonProtocol

trait SnakyFieldSprayJsonSupport extends DefaultJsonProtocol {
  import reflect._

  override protected def extractFieldNames(tag: ClassTag[_]): Array[String] = {
    def snakify(name: String) = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name)
    super.extractFieldNames(tag).map(snakify)
  }
}

object SnakyFieldSprayJsonSupport extends SnakyFieldSprayJsonSupport
