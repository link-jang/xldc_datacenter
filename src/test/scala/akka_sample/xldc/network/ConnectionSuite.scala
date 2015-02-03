package akka_sample.xldc.network

import org.scalatest.FunSuite
import java.nio._
import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class  ConnectionSuite extends FunSuite {
  
	test("security default off") {
   
    val manager = new ConnectionManager(2553)
    var receivedMessage = false
    manager.onReceiveMessage( (msg: Message) => {
      receivedMessage = true
      None
    })

    val size = 10 * 1024 * 1024
    val buffer = ByteBuffer.allocate(size).put(Array.tabulate[Byte](size)(x => x.toByte))
    buffer.flip

    val bufferMessage = Message.createBufferMessage(buffer.duplicate)
    Await.result(manager.sendMessageReliably("192.168.109.195", bufferMessage), 10 seconds)

    assert(receivedMessage == true)

//    manager.stop()
  }

}