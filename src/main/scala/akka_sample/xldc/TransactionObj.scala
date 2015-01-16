package akka_sample.xldc
import akka_sample.xldc.persist._



object TransactionObj {
  
  case class FileStatus(taskId: Long, isExists: Boolean, md5: String)

  abstract class RpcObj
  
  case class RequestStr(str: String) extends RpcObj
  
  case class RequstCheckFile(taskType: Array[TaskMeta]) extends RpcObj
  
  case class ResponseCheckFile(status: Array[FileStatus]) extends RpcObj
  
  case class RequstGenerTask(taskType: String, day: String, hour: String) extends RpcObj
  
  case class ResponseGenerTask(taskType: Boolean) extends RpcObj
  
  case class RequstGenerFile(file: RsyncDataMeta) extends RpcObj
  
  case class ResponseGenerFilek(taskType: Boolean) extends RpcObj
  
  
  case class RequstExistTask(taskType: String) extends RpcObj
  
  case class ResponseExistTask(taskType: Array[TaskMeta]) extends RpcObj
  
  case class RequstNotifyTask(taskType: String) extends RpcObj
  
  case class ResponseNotifyTask(taskType: Array[TaskMeta]) extends RpcObj
  
  case class RequstCompleteTask(taskType: String) extends RpcObj
  
  case class ResponseCompleteTask(taskType: Array[TaskMeta]) extends RpcObj
  
  
  case class UpdateExistTask(filestatus: FileStatus) extends RpcObj
  
  case class UpdateNotifyTask(id: Long, status: Boolean) extends RpcObj
  
  case class UpdateCompleteTask(id: Long, status: Boolean) extends RpcObj
  
  
  case class NotifyClient(task: TaskMeta) extends RpcObj
  
  
  case class RpcNotify(task: TaskMeta) extends RpcObj
  
  case class RpcResponseNotify(task: TaskMeta, status: Boolean) extends RpcObj
  
  case class RpcResponseComplete(task: TaskMeta, status: Boolean) extends RpcObj
  
  
  
}