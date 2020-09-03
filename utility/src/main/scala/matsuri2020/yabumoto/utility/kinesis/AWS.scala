package matsuri2020.yabumoto.utility.kinesis

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient

object AWS {
  lazy val kinesis = KinesisAsyncClient
    .builder()
    .region(Region.AP_NORTHEAST_1)
    .build()
  lazy val dynamodb = DynamoDbAsyncClient
    .builder()
    .region(Region.AP_NORTHEAST_1)
    .build()
  lazy val cloudwatch = CloudWatchAsyncClient
    .builder()
    .region(Region.AP_NORTHEAST_1)
    .build()
}
