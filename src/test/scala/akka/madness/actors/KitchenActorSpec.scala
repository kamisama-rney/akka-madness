package akka.madness.actors

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.{ BehaviorTestKit, LogCapturing, ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.madness.actors.KitchenActor._
import akka.madness.config.ShelfSettings
import akka.madness.schema.Order
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level

class KitchenActorSpec extends ScalaTestWithActorTestKit(KitchenActorSpec.config)
  with AnyWordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "a kitchen actor" should {
    "move an order onto the correct shelf" in {
      // Create a set of test child actor probes
      val childProbes: Map[String, TestProbe[ShelfActor.ShelfCommand]] = Map(
        "frozen" -> createTestProbe[ShelfActor.ShelfCommand],
        "cold" -> createTestProbe[ShelfActor.ShelfCommand],
        "overflow" -> createTestProbe[ShelfActor.ShelfCommand]
      )
      def shelfBuilder(name: String, settings: ShelfSettings, overflow: Boolean,
        context: ActorContext[KitchenCommand]): ActorRef[ShelfActor.ShelfCommand] = {
        childProbes(name).ref
      }

      val kitchen = spawn(KitchenActor.apply(shelfBuilder), "kitchen")

      kitchen ! NewOrder(KitchenActorSpec.testOrder1)
      kitchen ! NewOrder(KitchenActorSpec.testOrder2)
      val frozenShelf = childProbes("frozen")
      val messages = frozenShelf.receiveMessages(2)
      messages.head should be(ShelfActor.MoveOrder(KitchenActorSpec.testOrder1))
      messages(1) should be(ShelfActor.MoveOrder(KitchenActorSpec.testOrder2))
      testKit.stop(kitchen)
    }
  }
  "move an order onto the overflow shelf if primary shelf full" in {
    // Create a set of test child actor probes
    val childProbes: Map[String, TestProbe[ShelfActor.ShelfCommand]] = Map(
      "frozen" -> createTestProbe[ShelfActor.ShelfCommand],
      "cold" -> createTestProbe[ShelfActor.ShelfCommand],
      "overflow" -> createTestProbe[ShelfActor.ShelfCommand]
    )
    def shelfBuilder(name: String, settings: ShelfSettings, overflow: Boolean,
      context: ActorContext[KitchenCommand]): ActorRef[ShelfActor.ShelfCommand] = {
      childProbes(name).ref
    }

    val kitchen = spawn(KitchenActor.apply(shelfBuilder), "kitchen2")

    kitchen ! MoveOrder("overflow", KitchenActorSpec.testOrder1)
    // Make sure other shelves didn't get messages
    childProbes("frozen").expectNoMessage()
    childProbes("cold").expectNoMessage()
    val message = childProbes("overflow").receiveMessage
    message should be(ShelfActor.MoveOrder(KitchenActorSpec.testOrder1))
    testKit.stop(kitchen)
  }
  "search all shelves for order to pickup" in {
    // Create a set of test child actor probes
    val childProbes: Map[String, TestProbe[ShelfActor.ShelfCommand]] = Map(
      "frozen" -> createTestProbe[ShelfActor.ShelfCommand],
      "cold" -> createTestProbe[ShelfActor.ShelfCommand],
      "overflow" -> createTestProbe[ShelfActor.ShelfCommand]
    )
    def shelfBuilder(name: String, settings: ShelfSettings, overflow: Boolean,
      context: ActorContext[KitchenCommand]): ActorRef[ShelfActor.ShelfCommand] = {
      childProbes(name).ref
    }

    val kitchen = spawn(KitchenActor.apply(shelfBuilder), "kitchen3")

    kitchen ! PickupOrder("driver", "secretOrder")
    // Make sure other shelves didn't get messages
    childProbes("frozen").expectMessageType[ShelfActor.PickupOrder]
    childProbes("cold").expectMessageType[ShelfActor.PickupOrder]
    val message: ShelfActor.PickupOrder = childProbes("overflow").expectMessageType[ShelfActor.PickupOrder]
    message should be(ShelfActor.PickupOrder("driver", "secretOrder"))
    testKit.stop(kitchen)
  }
  "log error if invalid shelf is specified in the NewOrder message" in {
    val probe = createTestProbe[ShelfActor.ShelfCommand]
    // Create a set of test child actor probes
    val childProbes: Map[String, TestProbe[ShelfActor.ShelfCommand]] = Map(
      "frozen" -> probe,
      "cold" -> probe,
      "hot" -> probe,
      "overflow" -> probe
    )

    def shelfBuilder(name: String, settings: ShelfSettings, overflow: Boolean,
      context: ActorContext[KitchenCommand]): ActorRef[ShelfActor.ShelfCommand] = {
      childProbes(name).ref
    }

    val kitchen = BehaviorTestKit(KitchenActor.apply(shelfBuilder), "kitchen4")
    kitchen.run(NewOrder(KitchenActorSpec.orderBadShelf))

    // Process results
    val logEntries = kitchen.logEntries()
    val noOrderLogEntry = logEntries.head
    noOrderLogEntry.level should be(Level.ERROR)
    noOrderLogEntry.message should be(s"Unable to find a shelf for: ${KitchenActorSpec.orderBadShelf}")
  }

  "log error if invalid shelf is specified in the MoveOrder message" in {
    val probe = createTestProbe[ShelfActor.ShelfCommand]
    // Create a set of test child actor probes
    val childProbes: Map[String, TestProbe[ShelfActor.ShelfCommand]] = Map(
      "frozen" -> probe,
      "cold" -> probe,
      "hot" -> probe,
      "overflow" -> probe
    )

    def shelfBuilder(name: String, settings: ShelfSettings, overflow: Boolean,
      context: ActorContext[KitchenCommand]): ActorRef[ShelfActor.ShelfCommand] = {
      childProbes(name).ref
    }

    val kitchen = BehaviorTestKit(KitchenActor.apply(shelfBuilder), "kitchen5")
    kitchen.run(MoveOrder("frozen2", KitchenActorSpec.orderBadShelf))

    // Process results
    val logEntries = kitchen.logEntries()
    val noOrderLogEntry = logEntries.head
    noOrderLogEntry.level should be(Level.ERROR)
    noOrderLogEntry.message should be(s"Unable to find the `${KitchenActorSpec.orderBadShelf.temp}` shelf for order: ${KitchenActorSpec.orderBadShelf}")
  }
}

