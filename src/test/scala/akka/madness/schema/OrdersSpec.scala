package akka.madness.schema

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class OrdersSpec extends AnyWordSpecLike with Matchers {

  "An Orders class" should {
    "successfully load a json file of orders" in {
      val testfilePath = getClass.getResource("/testfile.json").getPath
      val orders = Orders.loadFromFile(testfilePath)
      orders.items.length should be(2)
    }

    "throw an exception if there is an error in the json" in {
      val testfilePath = getClass.getResource("/badtestfile.json").getPath
      assertThrows[IllegalArgumentException] {
        Orders.loadFromFile(testfilePath)
      }
    }

    "load the data correctly and calculate remaining value" in {
      val testRecords = new Orders(OrdersSpec.testJson)
      val testRecord = testRecords.items(0)

      testRecord.id should be("a8cfcb76-7f24-4420-a5ba-d46dd77bdffd")
      testRecord.name should be("Banana Split")
      testRecord.temp should be("frozen")
      testRecord.shelfLife should be(20)
      testRecord.decayRate should be(1.0)
    }

    "calculate a correct value based on object settings" in {
      val testRecord = Order("id", "foo", "cold", 20, 1, Some(0), Some("Excellent Foods"))

      testRecord.copy(age = Some(1)).value() should be(0.95)
      testRecord.copy(age = Some(10)).value(2) should be(0)
      testRecord.copy(age = Some(20)).value() should be(0)
    }
  }
}

object OrdersSpec {
  val testJson: String =
    """
      |[
      |  {
      |    "id": "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd",
      |    "name": "Banana Split",
      |    "temp": "frozen",
      |    "shelfLife": 20,
      |    "decayRate": 1
      |  }
      |]
      |""".stripMargin
}
