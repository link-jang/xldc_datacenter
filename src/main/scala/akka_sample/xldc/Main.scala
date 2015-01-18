package akka_sample.xldc
import akka.actor.ActorSystem
import akka.actor.Props
import akka_sample.xldc.TransactionObj._
import akka_sample.xldc.persist._
import akka.actor.actorRef2Scala
import akka_sample.xldc.master._
import akka_sample.xldc.client.ClientActor
import com.typesafe.config.ConfigException
import akka_sample.xldc.master.DbActor
import akka.io.IO
import spray.can.Http

object Main {
  
    
  def main(args: Array[String]) {
    

    implicit val system = ActorSystem("DataCenterSystem")
    var handler = system.actorOf(Props[HttpServerActor], name = "webActor")
    IO(Http) ! Http.Bind(handler, interface = "localhost", port = 8080)
    
    
    try{
      val servertype = system.settings.config.getValue("akka.serverclient.type").unwrapped().toString()
      val serverPath = system.settings.config.getValue("akka.serverclient.server").unwrapped().toString()
      val clientPath = system.settings.config.getValue("akka.serverclient.client").unwrapped().toString().replaceAll("(\\[|\\]|\\s+)", "").split(",")
      
      
      val jdbcuser = system.settings.config.getValue("akka.serverclient.dbconfig.user").unwrapped().toString()
      val jdbcpassword = system.settings.config.getValue("akka.serverclient.dbconfig.password").unwrapped().toString()
      val jdbcurl = system.settings.config.getValue("akka.serverclient.dbconfig.jdbcurl").unwrapped().toString()
      
      
      if (servertype == "server"){
//        system.actorOf(Props(classOf[DbActor],Array(jdbcurl, jdbcuser, jdbcpassword)), name = "dbActor")
//        system.actorOf(Props(classOf[ServerActor],clientPath.toList), name = "serverActor")
      }
      else if(servertype == "client"){
        system.actorOf(Props(classOf[ClientActor], serverPath, clientPath.toList), name = "clientActor")
      }else{
        
      }
    }catch {
      case ex: ConfigException =>
        println("akka.serverclient.type set: client/server")
      case a: java.lang.Exception =>
        println("config error" + a.toString())
    }
    
  	

  }

}