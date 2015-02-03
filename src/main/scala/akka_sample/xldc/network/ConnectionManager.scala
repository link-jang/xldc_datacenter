package akka_sample.xldc.network

import java.io.{FileInputStream, IOException}
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.nio._
import java.nio.channels._
import java.nio.channels.spi._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{LinkedBlockingDeque, ThreadPoolExecutor, TimeUnit}


import akka.actor.ActorLogging
import org.apache.commons.logging.LogFactory



import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet, SynchronizedMap, SynchronizedQueue}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps

import com.google.common.base.Charsets.UTF_8
import io.netty.util.{Timeout, TimerTask, HashedWheelTimer}

import scala.util.Try
import scala.util.control.NonFatal

object test {
  def main(str: Array[String]): Unit ={
    val manager = new ConnectionManager( 1232)
    var receivedMessage = false
    manager.onReceiveMessage( (msg: Message) => {
      receivedMessage = true
      var array = new Array[Byte](msg.size)
      msg.asInstanceOf[BufferMessage].buffers(0).get(array)


      println( "_______________" + new String(array))
      None
    })

//    val fis = new FileInputStream(new java.io.File("/root/install.log"))
//    val fileChannel = fis.getChannel
    val size = 10 * 1024 * 1024
//    val buffer = ByteBuffer.allocate(size)
//    fileChannel.read(buffer)

    val buffer = ByteBuffer.allocate(size).put(Array.tabulate[Byte](size)(x => 1.toByte))
    buffer.flip

    val bufferMessage = Message.createBufferMessage(  ByteBuffer.wrap("目录".getBytes) )

    val result = Await.result(manager.sendMessageReliably("192.168.109.195", bufferMessage) , Duration.Inf).asInstanceOf[BufferMessage]
    println("-----------------:" + result.id)

  }
}

class ConnectionManager (port: Int, name: String = "Connection manager") {

  val log = LogFactory.getLog(this.getClass)
  
  class MessageStatus(
      val message: Message,
      completionHandler: Try[Message] => Unit) {

    def success(ackMessage: Message) {
      if (ackMessage == null) {
        failure(new NullPointerException)
      }
      else {
        completionHandler(scala.util.Success(ackMessage))
      }
    }

    def failWithoutAck() {
      completionHandler(scala.util.Failure(new IOException("Failed without being ACK'd")))
    }

    def failure(e: Throwable) {
      completionHandler(scala.util.Failure(e))
    }
  }
  
  
  private val selector = SelectorProvider.provider.openSelector()
  private val ackTimeoutMonitor =
    new HashedWheelTimer(Utils.namedThreadFactory("AckTimeoutMonitor"))

  private val ackTimeout = 5
  private val handlerThreadCount = 20
  private val ioThreadCount = 4
  private val connectThreadCount = 1
  
  private val handleMessageExecutor = new ThreadPoolExecutor(
    handlerThreadCount,
    handlerThreadCount,
    60, TimeUnit.SECONDS,
    new LinkedBlockingDeque[Runnable](),
    Utils.namedThreadFactory("handle-message-executor")) {

    override def afterExecute(r: Runnable, t: Throwable): Unit = {
      super.afterExecute(r, t)
      if (t != null && NonFatal(t)) {
        log.error("Error in handleMessageExecutor is not handled properly", t)
      }
    }
  }
  
  private val handleReadWriteExecutor = new ThreadPoolExecutor(
    ioThreadCount,
    ioThreadCount,
    60, TimeUnit.SECONDS,
    new LinkedBlockingDeque[Runnable](),
    Utils.namedThreadFactory("handle-read-write-executor")) {

    override def afterExecute(r: Runnable, t: Throwable): Unit = {
      super.afterExecute(r, t)
      if (t != null && NonFatal(t)) {
        log.error("Error in handleReadWriteExecutor is not handled properly", t)
      }
    }
  }
  
  
  private val handleConnectExecutor = new ThreadPoolExecutor(
    connectThreadCount,
    connectThreadCount,
    60, TimeUnit.SECONDS,
    new LinkedBlockingDeque[Runnable](),
    Utils.namedThreadFactory("handle-connect-executor")) {

    override def afterExecute(r: Runnable, t: Throwable): Unit = {
      super.afterExecute(r, t)
      if (t != null && NonFatal(t)) {
        log.error("Error in handleConnectExecutor is not handled properly", t)
      }
    }
  }
  
  
  def onReceiveMessage(callback: (Message) => Option[Message]) {
    onReceiveCallback = callback
  }
   
   
  private val serverChannel = ServerSocketChannel.open()
  
