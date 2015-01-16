package akka_sample.xldc.client

import akka.actor.Actor
import akka.actor.ActorSelection
import akka.actor.ActorLogging
import akka_sample.xldc.TransactionObj._
import akka.actor.Props
import akka.actor.ActorRef

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
      log.info("recieve RpcNotify" + task.id + task.desFile)
      sender ! RpcResponseNotify(task, true)
    }
    case RequstCheckFile(taskArray) => {
      log.info("recieve CheckFile" + taskArray(0).oriFile)
      checkFileActor ! RequstCheckFile(taskArray)
    }
    
    case _ => println("")
    
    
    
  }

}