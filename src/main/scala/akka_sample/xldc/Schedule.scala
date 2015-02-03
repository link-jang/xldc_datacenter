package akka_sample.xldc

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit, ThreadPoolExecutor}

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.io.IO
import akka.util.Timeout
import akka_sample.xldc.TransactionObj._

import akka_sample.xldc.client.ClientActor
import akka_sample.xldc.master.httpserver.HttpServerActor
import akka_sample.xldc.master.{ServerActor, DbActor}
import akka_sample.xldc.network.{BufferMessage, Message, ConnectionManager, Utils}
import akka_sample.xldc.persist.RsyncDataMeta
import io.netty.util.{ TimerTask, HashedWheelTimer}
import spray.can.Http

import scala.collection.mutable.HashMap
import scala.concurrent.Await
import akka.pattern.ask

import akka.util.Timeout
import scala.concurrent.duration._
import Configrature._

import scala.util.control.NonFatal

/**
 * Created by root on 1/27/15.
 */

class NoteType(Value :String)  {
  var value =
  Value.toLowerCase() match {
    case "server" => NoteType.Server
    case "client" => NoteType.Client
  }
}

object NoteType extends Enumeration{
  type noteMode = Value
  val Server, Client = Value
}




class Schedule () {



  val clientActorMap = new HashMap[String, ActorSelection]
  private val scheduleSystem = Configrature.system


  val noteType = new NoteType(Configrature.servertype(scheduleSystem))

  val datatport = Configrature.datatport(scheduleSystem)

  private val dagSchedulerActorSupervisor =
    scheduleSystem.actorOf(Props(new DAGSchedulerActorSupervisor),"supper")

  private val dAGSchedulerActorProp = Props(classOf[DAGSchedulerActor], this)
  var dagSchedulerActor:ActorRef = _



  private val dbProp = Props(classOf[DbActor],
    Array(Configrature.jdbcurl(scheduleSystem), Configrature.jdbcuser(scheduleSystem), Configrature.jdbcpassword(scheduleSystem)))
  var dbActor: ActorRef = _

  private var serverProp: Props = _
  var serverActor: ActorSelection = _

  private val clientProp = Props(classOf[ClientActor], this)
  private var clientActor: AnyRef = _

  private var webProp: Props = _

  private val createTaskThread =
    new HashedWheelTimer(Utils.namedThreadFactory("Create task Thread"))
  private val ackTimeout = 5


  private val handleTaskExecutor = new ThreadPoolExecutor(
    4,
    4,
    60, TimeUnit.SECONDS,
    new LinkedBlockingDeque[Runnable](),
    Utils.namedThreadFactory("handle-read-write-executor")) {

    override def afterExecute(r: Runnable, t: Throwable): Unit = {
      super.afterExecute(r, t)
      if (t != null && NonFatal(t)) {
        println("Error in handleReadWriteExecutor is not handled properly", t)
      }
    }
  }

  var manager: ConnectionManager = _


  def onDataRecieve(msg: Message) = {
    var array = new Array[Byte](msg.size)
    msg.asInstanceOf[BufferMessage].buffers(0).get(array)


    println( "_______________" + new String(array))
    None
  }


  implicit val timeout = Timeout(5 seconds)





  def initActor(): Unit ={

    noteType.value match {
      case NoteType.Server => {

        dbActor = createNewActor(dagSchedulerActorSupervisor, dbProp, "dbActor")

        serverProp = Props(classOf[ServerActor], dbActor)
        val serverActor1 = createNewActor(dagSchedulerActorSupervisor, serverProp, "serverActor")

//        serverActor = getSelectActor(List(Configrature.serverPath(scheduleSystem)))(0)

//        clientActor = createNewActor(dagSchedulerActorSupervisor, clientProp, "clientActor")

        webProp = Props(classOf[HttpServerActor], dbActor)

        dagSchedulerActor = createNewActor(dagSchedulerActorSupervisor, dAGSchedulerActorProp, "dAGSchedulerActor" )

        val webhandle = createNewActor(dagSchedulerActorSupervisor, webProp, "webActor")

        IO(Http) ! Http.Bind(webhandle, interface = Configrature.webhost(scheduleSystem), port = Configrature.webport(scheduleSystem).toInt)
//        getClientActor(Configrature.clientPath(scheduleSystem).toList)
        clientActorMap.foreach(a => println(a._1))

      }
      case NoteType.Client => {


      }
    }

    clientActor = createNewActor(dagSchedulerActorSupervisor, clientProp, "clientActor")
    getClientActor(Configrature.clientPath(scheduleSystem).toList)
    serverActor = getSelectActor(List(Configrature.serverPath(scheduleSystem)))(0)

    manager = new ConnectionManager( datatport)

    manager.onReceiveMessage(onDataRecieve)

  }



