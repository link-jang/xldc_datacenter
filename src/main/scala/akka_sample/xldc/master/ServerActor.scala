package akka_sample.xldc.master

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorLogging
import akka_sample.xldc.persist.xldc_db
import akka_sample.xldc.persist.RsyncDataMeta
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.Props
import akka_sample.xldc.TransactionObj._

class ServerActor(clients: List[String]) extends Actor with ActorLogging{
  
  
  override def preStart(): Unit = {
    
    println("start server")
  	dbActor !  RequstGenerFile(new RsyncDataMeta(0, "zyzx_data", "twin07320:2553", "/data1/download/result/speed_${day}_${hour}", "twin07364:2553", "/tmp/speed_${day}_${hour}", "hour"))
  	dbActor !  RequstGenerFile(new RsyncDataMeta(0, "xldc_data", "192.168.109.195:2553", "/data1/download/result/speed_${day}_${hour}", "192.168.109.195:2553", "/tmp/speed_${day}_${hour}", "hour"))
    dbActor !  RequstGenerTask("hour", "20150112", "22")
    dbActor !  RequstExistTask("hour")
    
  }
  import context.dispatcher
  
  val dbActor = context.actorSelection("/user/dbActor")
//  val checkFileActor: ActorRef = context.actorOf(Props[CheckFileActor], name ="checkfileactor")
  val rpcActor: ActorRef = context.actorOf(Props[SendPocessActor], name = "rpcActor")
  
  import scala.collection.mutable.Map
  val  clientActor = Map[String, ActorSelection]()
  clients.foreach( 
    client => 
  	clientActor += (client.substring(client.indexOf("@") + 1, client.indexOf("/user")) -> context.actorSelection(client))
  )
  
//  clientActor.foreach(a => a._2 ! RpcNotify(null) )
  
  
  
  implicit val timeout = Timeout(5 seconds)
  
  

   
  
  def generateTask(fileType: String, day: String, hour: String): Unit ={
    
    dbActor ! RequstGenerTask(fileType, day, hour)
    
  }
  
  def checkFile(fileType: String): Unit = {
    
    
    val future = dbActor ? RequstExistTask(fileType)
    val result = Await.result(future, timeout.duration).asInstanceOf[ ResponseExistTask]
   
    result.taskType.foreach(task => 
      if (clientActor.contains(task.oriMechin))
      	clientActor.get(task.oriMechin).get ! RequstCheckFile(Array(task))
      else
        log.error("cat not find mechine :" + task.oriMechin)
      )


  }
  
  
  def notifyClient(fileType: String): Unit = {
    
    val future = dbActor ? RequstNotifyTask(fileType)
    val result = Await.result(future, timeout.duration).asInstanceOf[ResponseNotifyTask]
    
    val tasks = result.taskType
    
    for (task <- tasks){
      rpcActor ! RpcNotify(task)
    }
    
  }
  
  
  def receive = {
    
    
    case "start" =>{
      log.info("start server")
    }
    
    case RpcResponseNotify(task, status) => {
    	if (status)
    		dbActor ! UpdateNotifyTask(task.id, status)
    }
    
    case RpcResponseComplete(task, status) => {
      if (status)
      	dbActor ! UpdateCompleteTask(task.id, status)
    }
    
    
  }

}