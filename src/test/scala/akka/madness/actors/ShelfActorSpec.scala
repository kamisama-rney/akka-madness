package akka.madness.actors

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit}
import akka.madness.actors
import akka.madness.config.ShelfSettings
import akka.madness.schema.Order
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level

class ShelfActorSpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString(ShelfActorSpec.config))
  with AnyWordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A Shelf actor" should {
    "store an order if there's room" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Create an actor instance
      // spawn a new Shelf actor as child of the TestKit's guardian actor
      val shelfActor = spawn(ShelfActor(probe.ref, ShelfSettings("test-shelf", 10, 1, overflow = false)), "test-shelf1")

      // Send an order to the shelf
      shelfActor ! ShelfActor.MoveOrder(ShelfActorSpec.testOrder1)
      shelfActor ! ShelfActor.ReturnShelfContents(contentsProbe.ref)

      val contents = contentsProbe.receiveMessages(1)
      val orders = contents.head.orders
      orders.length should be(1)
      val firstOrder = orders.head

      firstOrder should be(ShelfActorSpec.testOrder1)
      testKit.stop(shelfActor)
    }

    "move it to the overflow shelf if there's no room" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Create an actor instance
      // spawn a new Shelf actor as child of the TestKit's guardian actor
      val shelfActor = spawn(ShelfActor(probe.ref, ShelfSettings("test-shelf", 1, 1, overflow = false)), "test-shelf2")

      // Send two orders to the shelf that can hold 1 and request a list of contents
      shelfActor ! ShelfActor.MoveOrder(ShelfActorSpec.testOrder1)
      shelfActor ! ShelfActor.MoveOrder(ShelfActorSpec.testOrder2)
      shelfActor ! ShelfActor.ReturnShelfContents(contentsProbe.ref)

      // Process responses from Actor
      val contents = contentsProbe.receiveMessage().orders
      val movedOrder = probe.receiveMessage().asInstanceOf[actors.KitchenActor.MoveOrder]

      // Validate contents
      contents.length should be(1)
      contents.head should be(ShelfActorSpec.testOrder1)
      movedOrder.order should be(ShelfActorSpec.testOrder2)
      movedOrder.tossIfNoRoom should be(false)
      testKit.stop(shelfActor)
    }

    "toss the order if moved from overflow shelf back to a temperature shelf" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Create an actor instance
      // spawn a new Shelf actor as child of the TestKit's guardian actor
      val shelfActor = spawn(ShelfActor(probe.ref, ShelfSettings("test-shelf", 1, 1, overflow = false)), "test-shelf3")

      // Send an order to the shelf
      shelfActor ! ShelfActor.MoveOrder(ShelfActorSpec.testOrder1)
      shelfActor ! ShelfActor.MoveOrder(ShelfActorSpec.testOrder2, tossIfNoRoom = true)
      shelfActor ! ShelfActor.ReturnShelfContents(contentsProbe.ref)

      // Process responses from Actor
      val contents = contentsProbe.receiveMessage().orders

      // Validate contents
      probe.expectNoMessage()
      contents.length should be(1)
      contents.head should be(ShelfActorSpec.testOrder1)
      testKit.stop(shelfActor)
    }

    "attempt to move to a temperature shelf from `overflow` if no room" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Create an actor instance
      // spawn a new Shelf actor as child of the TestKit's guardian actor
      // Run actor in synchronous mode with Behavior test kit
      val shelfActor = BehaviorTestKit(ShelfActor.apply(probe.ref, ShelfSettings("test-shelf", 1, 1, overflow = true), testing = true))

      // Send an order to the shelf
      shelfActor.run(ShelfActor.MoveOrder(ShelfActorSpec.testOrder1))
      shelfActor.run(ShelfActor.MoveOrder(ShelfActorSpec.testOrder2))
      shelfActor.run(ShelfActor.ReturnShelfContents(contentsProbe.ref))

      // Process responses from Actor
      val contents = contentsProbe.receiveMessage().orders
      val rejectedOrder = probe.receiveMessage().asInstanceOf[KitchenActor.MoveOrder]

      // Validate contents
      rejectedOrder.tossIfNoRoom should be(true)
      rejectedOrder.order should be(ShelfActorSpec.testOrder1)
      contents.length should be(1)
      contents.head should be(ShelfActorSpec.testOrder2)
    }

    "allow the pickup of the order by a driver" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Create an actor instance
      // spawn a new Shelf actor as child of the TestKit's guardian actor
      val shelfActor = spawn(ShelfActor(probe.ref, ShelfSettings("test-shelf", 1, 1, overflow = false)), "test-shelf4")

      // Send an order to the shelf
      shelfActor ! ShelfActor.MoveOrder(ShelfActorSpec.testOrder1)
      shelfActor ! ShelfActor.PickupOrder("testDriver", ShelfActorSpec.testOrder1.id)
      shelfActor ! ShelfActor.ReturnShelfContents(contentsProbe.ref)

      // Process responses from Actor
      val contents = contentsProbe.receiveMessage().orders
      contents.length should be(0)
      testKit.stop(shelfActor)
    }

    //    "report that there is no order to pickup" in {
    //      // define a probe which allows it to easily send messages
    //      val probe = createTestProbe[KitchenActor.KitchenCommand]()
    //
    //      // Run actor in synchronous mode with Behavior test kit
    //      val shelfActor = BehaviorTestKit(ShelfActor.apply(probe.ref, ShelfSettings("test-shelf", 1, 1, overflow = false), testing = true))
    //      shelfActor.run(ShelfActor.PickupOrder("testDriver", ShelfActorSpec.testOrder1.id))
    //
    //      // Process results
    //      val logEntries = shelfActor.logEntries()
    //      val noOrderLogEntry = logEntries.head
    //      noOrderLogEntry.level should be(Level.WARN)
    //      noOrderLogEntry.message should be(s"testDriver unable to pickup order: ${ShelfActorSpec.testOrder1.id}")
    //    }

    "age an order" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Run actor in synchronous mode with Behavior test kit
      val shelfActor = BehaviorTestKit(ShelfActor.apply(probe.ref, ShelfSettings("test-shelf", 1, 2, overflow = false), testing = true))
      shelfActor.run(ShelfActor.MoveOrder(ShelfActorSpec.testOrder1))
      shelfActor.run(ShelfActor.AgeOrders)
      shelfActor.run(ShelfActor.AgeOrders)
      shelfActor.run(ShelfActor.ReturnShelfContents(contentsProbe.ref))

      // Process results
      val contents = contentsProbe.receiveMessage().orders
      contents.head.age shouldBe Some(4)

      val logEntries = shelfActor.logEntries()
      val orderOnShelf = logEntries.head
      orderOnShelf.level should be(Level.INFO)
      orderOnShelf.message should be(s"Added: ${ShelfActorSpec.testOrder1} onto the testkit shelf, contents now:\n${ShelfActorSpec.testOrder1}")
    }

    "expire an order when the value hits zero" in {
      // define a probe which allows it to easily send messages
      val probe = createTestProbe[KitchenActor.KitchenCommand]()
      val contentsProbe = createTestProbe[ShelfActor.ShelfContents]()

      // Run actor in synchronous mode with Behavior test kit
      val shelfActor = BehaviorTestKit(ShelfActor.apply(probe.ref, ShelfSettings("test-shelf", 1, 2, overflow = false), testing = true))
      shelfActor.run(ShelfActor.MoveOrder(ShelfActorSpec.dyingOrder))
      shelfActor.run(ShelfActor.AgeOrders)
      shelfActor.run(ShelfActor.ReturnShelfContents(contentsProbe.ref))

      // Process results
      val contents = contentsProbe.receiveMessage().orders
      contents.length should be(0)

      val logEntries = shelfActor.logEntries()
      // Expecting 3 log entries
      logEntries.length should be(3)

      // Second log entry should be the expire item message
      val expiredOrder = logEntries(1)
      expiredOrder.level should be(Level.INFO)
      // Check message contents updating order age to match after AgeOrders message
      expiredOrder.message should be(s"${ShelfActorSpec.dyingOrder.copy(age = Some(6))} is no longer saleable, discarding")
    }
  }
}

object ShelfActorSpec {
  final val testOrder1 = Order(UUID.randomUUID().toString, "test order 1", "frozen", 10, 0.50, None, None)
  final val testOrder2 = Order(UUID.randomUUID().toString, "test order 2", "frozen", 5, 0.50, None, None)
  final val dyingOrder = Order(UUID.randomUUID().toString, "test order 2", "frozen", 5, 1, Some(4), None)

  final val config =
    """
      |akka.typed {
      |  loggers = ["akka.testkit.TestEventListener"]
      |}
      |""".stripMargin
}