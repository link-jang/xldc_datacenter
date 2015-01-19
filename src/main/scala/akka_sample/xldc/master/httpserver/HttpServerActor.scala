package akka_sample.xldc.master.httpserver

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import spray.http.HttpMethods._
import spray.http.HttpRequest
import spray.http._
import spray.http.Uri._
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.util._
import spray.can.Http
import spray.http.MediaTypes._
import akka.actor.actorRef2Scala
import spray.http.ContentType.apply
import spray.http.HttpEntity.apply
import spray.http.StatusCode.int2StatusCode
import scala.io.Source
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import akka_sample.xldc.TransactionObj._
import akka_sample.xldc.persist.RsyncDataMeta
import akka_sample.xldc.persist._

import spray.json._
import DefaultJsonProtocol._

import spray.util._

class HttpServerActor(dbactor: ActorRef) extends Actor with ActorLogging {
  

 
  import context.dispatcher
  
   def receive = {
    
    case ex: Http.Connected => 
      sender ! Http.Register(self)
      
    case HttpRequest(GET, Uri.Path("/"), _, _, _)=>
      println("1 " + System.currentTimeMillis())
      sender ! index
      
    case HttpRequest(GET, Uri.Path("/metadata"), _, _, _)=>

      sender ! metadata
      
      
    case HttpRequest(GET, Uri.Path("/monitortab"), _, _, _) =>
      
      sender ! taskdata
      
    case HttpRequest(GET, Uri.Path("/stat/css/bootstrap.min.css"), _, _, _) =>
      println("2 " + System.currentTimeMillis())
      sender ! bootstrap_stat(Array("/stat/css/bootstrap.min.css"))
      
      
    case HttpRequest(GET, Uri.Path("/stat/js"), _, _, _) =>
      
      sender ! bootstrap_js(Array("/stat/js/jquery-1.11.2.min.js", "/stat/js/bootstrap.min.js")) 
      
      
    
    case HttpRequest(GET, Uri.Path("/getMetaData_json"), _, _, _) =>{  
      
      
      implicit val timeout = Timeout(5 seconds)
      val future = dbactor ? RequstMetaData("hour")
      val result = Await.result(future, timeout.duration).asInstanceOf[ResponseMetaData]
      
      import syncDataMetaProto._
      
      val json = result.files.toList.toJson.toString
    
      println(json)
      sender ! HttpResponse(
          
          entity = HttpEntity(`application/json`, json)
       )
    }
    
    case HttpRequest(GET, Uri.Path("/getTaskData_json"), _, _, _) =>{ 
      
      println("webactor")
      
      implicit val timeout = Timeout(5 seconds)
      val future = dbactor ? RequstTaskData
      val result = Await.result(future, timeout.duration).asInstanceOf[ResponseTaskData]
      import taskMetaProto._
      val json = result.tasks.toList.toJson.toString
      
      println(json)
      
      sender ! HttpResponse(
          
          entity = HttpEntity(`application/json`, json)
       )
      
    }
      
    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")
      
    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out..."
      )
      
    
  }
  

  
  def  bootstrap_stat(urlpath: Array[String]): HttpResponse ={
    
    var str = urlpath.map(url => Source.fromFile(new java.io.File(this.getClass().getResource(url).getPath())).getLines.reduce(_ + "\n" +  _)).reduce(_ + "\n" +  _)

    HttpResponse(    
      	entity = HttpEntity(`text/css`, str)
  		)
	}
  
  def  bootstrap_js(urlpath: Array[String]): HttpResponse ={
    
    var str = urlpath.map(url => Source.fromFile(new java.io.File(this.getClass().getResource(url).getPath())).getLines.reduce(_ + "\n" +  _)).reduce(_ + "\n" +  _)

    HttpResponse(    
      	entity = HttpEntity(str)
  		)
	}

  def index : HttpResponse = {
    val str = Source.fromFile(new java.io.File(this.getClass().getResource("/stat/html/index.html").getPath())).getLines.reduce(_ + "\n" +  _)
//    print(str)
    HttpResponse(
    		entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), str)
      )
    
  }
  
  val  metadata: HttpResponse = {
    val str = Source.fromFile(new java.io.File(this.getClass().getResource("/stat/html/metaData.html").getPath())).getLines.reduce(_ + "\n" +  _)
    HttpResponse(
    		entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), str)
      )
  }

  val taskdata: HttpResponse = {
    val str = Source.fromFile(new java.io.File(this.getClass().getResource("/stat/html/taskData.html").getPath())).getLines.reduce(_ + "\n" +  _)
    HttpResponse(
    		entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), str)
      )
  }
  
  
  
}