  private val connectionsByKey =
    new HashMap[SelectionKey, Connection] with SynchronizedMap[SelectionKey, Connection]
  
  private val messageStatuses = new HashMap[Int, MessageStatus]  // [MessageId, MessageStatus]
  private val keyInterestChangeRequests = new SynchronizedQueue[(SelectionKey, Int)]
  private val registerRequests = new SynchronizedQueue[SendingConnection]

  implicit val futureExecContext = ExecutionContext.fromExecutor(
    Utils.newDaemonCachedThreadPool("Connection manager future execution context"))
    
  
  @volatile
  private var onReceiveCallback: (BufferMessage) => Option[Message] = {

    (bm: BufferMessage) =>  {
    val a = bm.getChunkForReceiving(bm.currentSize())
    None
  }}
  
  serverChannel.configureBlocking(false)
  serverChannel.socket.setReuseAddress(true)
  serverChannel.socket.setReceiveBufferSize(256 * 1024)



  
  private def startService(port: Int): (ServerSocketChannel, Int) = {
    serverChannel.socket.bind(new InetSocketAddress(port))
    (serverChannel, serverChannel.socket.getLocalPort)
  }
  
  
  Utils.startServiceOnPort[ServerSocketChannel](port, startService, name)
  serverChannel.register(selector, SelectionKey.OP_ACCEPT)
  
  
  private val selectorThread = new Thread("connection-manager-thread") {
    override def run() = ConnectionManager.this.run()
  }
  
  
  selectorThread.setDaemon(true)
  selectorThread.start()
  
  
  private val writeRunnableStarted: HashSet[SelectionKey] = new HashSet[SelectionKey]()
  
  private def triggerWrite(key: SelectionKey) {
    val conn = connectionsByKey.getOrElse(key, null)
    if (conn == null) return

    writeRunnableStarted.synchronized {
      // So that we do not trigger more write events while processing this one.
      // The write method will re-register when done.
      if (conn.changeInterestForWrite()) conn.unregisterInterest()
      if (writeRunnableStarted.contains(key)) {
        // key.interestOps(key.interestOps() & ~ SelectionKey.OP_WRITE)
        return
      }

      writeRunnableStarted += key
    }
    handleReadWriteExecutor.execute(new Runnable {
      override def run() {
        try {
          var register: Boolean = false
          try {
            register = conn.write()
          } finally {
            writeRunnableStarted.synchronized {
              writeRunnableStarted -= key
              val needReregister = register || conn.resetForceReregister()
              if (needReregister && conn.changeInterestForWrite()) {
                conn.registerInterest()
              }
            }
          }
        } catch {
          case NonFatal(e) => {
            log.error("Error when writing to ", e)
          }
        }
      }
    } )
  }
  
  private val readRunnableStarted: HashSet[SelectionKey] = new HashSet[SelectionKey]()
  
