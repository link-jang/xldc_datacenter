package akka_sample.xldc.master

import akka.actor.Actor
import akka.actor.ActorLogging
import spray.http.HttpMethods._
import spray.http.HttpRequest
import spray.http._
import spray.http.Uri._
import spray.routing.RequestContext
import HttpMethods._
import MediaTypes._
import spray.util._
import spray.can.Http
import akka.util.Timeout
import MediaTypes._
class HttpServerActor extends Actor with ActorLogging {
  
//  implicit val timeout: Timeout = 1.second // for the actor 'asks'
  import context.dispatcher
  
   def receive = {
    
    case ex: Http.Connected => 
      sender ! Http.Register(self)
      
    case HttpRequest(GET, Uri.Path("/"), _, _, _)=>
      sender ! index
      
    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")
      
    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(
        status = 500,
        entity = "The " + method + " request to '" + uri + "' has timed out..."
      )
      
    
  }
  
  
  lazy val index = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Say hello to <i>spray-can</i>!</h1>
          <p>Defined resources:</p>
          <ul>
            <li><a href="/ping">/ping</a></li>
          </ul>

        </body>
      </html>.toString()
    ))

}