  def createNewActor(parentActor: ActorRef, props: Props, name: String = ""): ActorRef = {
    val future = parentActor ? (props, name)
    val resultActor = Await.result(future, timeout.duration).asInstanceOf[ActorRef]
    println(resultActor.path)
    resultActor
  }

  def getSelectActor(paths: List[String]): List[ActorSelection] = {

    paths.map(path => scheduleSystem.actorSelection(path)).toList
  }

  def getClientActor(paths: List[String]): Unit = {

    for(path <- paths) {
      clientActorMap +=(path.substring(path.indexOf("@") + 1, path.indexOf("/user")) -> scheduleSystem.actorSelection(path))
    }

  }


  initActor



  private class DAGSchedulerActorSupervisor()
    extends Actor with ActorLogging {

    override val supervisorStrategy =
      OneForOneStrategy() {
        case x: Exception =>
          log.error("eventProcesserActor failed; shutting down SparkContext", x)
          try {
//            dagScheduler.doCancelAllJobs()
          } catch {
            case t: Throwable => log.error("DAGScheduler failed to cancel all jobs.", t)
          }
//          dagScheduler.sc.stop()
          Stop
      }

    def receive = {
      case p: (Props, String) =>
        sender ! context.actorOf(p._1, name = p._2)

      case RequstExistTask(fileType) =>
        dbActor ? RequstExistTask("hour")
      case ResponseExistTask(tasks) =>
        tasks.foreach(task =>
          clientActorMap.get(task.oriMechin).asInstanceOf[ActorSelection] ! RequstCheckFile(Array(task))
        )

      case _ => log.error("received unknown message in DAGSchedulerActorSupervisor")
    }
  }





  def generateTask(): Unit ={



      handleTaskExecutor.execute(new Runnable {
        override def run(): Unit = {
          var call: Calendar = Calendar.getInstance()
          val date: SimpleDateFormat = new SimpleDateFormat("yyyyMMdd")
          val hour: Int = call.getTime.getHours
          val day: String = date.format(call.getTime).toString

          dbActor ! RequstGenerTask("hour", day, hour.toString)
          println("schedule start:" + System.currentTimeMillis())
        }
      })






  }
  generateTask



  def checkFileExistsTask(): Unit = {

      handleTaskExecutor.execute(new Runnable{
        override def run(): Unit = {

          dagSchedulerActor ! RequstExistTask("hour")
        }
      })


  }
  checkFileExistsTask



  def notifyClientTask():Unit = {

    handleTaskExecutor.execute(new Runnable{
      override def run(): Unit = {

        dagSchedulerActor ! RequstNotifyTask("hour")
      }
    })
  }
  while (true) {
    notifyClientTask
    Thread.sleep(5000)
  }



}



private class DAGSchedulerActor(scheduleHandle: Schedule) extends Actor with ActorLogging {



  def receive = {

    case RequstExistTask(fileType) =>
      scheduleHandle.dbActor ! RequstExistTask("hour")


    case ResponseExistTask(tasks) =>
      tasks.foreach(task =>
      {
        val actor = scheduleHandle.clientActorMap.get(task.oriMechin)
        if(actor.isDefined)
        actor.get.asInstanceOf[ActorSelection] ! RequstCheckFile(Array(task))
      }
      )

    case ResponseCheckFile(filesStatus) =>
      filesStatus.foreach(file =>
        scheduleHandle.dbActor ! UpdateExistTask(file)
      )


    case RequstNotifyTask(fileType) =>
      scheduleHandle.dbActor ! RequstNotifyTask("hour")

    case ResponseNotifyTask(taskArray) =>
      taskArray.foreach(
        task => {
          val destActor = scheduleHandle.clientActorMap.get(task.desMechin)
          if (destActor.isDefined) {
            destActor.get ! NotifyClient(task)
          }
        }
      )

    case RpcResponseNotify(task, status) =>
      if(status){
        scheduleHandle.dbActor ! UpdateNotifyTask(task.id, true)
      }

    case RpcResponseComplete(task, status) =>

      scheduleHandle.dbActor ! UpdateCompleteTask(task.id, status)

    case _ => println("don't kown")

  }


}