package akka_sample.xldc.network
import java.net._
import java.nio._
import java.nio.channels._
import java.util.LinkedList
import akka.event.Logging
import scala.collection.mutable.{ArrayBuffer, HashMap}

abstract class Connection (val channel: SocketChannel, val selector: Selector)   {
  
  val log = Logging.getLogger(null)
  
  channel.configureBlocking(false)
  channel.socket().setTcpNoDelay(true)
  channel.socket().setReuseAddress(true)
  channel.socket().setKeepAlive(true)
  
  @volatile private var closed = false
  
  var remoteAddress = channel.socket().getReceiveBufferSize().asInstanceOf[InetSocketAddress]
  
  def resetForceReregister(): Boolean
  
  def registerInterest()
  
  def unregisterInterest()
  
  def changeInterestForRead(): Boolean
  
  def changeInterestForWrite(): Boolean
  
  def key() = channel.keyFor(selector)
  
  var onKeyInterestChangeCallback: (Connection, Int) => Unit = null
  
  
  def read(): Boolean = {
    throw new UnsupportedOperationException(
      "Cannot read on connection of type " + this.getClass.toString)
  }
  
  def write(): Boolean = {
    throw new UnsupportedOperationException(
      "Cannot write on connection of type " + this.getClass.toString)
  }
  
  def close() {
    closed = true
    val k = key()
    
    if (k != null){
      k.cancel()
    }
    
    channel.close()
    
    
  }
  
  protected def isClosed: Boolean = closed
  
  def printBuffer(buffer: ByteBuffer, position: Int, length: Int) {
    
    val bytes = new Array[Byte](length)
    val curpostion = buffer.position()
    buffer.position(position)
    
    buffer.get(bytes)
    
    bytes.foreach( byte => print(byte + " "))
    println("(" + position + ")")
    buffer.position(curpostion)
  }
  
  def changeConnectionKeyInterest(ops: Int) {
    if (onKeyInterestChangeCallback != null) {
      onKeyInterestChangeCallback(this, ops)
    } else {
      throw new Exception("OnKeyInterestChangeCallback not registered")
    }
  }
  
}
  
