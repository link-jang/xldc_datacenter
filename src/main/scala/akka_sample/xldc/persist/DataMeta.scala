package akka_sample.xldc.persist

import org.squeryl.SessionFactory

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.KeyedEntity
import org.squeryl.annotations.Column
import java.util.Date
import java.sql.Timestamp
import org.squeryl.Session
import org.squeryl.adapters.MySQLAdapter
import java.sql.Timestamp
import spray.json._
import DefaultJsonProtocol._


abstract class Meta

case class RsyncDataMeta (
  val id: Long,
  val dataName: String,
  val oriMechin: String,
  val oriFile: String,
  val destMechin: String,
  val desFile: String,
  val fileType:String
 )extends Meta with KeyedEntity[Long]

//case class NamedList[A](name: String, items: List[A])


object syncDataMetaProto extends DefaultJsonProtocol {
//  implicit def RsyncDataMetaListFormat[A :JsonFormat] = jsonFormat2(NamedList.apply[A])
  implicit val RsyncDataMetaFormat = jsonFormat7(RsyncDataMeta)
}


case class TaskMeta (
  val id: Long,
  val dataId: Long,
  val oriMechin: String,
  val oriFile: String,
  val desMechin: String,
  val desFile: String,
  val taskType: String,
  val day: String,
  val hour: String,
  val isExits: Boolean,
  val isNotified: Boolean,
  val notifiedTime: Option[String],
  val isCompleted:Boolean,
  val completedTime: Option[String],
  val filesize: Long,
  val md5: String
)extends Meta with KeyedEntity[Long]{
  
  def this(dataId: Long, oriMechin:String, oriFile:String, desMechin:String, desFile: String, taskType: String, day: String, hour: String, isExits: Boolean, isNotified: Boolean, isCompleted:Boolean) 
  	= this(0, dataId, oriMechin, oriFile, desMechin, desFile, taskType, day, hour, isExits, isNotified, Some(new Timestamp(0).toString()), isCompleted, Some(new Timestamp(0).toString()),0, "")
}


object taskMetaProto extends DefaultJsonProtocol {
  implicit val taskDataMetaFormat = DefaultJsonProtocol.jsonFormat16(TaskMeta)
}

object xldc_db extends Schema{
  
  val rsyncDataMeta = table[RsyncDataMeta]
  on(rsyncDataMeta){
    b => declare(
    		b.id is (autoIncremented)
    )
  }
  
  val taskMeta = table[TaskMeta]
  on(taskMeta) {
    b => declare(
    	b.id is (autoIncremented)
    )
  }
  
  
}