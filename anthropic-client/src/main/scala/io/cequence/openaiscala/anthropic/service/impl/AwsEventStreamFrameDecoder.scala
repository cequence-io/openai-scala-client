package io.cequence.openaiscala.anthropic.service.impl

import akka.stream._
import akka.stream.stage._
import akka.util.ByteString

class AwsEventStreamFrameDecoder extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("AwsEventStreamFrameDecoder.in")
  val out = Outlet[ByteString]("AwsEventStreamFrameDecoder.out")
  override val shape = FlowShape(in, out)

  private implicit val order = java.nio.ByteOrder.BIG_ENDIAN

  override def createLogic(attrs: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var buffer = ByteString.empty

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        buffer ++= grab(in)
        emitFrames()
      }
      override def onUpstreamFinish(): Unit = {
        emitFrames()
        if (buffer.isEmpty) completeStage()
        else failStage(new RuntimeException("Truncated frame at stream end"))
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (!hasBeenPulled(in)) pull(in)
      }
    })

    def emitFrames(): Unit = {
      while (buffer.size >= 4) {
        val totalLength = buffer.iterator.getInt
        println("buffer size: " + buffer.size)
        println("total length: " + totalLength)
        println("buffer:       " + buffer.utf8String)

        if (buffer.size < 4 + totalLength) {
          // not enough data yet
          return
        }
        val frame = buffer.slice(4, 4 + totalLength)
        buffer = buffer.drop(4 + totalLength)
        emit(out, frame)
      }

      if (!hasBeenPulled(in) && !isClosed(in)) {
        pull(in)
      }
    }
  }
}