class SendingConnection(val address : InetSocketAddress, selector_ : Selector)
  extends Connection(SocketChannel.open, selector_){
  
  private class Outbox {
    val messages = new LinkedList[Message]()
    val defaultChunkSize = 65536
    var nextMessageToBeUsed = 0
    
  
  
    def addMessage(message: Message) {
       messages.synchronized {
         messages.add(message)
         log.debug("Added [" + message + "] to outbox for sending to ")
       }
     }
    
    def getChunk: Option[MessageChunk] = {
      messages.synchronized{
        while(! messages.isEmpty()) {
          val message = messages.removeFirst()
          
          val chunk = message.getChunkForSending(defaultChunkSize)
          
          if( chunk.isDefined){
            messages.add(message)
            nextMessageToBeUsed = nextMessageToBeUsed + 1
            
            if(!message.start){
              message.start = true
              message.starttime = System.currentTimeMillis()
              return chunk
            }else{
              message.finishtime = System.currentTimeMillis()
            }
          }
        
        }
        
        None
      }
    }
    
  }
  
  
  
  
  private val outbox = new Outbox()
  
  private var needForceReregister = false
  
  val currentBuffers = new ArrayBuffer[ByteBuffer]()
  
  
  val DEFAULT_INTEREST = SelectionKey.OP_READ
  
  override def registerInterest() {
    changeConnectionKeyInterest(SelectionKey.OP_WRITE | DEFAULT_INTEREST )
  }
  
  override def unregisterInterest() {
    changeConnectionKeyInterest(DEFAULT_INTEREST)
  }
  
  def send(message: Message) {
    outbox.synchronized{
      outbox.addMessage(message)
      needForceReregister = true
    }
    
    if (channel.isConnected()){
      registerInterest()
    }
    
  }
  
  
  def resetForceReregister(): Boolean = {
    outbox.synchronized {
      val result = needForceReregister
      needForceReregister = false
      result
    }
  }
  
  def connect() {
    try {
      channel.register(selector, SelectionKey.OP_CONNECT)
      channel.connect(address)
      log.info("Initiating connection to [" + address + "]")
    }catch {
      case e: Exception => {
        log.error("Error connection to " + address, e)
      }
      
    }
  }
  
  
  def finishConnect(force: Boolean): Boolean = {
    
    try {
      
      val connected = channel.finishConnect()
      if (! force && ! connected) {
        log.info("fail to finish connect")
        return false
      }
      
      registerInterest()
    }catch {
      case e: Exception =>{
        log.warning("Error finishing connection to " + address, e)
      }
    }
    true
    
  }
  
  override  def write(): Boolean = {
    try {
      
      while (true){
        if( currentBuffers.size == 0 ){
          outbox.synchronized{
            outbox.getChunk match {
              case Some(chunk) => {
                val buffers = chunk.buffers
                
                if (needForceReregister && buffers.exists(_.remaining() > 0)) resetForceReregister()
                currentBuffers ++= buffers
              }
              
              case None => {
                // changeConnectionKeyInterest(0)
                /* key.interestOps(0) */
                return false
              }
               
            }
          }
        }
        
        if (currentBuffers.size > 0) {
          val buffer = currentBuffers(0)
          val remainingBytes = buffer.remaining
          val writtenBytes = channel.write(buffer)
          if (buffer.remaining == 0) {
            currentBuffers -= buffer
          }
          if (writtenBytes < remainingBytes) {
            // re-register for write.
            return true
          }
        }
      }
      
    }catch{
      case e: Exception => {
        close()
        return false
      }
    }
    
    true
  }
  
  override def read(): Boolean = {
    try{
      
      val length =  channel.read(ByteBuffer.allocate(1))
      if(length == -1){
        close()
      }else if( length > 0) {
        log.error("Unexpected data read from sendingConnection")
      }
      
    }catch{
      case e: Exception => {
        close()
      }
    }
    
    false
  }
  
  
  override def changeInterestForRead(): Boolean = false

  override def changeInterestForWrite(): Boolean = ! isClosed
  
   
  
}
  
  
class ReceivingConnection(
    channel_ : SocketChannel,
    selector: Selector)
    extends Connection(channel_, selector){
  
  
    class Inbox() {
      val messages = new HashMap[Int, BufferMessage]()
  
      def getChunk(header: MessageChunkHeader): Option[MessageChunk] = {
  
        def createNewMessage: BufferMessage = {
          val newMessage = Message.create(header).asInstanceOf[BufferMessage]
          newMessage.start = true
          newMessage.starttime = System.currentTimeMillis
     
        
          messages += ((newMessage.id, newMessage))
          newMessage
        }
  
        val message = messages.getOrElseUpdate(header.id, createNewMessage)
        
        message.getChunkForReceiving(header.chunkSize)
      }
  
      def getMessageForChunk(chunk: MessageChunk): Option[BufferMessage] = {
        messages.get(chunk.header.id)
      }
  
      def removeMessage(message: Message) {
        messages -= message.id
      }
    }
    
    val inbox = new Inbox()
    val headerBuffer: ByteBuffer = ByteBuffer.allocate(MessageChunkHeader.HEADER_SIZE)
    var onReceiveCallback: (Connection, Message) => Unit = null
    var currentChunk: MessageChunk = null
    
    
    channel.register(selector, SelectionKey.OP_READ)
    
    
    override def read(): Boolean = {
      try{
        while(true){
          if (currentChunk == null){
            val headBytesRead = channel.read(headerBuffer)
            if (headBytesRead == -1){
              close()
              return false
            }
            
            if(headerBuffer.remaining() > 0){
              return true
            }
            
            headerBuffer.flip()
            
            if (headerBuffer.remaining() != MessageChunkHeader.HEADER_SIZE){
              throw new Exception("unexpected number of bytes (" + headerBuffer.remaining() + ") in the header")
              
            }
            
            val header = MessageChunkHeader.create(headerBuffer)
            headerBuffer.clear()
            
            header.typ match {
              case Message.BUFFER_MESSAGE => {
                if (header.totalSize == 0) {
                  if (onReceiveCallback != null){
                    onReceiveCallback(this, Message.create(header))
                  }
                  
                  currentChunk = null
                  
                  return true
                }else{
                  currentChunk = inbox.getChunk(header).orNull
                }
                
              }
              
              case _ => throw new Exception("Message unkown received")
              
              
            }
            
          }
          
          if (currentChunk == null) throw new Exception("No message chunk to receive data")
          
          val bytesRead = channel.read(currentChunk.buffer)
          if (bytesRead == 0) {
            // re-register for read event ...
            return true
          } else if (bytesRead == -1) {
            close()
            return false
          }
          
          if (currentChunk.buffer.remaining == 0) {
     
            val bufferMessage = inbox.getMessageForChunk(currentChunk).get
            if (bufferMessage.isCompletelyReceived) {
              bufferMessage.flip()
              bufferMessage.finishtime = System.currentTimeMillis
 

              if (onReceiveCallback != null) {
                onReceiveCallback(this, bufferMessage)
              }
              inbox.removeMessage(bufferMessage)
            }
            currentChunk = null
          }
          
          
        }
        
        
        
      }catch{
        case e: Exception => {
          log.error("Error reading from connection to remote connection", e)
          close()
          return false
        }
        
        
      }
      
      true
      
      
    }
    
    def onReceive(callback: (Connection, Message) => Unit) {onReceiveCallback = callback}

    // override def changeInterestForRead(): Boolean = ! isClosed
    override def changeInterestForRead(): Boolean = true
  
    override def changeInterestForWrite(): Boolean = {
      throw new IllegalStateException("Unexpected invocation right now")
    }
  
    override def registerInterest() {
      // Registering read too - does not really help in most cases, but for some
      // it does - so let us keep it for now.
      changeConnectionKeyInterest(SelectionKey.OP_READ)
    }
  
    override def unregisterInterest() {
      changeConnectionKeyInterest(0)
    }
  
    // For read conn, always false.
    override def resetForceReregister(): Boolean = false
    
    
    
    
}