  private def triggerRead(key: SelectionKey) {
    val conn = connectionsByKey.getOrElse(key, null)
    if (conn == null) return

    readRunnableStarted.synchronized {
      // So that we do not trigger more read events while processing this one.
      // The read method will re-register when done.
      if (conn.changeInterestForRead())conn.unregisterInterest()
      if (readRunnableStarted.contains(key)) {
        return
      }

      readRunnableStarted += key
    }
    handleReadWriteExecutor.execute(new Runnable {
      override def run() {
        try {
          var register: Boolean = false
          try {
            register = conn.read()
          } finally {
            readRunnableStarted.synchronized {
              readRunnableStarted -= key
              if (register && conn.changeInterestForRead()) {
                conn.registerInterest()
              }
            }
          }
        } catch {
          case NonFatal(e) => {
            log.error("Error when reading from ", e)
            throw e
          }
        }
      }
    } )
  }
  
  
  private def triggerConnect(key: SelectionKey) {
    val conn = connectionsByKey.getOrElse(key, null).asInstanceOf[SendingConnection]
    if (conn == null) return

    // prevent other events from being triggered
    // Since we are still trying to connect, we do not need to do the additional steps in
    // triggerWrite
    conn.changeConnectionKeyInterest(0)

    handleConnectExecutor.execute(new Runnable {
      override def run() {
        try {
          var tries: Int = 10
          while (tries >= 0) {
            if (conn.finishConnect(false)) return
            // Sleep ?
            Thread.sleep(1)
            tries -= 1
          }

          // fallback to previous behavior : we should not really come here since this method was
          // triggered since channel became connectable : but at times, the first finishConnect need
          // not succeed : hence the loop to retry a few 'times'.
          conn.finishConnect(true)
        } catch {
          case NonFatal(e) => {
            log.error("Error when finishConnect for " , e)
          }
        }
      }
    } )
  }
  
  private def triggerForceCloseByException(key: SelectionKey, e: Exception) {
    try {
      key.interestOps(0)
    } catch {
      // ignore exceptions
      case e: Exception => log.error("Ignoring exception", e)
    }

    val conn = connectionsByKey.getOrElse(key, null)
    if (conn == null) return

    // Pushing to connect threadpool
    handleConnectExecutor.execute(new Runnable {
      override def run() {
        try {
          

        } catch {
          // ignore exceptions
          case NonFatal(e) => log.error("Ignoring exception", e)
        }
        try {
          conn.close()
        } catch {
          // ignore exceptions
          case NonFatal(e) => log.error("Ignoring exception", e)
        }
      }
    })
  }
  
  private def wakeupSelector() {
    selector.wakeup()
  }
  
  def changeConnectionKeyInterest(connection: Connection, ops: Int) {
    keyInterestChangeRequests += ((connection.key, ops))
    // so that registrations happen !
    wakeupSelector()
  }
  def handleConnectionError(connection: Connection, e: Throwable) {
    log.error("Handling connection error on connection to " )
//    connection.getRemoteConnectionManagerId())
//    removeConnection(connection)
  }
  
  private def addListeners(connection: Connection) {
    connection.onKeyInterestChange(changeConnectionKeyInterest)
    connection.onException(handleConnectionError)
//    connection.onClose(removeConnection)
  }
  
