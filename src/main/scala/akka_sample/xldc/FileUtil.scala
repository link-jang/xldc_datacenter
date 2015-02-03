package akka_sample.xldc

import java.io.File;  
import java.io.FileInputStream;  
import java.math.BigInteger;  
import java.security.MessageDigest;  
import java.util.HashMap;  
import java.util.Map; 

object FileUtil {
  
  def getFileMD5(file: java.io.File): String = {
    
    if(!file.isFile())
      return null
      
     var digest: MessageDigest = null 
     var in: FileInputStream = null  
     var buffer = new Array[Byte](1024)
     var len = 0  
     try {  
        digest = MessageDigest.getInstance("MD5");  
        in = new FileInputStream(file);  
        len = in.read(buffer, 0, 1024)
        while (len != -1) {
          digest.update(buffer, 0, len);  
          len = in.read(buffer, 0, 1024)
        }  
        in.close();  
    } catch {  
      case ex: Exception => throw ex
    }  
    val bigInt = new BigInteger(1, digest.digest());  
    return bigInt.toString(16); 
  }
  
  
  def main(args: Array[String]): Unit = {
    println( this.getFileMD5(new java.io.File("/xldc.txt")))
  }
  

}