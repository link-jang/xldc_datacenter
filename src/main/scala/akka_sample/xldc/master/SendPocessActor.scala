package akka_sample.xldc.master

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

class SendPocessActor extends Actor with ActorLogging{
  
  
  def receive = {
    
    case NotifyClient =>
      
    
  }

}