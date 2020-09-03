package matsuri2020.yabumoto.utility.kinesis

import akka.util.ByteString
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry

import scala.io.Codec

case class KinesisStreamObject(partitionKey: String, body: ByteString, tryCount: Int = 1) {
  lazy val byteLength = body.length + partitionKey.getBytes(Codec.UTF8.charSet).length

  def toRetryRecord = copy(tryCount = tryCount + 1)

  def toEntry: PutRecordsRequestEntry = {
    PutRecordsRequestEntry
      .builder()
      .partitionKey(partitionKey)
      .data(SdkBytes.fromByteBuffer(body.toByteBuffer))
      .build()
  }
}
