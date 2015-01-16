package akka_sample.xldc

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor


object ClusterApp {
  
  
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("0"))
    else
      startup(args)
      
     
  }
  
  def startup(ports: Seq[String]): Unit = {
    
//    ports.foreach(
//    		port =>{
//    		  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
//    		  withFallback(ConfigFactory.load())
//
//    		  // Create an Akka system
//    		  val system = ActorSystem("ClusterSystem", config)
//    		  // Create an actor that handles cluster domain events
//    		  system.actorOf(Props[ClusterListener], name = "clusterListener" + port)
//    		}
//    
//    )
    
    val system = ActorSystem("ClusterSystem")
     import system.dispatcher
    val remotePath =
      "akka.tcp://ClusterSystem@127.0.0.1:2551/user/clusterListener2551"
    val actor = system.actorSelection(remotePath)
    actor ! "else"
    
    
  }
  
  

}