object KitchenActorSpec {
  final val testOrder1 = Order(UUID.randomUUID().toString, "test order 1", "frozen", 10, 0.50, None, None)
  final val testOrder2 = Order(UUID.randomUUID().toString, "test order 2", "frozen", 5, 0.50, None, None)
  final val testOrder3 = Order(UUID.randomUUID().toString, "test order 3", "frozen", 5, 0.50, None, None)
  final val orderBadShelf = Order(UUID.randomUUID().toString, "test order 2", "frozen2", 5, 1, Some(4), None)

  final val config = ConfigFactory.parseString(
    """
      |madness {
      |  orderBatchSize: 2
      |  timeBetweenBatches: 1 seconds
      |
      |  kitchen {
      |    name: "Excellent Foods"
      |    shelves = [
      |      {
      |        name: "frozen"
      |        size: 2
      |        ageRate: 1
      |        overflowShelf: "overflow"
      |      },
      |      {
      |        name: "cold"
      |        size: 1
      |        ageRate: 1
      |        overflowShelf: "overflow"
      |      }
      |      {
      |        name: "overflow"
      |        size: 1
      |        ageRate: 2
      |      }
      |    ]
      |  }
      |}
      |akka.typed {
      |  loggers = ["akka.testkit.TestEventListener"]
      |}""".stripMargin)
}