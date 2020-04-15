package akka.madness.actors

import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.madness.actors.KitchenActor.{ KitchenCommand, builderMethod }
import akka.madness.config.ShelfSettings
import akka.madness.schema.Order
import com.typesafe.config.Config

/**
 * Companion object used for construction of the KitchenActor class. This is accomplished
 * through the `apply` method
 */
object KitchenActor {

  // Type definition for injection method to create child actors
  type builderMethod = (String, ShelfSettings, Boolean, ActorContext[KitchenCommand]) => ActorRef[ShelfActor.ShelfCommand]

  /**
   * A construction method used to create the KitchenActor
   * @return - An reference to the actor instance
   */
  def apply(builder: builderMethod = shelfBuilder): Behavior[KitchenCommand] =
    Behaviors.setup(context => new KitchenActor(builder, context))

  // Message classes used to send commands to this actor
  sealed trait KitchenCommand
  final case class NewOrder(order: Order) extends KitchenCommand
  final case class MoveOrder(shelf: String, order: Order, tossIfNoRoom: Boolean = false) extends KitchenCommand
  final case class PickupOrder(driver: String, id: String) extends KitchenCommand
  final object Stop extends KitchenCommand

  def shelfBuilder(name: String, settings: ShelfSettings, overflow: Boolean, context: ActorContext[KitchenCommand]): ActorRef[ShelfActor.ShelfCommand] = context.spawn(ShelfActor(context.self, settings), name)

}

/**
 * Main actor class that represents a kitchen
 * @param context - The context of the ActorSystem this actor is running under
 */
class KitchenActor(childBuilder: builderMethod, context: ActorContext[KitchenCommand])
  extends AbstractBehavior[KitchenCommand](context) {
  import KitchenActor._

  val config: Config = context.system.settings.config

  // Read configuration settings for shelves and create the shelves
  val shelves: Map[String, ActorRef[ShelfActor.ShelfCommand]] = ShelfSettings.fromConfig(config)
    .map { shelf =>
      val (name: String, settings: ShelfSettings) = shelf
      name -> childBuilder(name, settings, settings.overflow, context)
    }

  /**
   * The actor's message handler. All messages are dispatched to the actor through this method
   * @param msg - A message sent to this actor
   * @return
   */
  override def onMessage(msg: KitchenCommand): Behavior[KitchenCommand] =
    msg match {
      case NewOrder(order)                       => handleNewOrder(order)
      case MoveOrder(shelf, order, tossIfNoRoom) => handleMoveToShelf(shelf, order, tossIfNoRoom)
      case PickupOrder(driver, id)               => handleOrderPickup(driver, id)
      case Stop                                  => Behaviors.stopped
    }

  /**
   * The method called when a driver comes into the kitchen to pickup an order
   * @param driver - The identifier of the driver picking up the order
   * @param id - The identifier of the order getting picked up
   * @return - The next behaviour desired of this actor which is "no change"
   */
  private def handleOrderPickup(driver: String, id: String): Behavior[KitchenCommand] = {
    // Search shelves for order
    shelves.values.foreach(shelf => ActorRef.ActorRefOps(shelf) ! ShelfActor.PickupOrder(driver, id))
    this
  }

  /**
   * Method called to move an order between shelves
   * @param order - Data structure representing the order getting moved to the overflow shelf
   * @return - The next behaviour desired of this actor which is "no change"
   */
  private def handleMoveToShelf(shelf: String, order: Order, tossIfNoRoom: Boolean): Behavior[KitchenCommand] = {
    shelves.get(shelf) match {
      case Some(shelfActor) =>
        // Send the order over to overflow
        shelfActor ! ShelfActor.MoveOrder(order)
      case None =>
        // Overflow shelf is missing?
        context.log.error(s"Unable to find the `$shelf` shelf for order: $order")
    }
    this
  }

  /**
   * Method called to create a new order and attempt to place it on a holding shelf
   * @param order- Data structure representing the new order received
   * @return - The next behaviour desired of this actor which is "no change"
   */
  private def handleNewOrder(order: Order): Behavior[KitchenCommand] = {
    shelves.get(order.temp) match {
      case Some(shelf) =>
        // Send the order to the shelf
        shelf ! ShelfActor.MoveOrder(order)
      case None =>
        // No shelf found to support temp, log order information (dlq), and continue
        context.log.error(s"Unable to find a shelf for: $order")
    }
    this
  }
}