  def addConnection(connection: Connection) {
    connectionsByKey += ((connection.key, connection))

//    try {
//      connection match {
//        case sendingConnection: SendingConnection =>
//          val sendingConnectionManagerId = sendingConnection.getRemoteConnectionManagerId()
//          ("Removing SendingConnection to " + sendingConnectionManagerId)

//          connectionsById -= sendingConnectionManagerId
//          connectionsAwaitingSasl -= connection.connectionId

//          messageStatuses.synchronized {
//            messageStatuses.values.filter(_.connectionManagerId == sendingConnectionManagerId)
//              .foreach(status => {
//              logInfo("Notifying " + status)
//              status.failWithoutAck()
//            })
//
//            messageStatuses.retain((i, status) => {
//              status.connectionManagerId != sendingConnectionManagerId
//            })
//          }
//        case receivingConnection: ReceivingConnection =>
//          val remoteConnectionManagerId = receivingConnection.getRemoteConnectionManagerId()
//          logInfo("Removing ReceivingConnection to " + remoteConnectionManagerId)
//
//          val sendingConnectionOpt = connectionsById.get(remoteConnectionManagerId)
//          if (!sendingConnectionOpt.isDefined) {
//            logError(s"Corresponding SendingConnection to ${remoteConnectionManagerId} not found")
//            return
//          }
//
//          val sendingConnection = sendingConnectionOpt.get
//          connectionsById -= remoteConnectionManagerId
//          sendingConnection.close()
//
//          val sendingConnectionManagerId = sendingConnection.getRemoteConnectionManagerId()
//
//          assert(sendingConnectionManagerId == remoteConnectionManagerId)
//
//          messageStatuses.synchronized {
//            for (s <- messageStatuses.values
//                 if s.connectionManagerId == sendingConnectionManagerId) {
//              logInfo("Notifying " + s)
//              s.failWithoutAck()
//            }
//
//            messageStatuses.retain((i, status) => {
//              status.connectionManagerId != sendingConnectionManagerId
//            })
//          }
//        case _ => logError("Unsupported type of connection.")
//      }
//    } finally {
      // So that the selection keys can be removed.
//      wakeupSelector()
//    }
  }
//  def run(): Unit = {
//    val selectCount = selector.select()
//    println("---------" + selectCount)
//
//  }
//
  
  
  def run() {
    try {

      while(!selectorThread.isInterrupted) {
        //send queue
        while (!registerRequests.isEmpty) {
          val conn: SendingConnection = registerRequests.dequeue()
          addListeners(conn)
          conn.connect()
          addConnection(conn)
        }
        //changed interest
        while(!keyInterestChangeRequests.isEmpty) {
          val (key, ops) = keyInterestChangeRequests.dequeue()
          try {
            if (key.isValid) {
              val connection = connectionsByKey.getOrElse(key, null)
              if (connection != null) {
                val lastOps = key.interestOps()
                key.interestOps(ops)

                // hot loop - prevent materialization of string if trace not enabled.

                def intToOpStr(op: Int): String = {
                  val opStrs = ArrayBuffer[String]()
                  if ((op & SelectionKey.OP_READ) != 0) opStrs += "READ"
                  if ((op & SelectionKey.OP_WRITE) != 0) opStrs += "WRITE"
                  if ((op & SelectionKey.OP_CONNECT) != 0) opStrs += "CONNECT"
                  if ((op & SelectionKey.OP_ACCEPT) != 0) opStrs += "ACCEPT"
                  if (opStrs.size > 0) opStrs.reduceLeft(_ + " | " + _) else " "
                }

//                println("Changed key for connection to   intToOpStr(ops) " + key.isConnectable())

              }
            } else {
              log.error("Key not valid ? " + key)
              throw new CancelledKeyException()
            }
          } catch {
            case e: CancelledKeyException => {
              log.error("key already cancelled ? " + key, e)
              triggerForceCloseByException(key, e)
            }
            case e: Exception => {
              log.error("Exception processing key " + key, e)
              triggerForceCloseByException(key, e)
            }
          }
        }

        val selectedKeysCount =
          try {
            selector.select()
          } catch {
            // Explicitly only dealing with CancelledKeyException here since other exceptions
            // should be dealt with differently.
            case e: CancelledKeyException => {
              // Some keys within the selectors list are invalid/closed. clear them.
              val allKeys = selector.keys().iterator()

              while (allKeys.hasNext) {
                val key = allKeys.next()
                try {
                  if (! key.isValid) {
                    log.error("Key not valid ? " + key)
                    throw new CancelledKeyException()
                  }
                } catch {
                  case e: CancelledKeyException => {
                    log.error("key already cancelled ? " + key, e)
                    triggerForceCloseByException(key, e)
                  }
                  case e: Exception => {
                    log.error("Exception processing key " + key, e)
                    triggerForceCloseByException(key, e)
                  }
                }
              }
            }
            0
          }

        if (selectedKeysCount == 0) {
          log.info("Selector selected " + selectedKeysCount + " of " + selector.keys.size +
            " keys:/"  )
        }

        if (selectorThread.isInterrupted) {
          log.error("Selector thread was interrupted!")
          return
        }

        if (0 != selectedKeysCount) {
          val selectedKeys = selector.selectedKeys().iterator()

          while (selectedKeys.hasNext) {
            val key = selectedKeys.next
            selectedKeys.remove()
            try {
              if (key.isValid) {
                if (key.isAcceptable) {
                  log.debug("acceptConnecetion")
                  acceptConnection(key)
                } else
                if (key.isConnectable) {

                  log.debug("triggerConnect")
                  triggerConnect(key)
                } else
                if (key.isReadable) {
                  log.debug("triggerRead")
                  triggerRead(key)
                } else
                if (key.isWritable) {
                  log.debug("triggerWrite")
                  triggerWrite(key)
                }
              } else {
                log.debug("key is not valid")
                throw new CancelledKeyException()
              }
            } catch {
              // weird, but we saw this happening - even though key.isValid was true,
              // key.isAcceptable would throw CancelledKeyException.
              case e: CancelledKeyException => {
                log.error("key already cancelled ? " + key, e)
                triggerForceCloseByException(key, e)
              }
              case e: Exception => {
                log.error("Exception processing key " + key, e)
                triggerForceCloseByException(key, e)
              }
            }
          }
        }
      }
    } catch {
      case e: Exception => log.error("Error in select loop", e)
    }
  }
  
