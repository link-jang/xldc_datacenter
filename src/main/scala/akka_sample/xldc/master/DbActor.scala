package akka_sample.xldc.master
import akka.actor.Actor
import akka.actor.ActorLogging
import org.squeryl.SessionFactory
import org.squeryl.Session
import org.squeryl.adapters.MySQLAdapter
import org.squeryl.PrimitiveTypeMode._
import akka_sample.xldc.persist._
import akka_sample.xldc.persist.RsyncDataMeta
import akka_sample.xldc.TransactionObj._




class DbActor(args: Array[String]) extends Actor with ActorLogging{
  
  require(args.length == 3)
  
  override def preStart(): Unit = {
    println("dbactor 1")
    Class.forName("com.mysql.jdbc.Driver");
    SessionFactory.concreteFactory = Some(() =>
    	Session.create(
    	java.sql.DriverManager.getConnection(args(0), args(1), args(2)),
    	new MySQLAdapter)
    )

    transaction{
      xldc_db.rsyncDataMeta.schema.drop
      xldc_db.rsyncDataMeta.schema.create
    }

    
  }
  
  def receive = {
    
    case RequstCompleteTask(fileType) => {
      try{
        transaction {
          def tasks = 
            from(xldc_db.taskMeta) (s => where (s.isCompleted === false) select (s))
          sender ! ResponseCompleteTask(tasks.toArray)
        }
        
      }catch{
        case ex: java.lang.Exception => 
          log.error("error while get isCompleted from taskMeta : " + ex.toString())
          sender ! ResponseCompleteTask(new Array[TaskMeta](0))
      }
    }

    case RequstNotifyTask(fileType) => {
      try{
        transaction {
          def tasks = 
            from(xldc_db.taskMeta) (s => where (s.isNotified === false and s.isExits === true) select (s))
          sender ! ResponseNotifyTask(tasks.toArray)
        }
        
      }catch{
        case ex: java.lang.Exception => 
          log.error("error while get isnotifyed from taskMeta : " + ex.toString())
          sender ! ResponseNotifyTask(new Array[TaskMeta](0))
      }
    }
    
    case RequstExistTask(fileType) => {
      try{
	      transaction{
	        def tasks = 
	        from (xldc_db.taskMeta) (s => where(s.isExits === false)select(s) )
	        sender ! ResponseExistTask(tasks.toArray)
	      }
      
      }catch {
        case ex: java.lang.Exception => 
          log.error("error while get from taskMeta : " + ex.toString())
          sender ! ResponseExistTask(new Array[TaskMeta](0))
          
      }
      
    }
    
    
    case UpdateExistTask(filestatus) => {
      try{
	      transaction{
	        
	        update (xldc_db.taskMeta) (s => 
	          where(s.id === filestatus.taskId)
	          set (s.isExits := filestatus.isExists, s.md5 := filestatus.md5)
	          )
	      }
      
      }catch {
        case ex: java.lang.Exception => 
          log.error("error while update isExits taskMeta : " + ex.toString())
      }
      
    }
    
   case UpdateNotifyTask(id, status) => {
      try{
	      transaction{
	        
	        update (xldc_db.taskMeta) (s => 
	          where(s.id === id)
	          set (s.isNotified := status)
	          )
	      }
      
      }catch {
        case ex: java.lang.Exception => 
          log.error("error while update notify taskMeta : " + ex.toString())
      }
      
    }
    
    case UpdateCompleteTask(id, status) => {
      try{
	      transaction{
	        
	        update (xldc_db.taskMeta) (s => 
	          where(s.id === id)
	          set (s.isCompleted := status)
	          )
	      }
      
      }catch {
        case ex: java.lang.Exception => 
          log.error("error while update complete taskMeta : " + ex.toString())
      }
      
    }
    
    
    //生成任务
    case RequstGenerTask(fileType, day, hour) => {
      try{
	      transaction{
	        def files = 
	          from(xldc_db.rsyncDataMeta) (s => where(s.fileType === fileType) select(s))
	        
	        files.foreach(file => 
	        	xldc_db.taskMeta.insert(new TaskMeta(file.id,file.oriMechin ,  file.oriFile.replace("${day}", day),
	        	    file.destMechin , file.oriFile.replace("${hour}", hour),fileType, day, hour, false, false, false))
	        )
	        sender ! ResponseGenerTask(true)
	      }
      
      }catch{
        case ex: java.lang.Exception => 
          log.error("error while insert into table TaskMeta : " + ex.toString())
          sender ! ResponseGenerTask(false)
      }
    }
    
    //添加文件
    case RequstGenerFile(file) => {
      try{
	      transaction{
	        xldc_db.rsyncDataMeta.insert(file)
	        sender ! ResponseGenerFilek(false)
	      }
      
      }catch{
        
        case ex: java.lang.Exception => 
          log.error("error while insert into table rsncDataMeta : " + ex.toString())
          sender ! ResponseGenerFilek(false)
      }
      
    }
    
    
    
    
    
    case _ => log.info("")
    
  }
  
  

}