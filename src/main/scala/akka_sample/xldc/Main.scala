package akka_sample.xldc
import akka.actor.ActorSystem
import akka.actor.ActorRef
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
import akka_sample.xldc.master.httpserver.HttpServerActor
import scala.io.Source

object Main {
  
    
  def main(args: Array[String]) {

    implicit val system = ActorSystem("DataCenterSystem")
    
    
    
    try{
      val servertype = Configrature.servertype(system)
      val serverPath = Configrature.serverPath(system)
      val clientPath = Configrature.clientPath(system)
      
      
      val jdbcuser = Configrature.jdbcuser(system)
      val jdbcpassword = Configrature.jdbcpassword(system)
      val jdbcurl = Configrature.jdbcurl(system)
      
      val webhost= Configrature.webhost(system)
      val webport = Configrature.webport(system)
      
      if (servertype == "server"){
        val dbactor = system.actorOf(Props(classOf[DbActor],Array(jdbcurl, jdbcuser, jdbcpassword)), name = "dbActor")
        system.actorOf(Props(classOf[ServerActor],clientPath.toList), name = "serverActor")
        
        var handler = system.actorOf(Props(classOf[HttpServerActor], dbactor), name = "webActor")
        IO(Http) ! Http.Bind(handler, interface = webhost, port = webport)
//        system.actorOf(Props(classOf[ClientActor], serverPath, clientPath.toList), name = "clientActor")
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