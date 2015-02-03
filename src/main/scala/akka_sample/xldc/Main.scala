package akka_sample.xldc
import akka.actor._
import akka.util.Timeout
import akka_sample.xldc.TransactionObj._
import akka_sample.xldc.persist._
import akka_sample.xldc.master._
import akka_sample.xldc.client.ClientActor
import com.typesafe.config.ConfigException
import akka_sample.xldc.master.DbActor
import akka.io.IO
import spray.can.Http
import akka_sample.xldc.master.httpserver.HttpServerActor
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source
import akka.pattern.ask
import akka.util.Timeout


object Main {



  
    
  def main(args: Array[String]) {
    
    
    try{

      new Schedule()


    }catch {
      case ex: ConfigException =>
        println("akka.serverclient.type set: client/server")
      case a: java.lang.Exception =>
        println("config error" + a.toString())
    }
    
  	

  }

}