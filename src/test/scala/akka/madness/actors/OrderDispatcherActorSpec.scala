package akka.madness.actors

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.madness.actors.KitchenActor.{KitchenCommand, NewOrder}
import akka.madness.actors.OrderDispatcherActor.{AddKitchen, PickupOrder, Start, SystemCommand}
import akka.madness.schema.Order
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.language.postfixOps

class OrderDispatcherActorSpec
  extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "An order dispatcher" should {
    "submit orders to the kitchen" in {
      val probe = createTestProbe[KitchenCommand]

      // Child builder
      def kitchenBuilder(name: String, context: ActorContext[SystemCommand]): ActorRef[KitchenCommand] = {
        probe.ref
      }

      // Create a test actor
      val dispatcher = spawn(OrderDispatcherActor.apply(OrderDispatcherActorSpec.queue, kitchenBuilder), "test")

      // Start the dispatcher
      dispatcher ! AddKitchen("ExcellentFoods")
      dispatcher ! Start

      // See if orders were submitted
      val messages = probe.receiveMessages(3)
      messages.head should be(NewOrder(OrderDispatcherActorSpec.testOrder1))
      messages(1) should be(NewOrder(OrderDispatcherActorSpec.testOrder2))
      messages(2) should be(NewOrder(OrderDispatcherActorSpec.testOrder3))

      testKit.stop(dispatcher)
    }
    "handle pickup order" in {
      val probe2 = createTestProbe[KitchenCommand]("ExcellentFoods2")

      // Child builder
      def kitchenBuilder(name: String, context: ActorContext[SystemCommand]): ActorRef[KitchenCommand] = {
        probe2.ref
      }

      // Create a test actor
      val dispatcher = spawn(OrderDispatcherActor.apply(OrderDispatcherActorSpec.queue, kitchenBuilder), "test2")

      // Start the dispatcher
      dispatcher ! AddKitchen("Excellent Foods")
      dispatcher ! PickupOrder("driver", "Excellent Foods", "order")

      // See if orders were submitted
      val message = probe2.receiveMessage(10 seconds)
      message should be(KitchenActor.PickupOrder("driver", "order"))
    }
  }

}

object OrderDispatcherActorSpec {
  final val testOrder1 = Order(UUID.randomUUID().toString, "test order 1", "frozen", 10, 0.50, None, None)
  final val testOrder2 = Order(UUID.randomUUID().toString, "test order 2", "frozen", 5, 0.50, None, None)
  final val testOrder3 = Order(UUID.randomUUID().toString, "test order 3", "frozen", 5, 0.50, None, None)
  final val queue: Queue[Order] = Queue(testOrder1, testOrder2, testOrder3)

  final val config =
    """
      |akka.typed {
      |  loggers = ["akka.testkit.TestEventListener"]
      |}
      |""".stripMargin
}