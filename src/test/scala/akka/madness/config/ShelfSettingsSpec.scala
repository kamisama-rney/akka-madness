package akka.madness.config

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ShelfSettingsSpec extends AnyWordSpecLike with Matchers {

  val config: Config = ConfigFactory.parseString(ShelfSettingsSpec.config)

  "a ShelfSettings class" should {
    "load all the shelf settings" in {
      val settings = ShelfSettings.fromConfig(config)

      settings.size should be(4)
      settings should contain("frozen" -> ShelfSettings("frozen", 10, 1, overflow = false, "overflow"))
      settings should contain("cold" -> ShelfSettings("cold", 10, 1, overflow = false, "overflow"))
      settings should contain("hot" -> ShelfSettings("hot", 10, 1, overflow = false, "overflow"))
      settings should contain("overflow" -> ShelfSettings("overflow", 15, 2, overflow = true, "none"))
    }
  }
}

object ShelfSettingsSpec {
  final val config =
    """
      |madness {
      |  orderBatchSize: 2
      |  timeBetweenBatches: 1 seconds
      |
      |  kitchen {
      |    name: "Excellent Foods"
    shelves = [
      |      {
      |        name: "frozen"
      |        size: 10
      |        ageRate: 1
      |        overflowShelf: "overflow"
      |      },
      |      {
      |        name: "cold"
      |        size: 10
      |        ageRate: 1
      |        overflowShelf: "overflow"
      |      },
      |      {
      |        name: "hot"
      |        size: 10
      |        ageRate: 1
      |        overflowShelf: "overflow"
      |      },
      |      {
      |        name: "overflow"
      |        size: 15
      |        ageRate: 2
      |      }
      |    ]
      |  }
      |}""".stripMargin
}