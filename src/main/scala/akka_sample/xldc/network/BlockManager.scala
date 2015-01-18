package akka_sample.xldc.network

import java.nio.ByteBuffer
import java.nio.{ByteBuffer, MappedByteBuffer}
import sun.nio.ch.DirectBuffer

object BlockManager {
  
  def dispose(buffer: ByteBuffer): Unit = {
    if (buffer != null && buffer.isInstanceOf[MappedByteBuffer]) {
      if (buffer.asInstanceOf[DirectBuffer].cleaner() != null) {
        buffer.asInstanceOf[DirectBuffer].cleaner().clean()
      }
    }
  }

}