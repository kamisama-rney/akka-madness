package akka.madness.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.madness.config.ShelfSettings
import akka.madness.schema.Order

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Companion object used for construction of the ShelfActor class. This is accomplished
 * through the `apply` method
 */
object ShelfActor {

  /**
   * A construction method used to create the ShelfActor
   * @return - An reference to the actor instance
   */
  def apply(kitchen: ActorRef[KitchenActor.MoveOrder], settings: ShelfSettings,
    testing: Boolean = false): Behavior[ShelfCommand] =
    Behaviors.setup { context =>
      Behaviors.withTimers(timers =>
        new ShelfActor(kitchen, settings, testing, context, timers))
    }

  // An object key used for the shelf aging timer
  private case object TimerKey

  // Message classes used to send commands to this actor
  sealed trait ShelfCommand
  final case object AgeOrders extends ShelfCommand
  final case object Stop extends ShelfCommand
  final case class ReturnShelfContents(replyTo: ActorRef[ShelfContents]) extends ShelfCommand
  final case class MoveOrder(order: Order, tossIfNoRoom: Boolean = false) extends ShelfCommand
  final case class PickupOrder(driver: String, id: String) extends ShelfCommand
  final case class ShelfContents(orders: List[Order])
}

/**
 * This actor represents a shelf in a restaurant. It maintains a list of all ready orders
 * and will age them every second and will remove any dead orders. If this shelf isn't marked
 * as the overflow, if the shelf is full it will automatically push it to the overflow actor
 * through the kitchen parent actor
 * @param timers - The interface used to schedule times
 * @param settings - The variable settings for the shelf (size and ageRate)
 */
class ShelfActor(kitchen: ActorRef[KitchenActor.MoveOrder], settings: ShelfSettings,
    testing: Boolean = false, context: ActorContext[ShelfActor.ShelfCommand],
    timers: TimerScheduler[ShelfActor.ShelfCommand])
  extends AbstractBehavior[ShelfActor.ShelfCommand](context) {
  import ShelfActor._

  var shelfItems: List[Order] = List.empty[Order]
  val name: String = context.self.path.name

  // Setup shelve aging timer so the actor gets messages every second to age any food on the shelf
  // and to clear any orders than aren't fit to deliver
  if (!testing)
    timers.startTimerAtFixedRate(TimerKey, ShelfActor.AgeOrders, 1 second)

  /**
   * Primary message handler for this actor
   * @param msg - A message directed to this actor with a command to carry out
   * @return - The same actor behavior as the current behavior
   */
  override def onMessage(msg: ShelfCommand): Behavior[ShelfCommand] = {
    msg match {
      case MoveOrder(order, tossIfNoRoom) => handleMoveOrder(order, tossIfNoRoom)
      case PickupOrder(driver, id)        => handlePickupOrder(driver, id)
      case AgeOrders                      => handleAgeOrders()
      case ReturnShelfContents(replyTo) =>
        replyTo ! ShelfContents(shelfItems)
        this
      case Stop => Behaviors.stopped
    }
  }

  /**
   * Method called by a timer that ages all the dished on the shelf
   * @return - The same actor behavior as the current behavior
   */
  private def handleAgeOrders(): Behavior[ShelfCommand] = {
    val originalItemCount = shelfItems.length

    // Age the items and expire all that are dead
    shelfItems = shelfItems.foldLeft(List.empty[Order])((newList, item) => {
      // Age the item by the shelve's age rate
      val agedItem: Order = item.ageOrder(settings.ageRate)

      // Check if the item is still good
      if (agedItem.value() > 0) {
        // retain good item
        newList :+ agedItem
      } else {
        // Report the discarded item
        context.log.info(s"$agedItem is no longer saleable, discarding")

        // toss wasted food
        newList
      }
    })

    if (shelfItems.length < originalItemCount)
      context.log.info(s"Items remaining on the $name shelf:\n ${shelfItems.mkString("\n")}")
    this
  }

  /**
   * Method called when the driver comes to pickup the order from a shelf
   * @param driver - Identifier of the driver picking up the order
   * @param id - Identifier of the order getting picked up
   * @return - The same actor behavior as the current behavior
   */
  private def handlePickupOrder(driver: String, id: String): Behavior[ShelfCommand] = {
    // Report the order picked up
    val pickedUpOrder = shelfItems.find(item => item.id == id)
    pickedUpOrder match {
      case Some(order) => context.log.info(s"$driver picked up order: $order")
      case None        =>
    }

    // Remove picked up item from shelf
    shelfItems = shelfItems.filterNot(item => item.id == id)

    // Report the remaining items on shelf
    context.log.info(s"Items remaining on the $name shelf:\n${shelfItems.mkString("\n")}")
    this
  }

  /**
   * Method used to put an order on the shelf. If there is no room on the shelf depending on
   * @param order - An order to place on the shelf
   * @param tossIfNoRoom - If there is no room on the shelf, toss the order
   * @return - The same actor behavior as the current behavior
   */
  private def handleMoveOrder(order: Order, tossIfNoRoom: Boolean): Behavior[ShelfCommand] = {
    // See if there is room on the shelf
    if (shelfItems.length >= settings.size) {
      if (!tossIfNoRoom) {
        if (settings.overflow) {
          // Find item that's about to expire
          val expiringItem = shelfItems.min(Order)

          // Remove picked up item from shelf
          shelfItems = shelfItems.filterNot(item => item.id == expiringItem.id)

          // Save the order
          shelfItems = shelfItems :+ order

          // Attempt to move it to its original shelf to reduce aging speed
          kitchen ! KitchenActor.MoveOrder(expiringItem.temp, expiringItem, tossIfNoRoom = true)
        } else {
          // Push it to the overflow
          kitchen ! KitchenActor.MoveOrder(settings.overflowShelf, order)
          context.log.info(s"No room on the $name shelf, passing to `${settings.overflowShelf}` shelf to see if there is room")
        }
      }
    } else {
      // Put order on the shelf
      shelfItems = shelfItems :+ order

      // Report new item on shelf
      context.log.info(s"Added: $order onto the $name shelf, contents now:\n${shelfItems.mkString("\n")}")
    }
    this
  }
}