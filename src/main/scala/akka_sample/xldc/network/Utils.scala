package akka_sample.xldc.network
import java.util.concurrent.{ConcurrentHashMap, Executors, ThreadFactory, ThreadPoolExecutor}
import com.google.common.io.{ByteStreams, Files}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.io._


object Utils {
  
  private val daemonThreadFactoryBuilder: ThreadFactoryBuilder =
    new ThreadFactoryBuilder().setDaemon(true)
  
  def namedThreadFactory(prefix: String): ThreadFactory = {
    daemonThreadFactoryBuilder.setNameFormat(prefix + "-%d").build()
  }
  
  def newDaemonCachedThreadPool(prefix: String): ThreadPoolExecutor = {
    val threadFactory = namedThreadFactory(prefix)
    Executors.newCachedThreadPool(threadFactory).asInstanceOf[ThreadPoolExecutor]
  }
  
  def startServiceOnPort[T](
      startPort: Int,
      startService: Int => (T, Int),
      serviceName: String = ""): (T, Int) = {
    
    val serviceString = if (serviceName.isEmpty) "" else s" '$serviceName'"
    val maxRetries = 10
    
    for (offset <- 0 to maxRetries) {
      
      // Do not increment port if startPort is 0, which is treated as a special port
      val tryPort = if (startPort == 0) {
        startPort
      } else {
        // If the new port wraps around, do not try a privilege port
        ((startPort + offset - 1024) % (65536 - 1024)) + 1024
      }
      try {
        val (service, port) = startService(tryPort)
        println(s"Successfully started service$serviceString on port $port.")
        return (service, port)
      } catch {
        case e: Exception  =>
          println(s"Service$serviceString could not bind on port $tryPort. " +
            s"Attempting port ${tryPort + 1}.")
            
      }
      

    }   
    
    return null

  }
  


}