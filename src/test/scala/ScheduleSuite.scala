import java.text.SimpleDateFormat
import java.util.Calendar

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
 * Created by root on 1/30/15.
 */


@RunWith(classOf[JUnitRunner])
class ScheduleSuite extends FunSuite {

  test("get local date time"){
    var call: Calendar = Calendar.getInstance()
    val date :SimpleDateFormat = new SimpleDateFormat("yyyyMMdd")
    val hour: Int = call.getTime.getHours
    val day: String = date.format(call.getTime)

    assert(hour === 14)
    assert(day === "20150201")
  }

}
