package akka_sample.xldc.client

import java.io.{RandomAccessFile, File, FileOutputStream, FileInputStream}
import java.nio.ByteBuffer

import akka.actor
import akka.actor.Actor
import akka.actor.ActorSelection
import akka.actor.ActorLogging
import akka_sample.xldc.Schedule
import akka_sample.xldc.TransactionObj._
import akka.actor.Props
import akka.actor.ActorRef
import akka_sample.xldc.network.{Message, BufferMessage}
import akka_sample.xldc.persist.TaskMeta

import scala.collection.mutable.{HashMap, ArrayBuffer}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ClientActor (scheduleHandle: Schedule) extends Actor with ActorLogging {

  
  val checkFileActor: ActorRef = context.actorOf(Props[CheckFileActor], name ="checkfileactor")

  private val Data_Chunk = 108 * 1024* 1024


  private val messageStatuses = new HashMap[Long,TaskMeta ]

  def receive = {
    
    case NotifyClient(task) => {

      log.info("recieve RpcNotify" + task.id + task.desFile)
      messageStatuses.synchronized{
        messageStatuses += (task.id -> task)
      }
      SendDataFileTask(task)
      sender ! RpcResponseNotify(task, true)

    }

    case RequstCheckFile(taskArray) => {
      log.info("recieve CheckFile" + taskArray(0).oriFile)
      checkFileActor ! RequstCheckFile(taskArray)
    }
    
    case ResponseCheckFile(response) => {
      scheduleHandle.dagSchedulerActor ! ResponseCheckFile(response)
    }


    case message: Array[Byte] => {
      val len = message.length
      println(len)
    }
    
    case _ => println("----")
    
    
    
  }

  def onDataRecieve(msg: Message): Option[Message] = {
    var array = new Array[Byte](msg.size)
    val id = msg.id
    var task: Option[TaskMeta] = None

    messageStatuses.synchronized{
      task = messageStatuses.get(id)
    }

    if(!task.isDefined){
      log.error("task not find :" + id)
      return None
    }


//    val fous = new FileOutputStream(new File(task.get.desFile))
    println(task.get.desFile)
    val file = new File(task.get.desFile)
    if (file.exists()) {
      file.delete()
    }
    val raf = new RandomAccessFile(file, "rw")
    val fileChannel = raf.getChannel
    var buff = ByteBuffer.allocate(Data_Chunk)


    val buffmsg = msg.asInstanceOf[BufferMessage].buffers(0)
    var len = buffmsg.remaining()

    fileChannel.write(buffmsg)
    fileChannel.close()

    scheduleHandle.dagSchedulerActor ! RpcResponseComplete(task.get, true)
    None
  }


  def SendDataFileTask(task: TaskMeta): Unit = {

    val fis = new FileInputStream(new java.io.File(task.oriFile))
    val fileChannel = fis.getChannel
    val size = 10 * 1024 * 1024
    val buffer = ByteBuffer.allocate(size)
    fileChannel.read(buffer)
    buffer.flip()
    val bufferMessage = Message.createBufferMessage(task.id, buffer)
    scheduleHandle.manager.onReceiveMessage(onDataRecieve)

    val result = Await.result(scheduleHandle.manager.sendMessageReliably(task.oriMechin.substring(0, task.oriMechin.indexOf(":")), bufferMessage) ,
      Duration.Inf).asInstanceOf[BufferMessage]



  }

}