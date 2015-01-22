package akka_sample.xldc
import akka.actor.ActorSystem

object Configrature {
  
  def servertype(system: ActorSystem) = system.settings.config.getValue("akka.serverclient.type").unwrapped().toString()
  def serverPath(system: ActorSystem) = system.settings.config.getValue("akka.serverclient.server").unwrapped().toString()
  def clientPath(system: ActorSystem) = system.settings.config.getValue("akka.serverclient.client").unwrapped().toString().replaceAll("(\\[|\\]|\\s+)", "").split(",")
  
  
  def jdbcuser(system: ActorSystem) = system.settings.config.getValue("akka.serverclient.dbconfig.user").unwrapped().toString()
  def jdbcpassword(system: ActorSystem) = system.settings.config.getValue("akka.serverclient.dbconfig.password").unwrapped().toString()
  def jdbcurl(system: ActorSystem) = system.settings.config.getValue("akka.serverclient.dbconfig.jdbcurl").unwrapped().toString()
  
  def webhost(system: ActorSystem) = system.settings.config.getValue("akka.web.hostname").unwrapped().toString()
  def webport(system: ActorSystem) = system.settings.config.getValue("akka.web.port").unwrapped().toString().toInt

}