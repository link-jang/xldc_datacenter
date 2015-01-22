package akka_sample.xldc.network
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer
import java.net.{InetAddress, InetSocketAddress}

class MessageChunkHeader(
  val typ: Long,
  val id: Int,
  val totalSize: Int,
  val chunkSize: Int,
  val other: Int,
  val hasError: Boolean,
  val address: InetSocketAddress
  ){
  
  lazy val buffer = {
    
    val ip = address.getAddress.getAddress
    val port = address.getPort
    
    ByteBuffer.allocate(MessageChunkHeader.HEADER_SIZE).
      putLong(typ).
      putInt(id).
      putInt(totalSize).
      putInt(chunkSize).
      putInt(other).
      put(if (hasError) 1.asInstanceOf[Byte] else 0.asInstanceOf[Byte]).
      putInt(ip.size).
      put(ip).
      putInt(port).
      position(MessageChunkHeader.HEADER_SIZE).
      flip.asInstanceOf[ByteBuffer]
    
  }
  
}

private object MessageChunkHeader {
  val HEADER_SIZE = 45

  def create(buffer: ByteBuffer): MessageChunkHeader = {
    if (buffer.remaining != HEADER_SIZE) {
      throw new IllegalArgumentException("Cannot convert buffer data to Message")
    }
    val typ = buffer.getLong()
    println("typ" + typ)
    val id = buffer.getInt()
    val totalSize = buffer.getInt()
    val chunkSize = buffer.getInt()
    val other = buffer.getInt()
    val hasError = buffer.get() != 0
    val securityNeg = buffer.getInt()
    var ipSize = buffer.getInt()
    if (ipSize < 0) ipSize = 0 
    val ipBytes = new Array[Byte](ipSize)
    buffer.get(ipBytes)
    val ip = InetAddress.getByAddress(ipBytes)
    val port = buffer.getInt()
    new MessageChunkHeader(typ, id, totalSize, chunkSize, other, hasError,
      new InetSocketAddress(ip, port))
  }
}

class MessageChunk(val header: MessageChunkHeader, val buffer: ByteBuffer) {
  val size = if (buffer == null) 0 else buffer.remaining()
  
  lazy val buffers = {
    val ab = new ArrayBuffer[ByteBuffer]()
    ab += header.buffer
    if (buffer != null) {
      ab += buffer
    }
    ab
  }
  
  override def toString() = {
    "" + this.getClass.getSimpleName + "(id =" + header.id + ", size = " + size + ")"
  }
}

 abstract class Message(val typ: Long, val id: Int) {
  
  var sendAddress :InetSocketAddress = null
  var start = false
  var starttime = -1L
  var finishtime = -1L
  var hasError = false
  
  def size: Int
  
  def getChunkForSending(maxChunkSize: Int): Option[MessageChunk]
  
  def getChunkForReceiving(chunkSize: Int):Option[MessageChunk]
  
  def timeTaken(): String = (finishtime - starttime).toString()
  
  override def toString = this.getClass.getSimpleName + "(id = " + id + ", size = " + size + ")"

  

}


object Message {
  
  val BUFFER_MESSAGE = 1111111111L
  
  var lastId = 1
  
  def geNewId = synchronized {
    lastId += 1
    if (lastId == 0) {
      lastId = 1
    }
    
    lastId
  }
  
  def create(header: MessageChunkHeader): Message = {
    
    val newMessage: Message = header.typ match {
      case BUFFER_MESSAGE => new BufferMessage(header.id,
        ArrayBuffer(ByteBuffer.allocate(header.totalSize)), header.other)
      
    }
    
    newMessage.hasError = header.hasError
    newMessage.sendAddress = header.address
    newMessage
    
    
  }
  
  def createBufferMessage(dataBuffers: Seq[ByteBuffer], ackId: Int): BufferMessage = {
    if (dataBuffers == null) {
      return new BufferMessage(0, new ArrayBuffer[ByteBuffer], ackId)
    }
    if (dataBuffers.exists(_ == null)) {
      throw new Exception("Attempting to create buffer message with null buffer")
    }
    new BufferMessage(0, new ArrayBuffer[ByteBuffer] ++= dataBuffers, ackId)
  }

  def createBufferMessage(dataBuffers: Seq[ByteBuffer]): BufferMessage =
    createBufferMessage(dataBuffers, 0)

  def createBufferMessage(dataBuffer: ByteBuffer, ackId: Int): BufferMessage = {
    if (dataBuffer == null) {
      createBufferMessage(Array(ByteBuffer.allocate(0)), ackId)
    } else {
      createBufferMessage(Array(dataBuffer), ackId)
    }
  }

  def createBufferMessage(dataBuffer: ByteBuffer): BufferMessage =
    createBufferMessage(dataBuffer, 0)

  def createBufferMessage(ackId: Int): BufferMessage = {
    createBufferMessage(new Array[ByteBuffer](0), ackId)
  }
  
  
}