  def acceptConnection(key: SelectionKey) {
    val serverChannel = key.channel.asInstanceOf[ServerSocketChannel]

    var newChannel = serverChannel.accept()

    // accept them all in a tight loop. non blocking accept with no processing, should be fine
    while (newChannel != null) {
      try {
//        val newConnectionId = new ConnectionId(id, idCount.getAndIncrement.intValue)
        val newConnection = new ReceivingConnection(newChannel, selector)
        newConnection.onReceive(receiveMessage)
        addListeners(newConnection)
        addConnection(newConnection)
        log.info("Accepted connection from [" + newConnection.remoteAddress + "]")
      } catch {
        // might happen in case of issues with registering with selector
        case e: Exception => log.error("Error in accept loop" + e.toString())
        								throw e
      }

      newChannel = serverChannel.accept()
    }
  }

  private def handleMessage(
                             message: Message,
                             connection: Connection) {
    message match {
      case bufferMessage: BufferMessage => {

        if (bufferMessage.hasAckId()) {
          messageStatuses.synchronized {
            messageStatuses.get(bufferMessage.ackId) match {
              case Some(status) => {
                messageStatuses -= bufferMessage.ackId
                status.success(message)
              }
              case None => {
                /**
                 * We can fall down on this code because of following 2 cases
                 *
                 * (1) Invalid ack sent due to buggy code.
                 *
                 * (2) Late-arriving ack for a SendMessageStatus
                 *     To avoid unwilling late-arriving ack
                 *     caused by long pause like GC, you can set
                 *     larger value than default to spark.core.connection.ack.wait.timeout
                 */
                log.error(s"Could not find reference for received ack Message ${message.id}")
              }
            }
          }
        } else {
          var ackMessage : Option[Message] = None
          try {
            ackMessage = if (onReceiveCallback != null) {
              onReceiveCallback(bufferMessage)
            } else {
              None
            }

            if (ackMessage.isDefined) {
              if (!ackMessage.get.isInstanceOf[BufferMessage]) {
                log.info("Response to " + bufferMessage + " is not a buffer message, it is of type "
                  + ackMessage.get.getClass)
              } else if (!ackMessage.get.asInstanceOf[BufferMessage].hasAckId) {
                log.info("Response to " + bufferMessage + " does not have ack id set")
                ackMessage.get.asInstanceOf[BufferMessage].ackId = bufferMessage.id
              }
            }
          } catch {
            case e: Exception => {
              log.error(s"Exception was thrown while processing message", e)
              ackMessage = Some(Message.createErrorMessage(e, bufferMessage.id))
            }
          } finally {
            sendMessage(connection.remoteAddress.getHostName, ackMessage.getOrElse {
              Message.createBufferMessage(bufferMessage.id)
            })
          }
        }
      }
      case _ => throw new Exception("Unknown type message received")
    }
  }
  
   def receiveMessage(connection: Connection, message: Message) {
//    val connectionManagerId = ConnectionManagerId.fromSocketAddress(message.senderAddress)
     log.debug("Received [" + message + "] ")
    val runnable = new Runnable() {
      val creationTime = System.currentTimeMillis
      def run() {
        try {
          log.info("Handler thread delay is " + (System.currentTimeMillis - creationTime) + " ms")
          handleMessage(message, connection)
          log.info("Handling delay is " + (System.currentTimeMillis - creationTime) + " ms")
        } catch {
          case NonFatal(e) => {
            log.error("Error when handling messages from ", e)
//            connection.callOnExceptionCallbacks(e)
          }
        }
      }
    }
    handleMessageExecutor.execute(runnable)
    /* handleMessage(connection, message) */
  }
  private def sendMessage(host: String, message: Message): Unit = {
    sendMessage(host, port, message)
  }

