package akka_sample.xldc.network

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer

case class BufferMessage(id_ : Int, val buffers: ArrayBuffer[ByteBuffer], var ackId: Int)
  extends Message(Message.BUFFER_MESSAGE, id_) {

  val initialSize = currentSize()
  var gotChunkForSendingOnce = false
  
  def currentSize() = {
    if (buffers == null || buffers.isEmpty) 0
    else {
      buffers.map(_.remaining()).reduceLeft(_ + _)
    }
      
  }
  
  def size = initialSize
  
  def getChunkForSending(maxChunkSize: Int): Option[MessageChunk] = {
    require( maxChunkSize >= 0 )
    
    if ( size == 0 && !  gotChunkForSendingOnce) {
      val newChunk = new MessageChunk(
        new MessageChunkHeader(typ, id, 0, 0, ackId, hasError, sendAddress), null)   
       gotChunkForSendingOnce = true   
       return Some(newChunk)  
      
    }
//    println(buffers(0).remaining() + "-----------------")
    while (! buffers.isEmpty) {
      val buffer = buffers(0)
      if( buffer.remaining() == 0){
        BlockManager.dispose(buffer)
        buffers -= buffer
      }else{
        val newBuffer = if ( buffer.remaining() <= maxChunkSize) {
          buffer.duplicate()
        }else{
          buffer.slice().limit(maxChunkSize).asInstanceOf[ByteBuffer]
        }
        
        buffer.position(buffer.position() + newBuffer.remaining())
        val newChunk = new MessageChunk(new MessageChunkHeader(
            typ, id, size, newBuffer.remaining, ackId, hasError, sendAddress), newBuffer)

        gotChunkForSendingOnce = true
        return Some(newChunk)
      }
      
    }
    
    None
    
  }

  def hasAckId() = (ackId != 0)
  
  def getChunkForReceiving(chunkSize: Int): Option[MessageChunk] = {
    // STRONG ASSUMPTION: BufferMessage created when receiving data has ONLY ONE data buffer
    if (buffers.size > 1) {
      throw new Exception("Attempting to get chunk from message with multiple data buffers")
    }
    val buffer = buffers(0)

    if (buffer.remaining > 0) {
      if (buffer.remaining < chunkSize) {
        throw new Exception("Not enough space in data buffer for receiving chunk")
      }
      val newBuffer = buffer.slice().limit(chunkSize).asInstanceOf[ByteBuffer]
      buffer.position(buffer.position + newBuffer.remaining)

      val newChunk = new MessageChunk(new MessageChunkHeader(
          typ, id, size, newBuffer.remaining, ackId, hasError, sendAddress), newBuffer)

      return Some(newChunk)
    }
    None
  }
  
  def isCompletelyReceived() = !buffers(0).hasRemaining
  
  def flip() {
    buffers.foreach(_.flip)
  }

}