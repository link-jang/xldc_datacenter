package akka_sample.xldc.client

import akka.actor.Actor
import akka.actor.ActorLogging
import org.squeryl.SessionFactory
import org.squeryl.Session
import org.squeryl.adapters.MySQLAdapter
import org.squeryl.PrimitiveTypeMode._
import akka_sample.xldc.persist.xldc_db
import akka_sample.xldc.persist.RsyncDataMeta
import akka_sample.xldc.TransactionObj._
import akka_sample.xldc.FileUtil._
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern.ask
import akka.actor.Props


class CheckFileActor extends Actor with ActorLogging{
  
  override def preStart(): Unit = {
  		
  }
  
  
  def receive = {
    case RequstCheckFile(files) => {
      
      val len = files.length
      val response = new Array[FileStatus](len)
      for(i <- 0 to len -1  ){
        val md5 = getFileMD5(new java.io.File(files(i).oriFile))
        println(md5)
        if (md5 == null){
          response(i) = new FileStatus(files(i).id, false, "")
        }else{
          response(i) = new FileStatus(files(i).id, true, md5)
        }
      }
      
      sender ! ResponseCheckFile(response)
      
    }
    case _ => log.info("")
    
  }
  

  
  

}