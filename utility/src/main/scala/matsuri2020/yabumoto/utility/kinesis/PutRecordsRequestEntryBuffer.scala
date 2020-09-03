package matsuri2020.yabumoto.utility.kinesis

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage._

import scala.collection.mutable.ArrayBuffer
import scala.io.Codec

class PutRecordsRequestEntryBuffer(maxRecords: Int, maxBytes: Int)
    extends GraphStage[FlowShape[KinesisStreamObject, Seq[KinesisStreamObject]]] {
  val in  = Inlet[KinesisStreamObject]("PutRecordsRequestEntryBuffer.in")
  val out = Outlet[Seq[KinesisStreamObject]]("PutRecordsRequestEntryBuffer.out")

  override def shape: FlowShape[KinesisStreamObject, Seq[KinesisStreamObject]] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var recordsBuffer     = ArrayBuffer[KinesisStreamObject]()
    private var byteLength: Long  = 0L
    private var downstreamWaiting = false

    def isBufferEmpty = recordsBuffer.isEmpty
    def isBufferFull  = recordsBuffer.length >= maxRecords || byteLength >= maxBytes
    def pushAndClear() = {
      push(out, recordsBuffer.toSeq)
      recordsBuffer = ArrayBuffer[KinesisStreamObject]()
      byteLength = 0L
    }

    override def preStart(): Unit = pull(in)

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val record = grab(in)
          recordsBuffer += record
          byteLength += record.body.length + record.partitionKey.getBytes(Codec.UTF8.charSet).length
          if (downstreamWaiting) {
            downstreamWaiting = false
            pushAndClear()
          }
          if (!isBufferFull) {
            pull(in)
          }
        }

        override def onUpstreamFinish(): Unit = {
          if (!isBufferEmpty) {
            emit(out, recordsBuffer.toSeq)
          }
          completeStage()
        }
      }
    )

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = {
          if (isBufferEmpty) {
            downstreamWaiting = true
          } else {
            pushAndClear()
          }
          if (!isBufferFull && !hasBeenPulled(in)) {
            pull(in)
          }
        }
      }
    )
  }
}

object PutRecordsRequestEntryBuffer {
  def apply(maxRecords: Int, maxBytes: Int): Flow[KinesisStreamObject, Seq[KinesisStreamObject], NotUsed] = {
    Flow.fromGraph(new PutRecordsRequestEntryBuffer(maxRecords, maxBytes))
  }
}