  private val connectionsById = new HashMap[Int, SendingConnection]

  private def sendMessage(host: String, port:Int, message: Message) {
    def startNewConnection(): SendingConnection = {
      val inetSocketAddress = new InetSocketAddress(host,
        port)
//      val newConnectionId = new ConnectionId(id, idCount.getAndIncrement.intValue)
      val newConnection = new SendingConnection(inetSocketAddress, selector)
      newConnection.onException {
        case (conn, e) => {
          log.error("Exception while sending message.", e)
//          reportSendingMessageFailure(message.id, e)
        }
      }
      registerRequests.enqueue(newConnection)
      newConnection
    }
//    val connection = connectionsById.getOrElseUpdate(connectionManagerId, startNewConnection())
    val connection = connectionsById.getOrElseUpdate(message.id, startNewConnection)
    message.sendAddress = new InetSocketAddress(host, port)
    log.debug("Before Sending [" + message + "] + " )

    connection.send(message)
    wakeupSelector()
  }
  
    
  def sendMessageReliably(host: String, message: Message)
      : Future[Message] = {
    val promise = Promise[Message]()

    // It's important that the TimerTask doesn't capture a reference to `message`, which can cause
    // memory leaks since cancelled TimerTasks won't necessarily be garbage collected until the time
    // at which they would originally be scheduled to run.  Therefore, extract the message id
    // from outside of the TimerTask closure (see SPARK-4393 for more context).
    val messageId = message.id
    // Keep a weak reference to the promise so that the completed promise may be garbage-collected
    val promiseReference = new WeakReference(promise)
    val timeoutTask: TimerTask = new TimerTask {
      override def run(timeout: Timeout): Unit = {
        messageStatuses.synchronized {
          messageStatuses.remove(messageId).foreach { s =>
            val e = new IOException("sendMessageReliably failed because ack " +
              s"was not received within $ackTimeout sec")
            val p = promiseReference.get
            if (p != null) {
              // Attempt to fail the promise with a Timeout exception
              if (!p.tryFailure(e)) {
                // If we reach here, then someone else has already signalled success or failure
                // on this promise, so log a warning:
                log.error("Ignore error because promise is completed", e)
              }
            } else {
              // The WeakReference was empty, which should never happen because
              // sendMessageReliably's caller should have a strong reference to promise.future;
              log.error("Promise was garbage collected; this should never happen!", e)
            }


          }
        }
      }
    }

    val timeoutTaskHandle = ackTimeoutMonitor.newTimeout(timeoutTask, ackTimeout, TimeUnit.SECONDS)

    val status = new MessageStatus(message, s => {
      timeoutTaskHandle.cancel()
      s match {
        case scala.util.Failure(e) =>
          // Indicates a failure where we either never sent or never got ACK'd
          if (!promise.tryFailure(e)) {
            log.error("Ignore error because promise is completed", e)
          }
        case scala.util.Success(ackMessage) =>
          if (ackMessage.hasError) {
            val errorMsgByteBuf = ackMessage.asInstanceOf[BufferMessage].buffers.head
            val errorMsgBytes = new Array[Byte](errorMsgByteBuf.limit())
            errorMsgByteBuf.get(errorMsgBytes)
            val errorMsg = new String(errorMsgBytes, UTF_8)
            val e = new IOException(
              s"sendMessageReliably failed with ACK that signalled a remote error: $errorMsg")
            if (!promise.tryFailure(e)) {
              log.error("Ignore error because promise is completed", e)
            }
          } else {

            if (!promise.trySuccess(ackMessage)) {

              log.error("Drop ackMessage because promise is completed")
            }
          }
      }
    })
    messageStatuses.synchronized {
      messageStatuses += ((message.id, status))
    }

    sendMessage(host, port, message)
    promise.future
  }
  
  

}