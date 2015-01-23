package akka_sample.xldc.client

import java.nio.ByteBuffer

import akka.actor.Actor
import akka.actor.ActorSelection
import akka.actor.ActorLogging
import akka_sample.xldc.TransactionObj._
import akka.actor.Props
import akka.actor.ActorRef
import akka_sample.xldc.network.BufferMessage
import akka_sample.xldc.persist.TaskMeta

import scala.collection.mutable.ArrayBuffer

class ClientActor (serverPath: String, clients: List[String]) extends Actor with ActorLogging {
  
  val serverActor = context.actorSelection(serverPath)
  import scala.collection.mutable.Map
  val  clientActor = Map[String, ActorSelection]()
  clients.foreach( 
    client => 
  	clientActor += (client.substring(client.indexOf("@") + 1, client.indexOf("/user")) -> context.actorSelection(client))
  )
  
  clientActor.foreach(a => println( a._1 + " " + a._2))
  
  
  val checkFileActor: ActorRef = context.actorOf(Props[CheckFileActor], name ="checkfileactor")
  
  def receive = {
    
    case RpcNotify(task) => {

      task match {
        case null =>
          log.info("recieve null RpcNotify")
          sender ! RpcResponseNotify(null, true)
        case t : TaskMeta =>
          log.info("recieve RpcNotify" + task.id + task.desFile)
          sender ! RpcResponseNotify(task, true)
      }

    }
    case RequstCheckFile(taskArray) => {
      log.info("recieve CheckFile" + taskArray(0).oriFile)
      checkFileActor ! RequstCheckFile(taskArray)
    }
    
    case ResponseCheckFile(response) =>
      serverActor ! ResponseCheckFile(response)


    case message: Array[Byte] => {
      val len = message.length
      println(len)
    }
    
    case _ => println("----")
    
    
    
  }

}