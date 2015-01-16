package akka_sample.xldc
import org.squeryl.SessionFactory
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.annotations.Column
import java.util.Date
import java.sql.Timestamp
import org.squeryl.Session
import org.squeryl.adapters.MySQLAdapter
import akka.actor.ActorSystem
import akka.actor.Props
import akka_sample.xldc.master._
import akka_sample.xldc.TransactionObj._

object DbUtil extends Schema{
  
	class Author(val id: Long, 
              val firstName: String, 
              val lastName: String,
              val email: Option[String]) {
	 def this() = this(0,"","",Some(""))		   
 	}
 
	class Book(val id: Long, 
            var title: String,
            @Column("AUTHOR_ID") // the default 'exact match' policy can be overriden
            var authorId: Long,
            var coAuthorId: Option[Long]) {
   
		def this() = this(0,"",0,Some(0L))
	}
 
	class Borrowal(val id: Long,
                val bookId: Long,
                val borrowerAccountId: Long,
                val scheduledToReturnOn: Date,
                val returnedOn: Option[Timestamp],
                val numberOfPhonecallsForNonReturn: Int)
                
                
  object Library extends Schema {
 
   //When the table name doesn't match the class name, it is specified here :
   val authors = table[Author]("AUTHORS")

   val books = table[Book]

   val borrowals = table[Borrowal]	

   on(borrowals)(b => declare(
     b.numberOfPhonecallsForNonReturn defaultsTo(0),
     b.borrowerAccountId is(indexed),
     columns(b.scheduledToReturnOn, b.borrowerAccountId) are(indexed)
   ))
  
   on(authors)(s => declare(
     s.email      is(unique,indexed("idxEmailAddresses")), //indexes can be named explicitely
     s.firstName  is(indexed),
     s.lastName   is(indexed, dbType("varchar(255)")), // the default column type can be overriden     
     columns(s.firstName, s.lastName) are(indexed) 
   ))
 

}  
 


}

