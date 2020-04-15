package akka.madness.actors

import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.Behaviors.ignore
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.madness.actors.KitchenActor.KitchenCommand
import akka.madness.config.ConfigurationKeys
import akka.madness.schema.Order
import akka.madness.{config => ConfigHelpers}
import com.typesafe.config.Config

import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Companion object used for construction of the OrderDispatcherActor class. This is accomplished
 * through the `apply` method
 */
object OrderDispatcherActor {
  // Type definition for injection method to create child actors
  type builderMethod = (String, ActorContext[SystemCommand]) => ActorRef[KitchenActor.KitchenCommand]

  /**
   * A creation method used to create the order dispatcher
   * @param orderQueue - A queue of order to publish into the system. In future could
   *                   consume from Kafka or other messaging system
   * @param kitchenBuilder - factory pattern method used to create child actors
   * @return
   */
  def apply(orderQueue: Queue[Order], kitchenBuilder: builderMethod = defaultKitchenBuilder): Behavior[SystemCommand] =
    Behaviors.setup[SystemCommand](context =>
      Behaviors.withTimers[OrderDispatcherActor.SystemCommand](timers =>
        new OrderDispatcherActor(orderQueue, kitchenBuilder, context, timers)))

  // Message classes used to send commands to this actor
  sealed trait SystemCommand
  case object Start extends SystemCommand
  case object Stop extends SystemCommand
  case object OrderBatch extends SystemCommand
  case class AddKitchen(name: String) extends SystemCommand
  case class PickupOrder(driver: String, kitchen: String, id: String) extends SystemCommand

  final val defaultKitchen: String = "ExcellentFoods"

  def defaultKitchenBuilder(name: String, context: ActorContext[SystemCommand]): ActorRef[KitchenCommand] =
    context.spawn(KitchenActor(), name)
}

/**
 * The main actor that drives all orders coming into this location
 * @param orderQueue - A queue of the orders loaded from the test file
 * @param context - The context of this actor instance
 * @param timers - A reference to the timers behavior class
 */
class OrderDispatcherActor(var orderQueue: Queue[Order], kitchenBuilder: OrderDispatcherActor.builderMethod,
    context: ActorContext[OrderDispatcherActor.SystemCommand],
    timers: TimerScheduler[OrderDispatcherActor.SystemCommand])
  extends AbstractBehavior[OrderDispatcherActor.SystemCommand](context) {

  import OrderDispatcherActor._
  context.log.info("Cloud Kitchen Application started")

  // Load configuration settings for dispatcher
  val config: Config = context.system.settings.config
  val batchSize: Int = ConfigHelpers.getIntOption(config, ConfigurationKeys.OrderBatchSize).getOrElse(1)
  val timeBetweenBatches: Duration = ConfigHelpers.getDurationOption(config, ConfigurationKeys.TimeBetweenBatches)
    .getOrElse(Duration.ofSeconds(1))

  // Load configuration setting for driver arrivals
  val minDriverWait: Duration = ConfigHelpers.getDurationOption(config, ConfigurationKeys.MinWait)
    .getOrElse(Duration.ofSeconds(2))
  val maxDriverWait: Duration = ConfigHelpers.getDurationOption(config, ConfigurationKeys.MaxWait)
    .getOrElse(Duration.ofSeconds(6))

  // Create an actor for the kitchen
  val kitchens: mutable.Map[String, ActorRef[KitchenActor.KitchenCommand]] =
    mutable.Map.empty[String, ActorRef[KitchenActor.KitchenCommand]]

  /**
   *
   * @param msg - Command message sent to this main application actor
   * @return - The next behavior which is the same as current
   */
  override def onMessage(msg: OrderDispatcherActor.SystemCommand): Behavior[OrderDispatcherActor.SystemCommand] = {
    msg match {
      case OrderDispatcherActor.OrderBatch                       => handleOrderBatch()
      case OrderDispatcherActor.PickupOrder(driver, kitchen, id) => handlePickupOrder(driver, kitchen, id)
      case OrderDispatcherActor.Start                            => handleStart()
      case OrderDispatcherActor.AddKitchen(name)                 => handleAddKitchen(name)
      case OrderDispatcherActor.Stop =>
        kitchens.values.foreach(_ ! KitchenActor.Stop)
        Behaviors.stopped
      case _ => ignore
    }
  }

  /**
   * Method that handled the addition of a kitchen to the dispatcher actor
   * @param name - The identifier of the kitchen
   * @return - The next behavior of the actor, same as current behavior
   */
  private def handleAddKitchen(name: String): Behavior[OrderDispatcherActor.SystemCommand] = {
    kitchens += (name -> kitchenBuilder(name, context))
    this
  }

  /**
   *
   * @param driver - The identifier of the driver picking up the order
   * @param kitchen - The location the driver is picking up the dish from
   * @param id - The identifier of the order getting picked up
   * @return - The next behavior of the actor, same as current behavior
   */
  private def handlePickupOrder(driver: String, kitchen: String, id: String): Behavior[OrderDispatcherActor.SystemCommand] = {
    kitchens.get(kitchen) match {
      case Some(restaurant) =>
        restaurant ! KitchenActor.PickupOrder(driver, id)
      case None => context.log.error(s"Driver is unable to find kitchen: $kitchen")
    }
    this
  }

  /**
   * Set up a timer that will feed a batch of orders to the kitchen.
   * @return - The next behavior of the actor, same as current behavior
   */
  private def handleStart(): Behavior[OrderDispatcherActor.SystemCommand] = {
    // Start feeding the system orders
    context.self ! OrderBatch
    this
  }

  /**
   * Handle the next batch of orders send. Loaded from file as queue, could come from REST or Kafka in
   * production environment
   *
   * @return - The next behavior of the actor, same as current behavior
   */
  private def handleOrderBatch(): Behavior[OrderDispatcherActor.SystemCommand] = {
    // Process a batch of orders
    for (_ <- 1 to batchSize) {

      // Make sure there are elements in the queue
      if (orderQueue.nonEmpty) {
        // Pop the queue
        val (order: Order, queue) = orderQueue.dequeue

        try {
          // Create order and associate with a new driver which is implemented as a timer
          val driverId = UUID.randomUUID().toString
          val driverPickup = util.Random.between(minDriverWait.getSeconds, maxDriverWait.getSeconds)
          val driverArrivalTime = FiniteDuration(driverPickup, TimeUnit.SECONDS)
          val kitchen = order.kitchen.getOrElse(OrderDispatcherActor.defaultKitchen)
          timers.startSingleTimer(driverId, PickupOrder(driverId, kitchen, order.id), driverArrivalTime)
        } catch {
          case e: Throwable => context.log.warn(s"Exception thrown while scheduling driver: $e")
        }
        // Send order to restaurant
        kitchens.get(order.kitchen.getOrElse(OrderDispatcherActor.defaultKitchen)) match {
          case Some(kitchen) => kitchen ! KitchenActor.NewOrder(order)
          case None          => context.log.error(s"Unable to find kitchen: ${order.kitchen}")
        }
        // Update the common queue var
        orderQueue = queue
      }
    }

    // Schedule next batch if there is more orders
    if (orderQueue.nonEmpty)
      timers.startSingleTimer("order scheduler", OrderBatch,
        new FiniteDuration(timeBetweenBatches.getSeconds, TimeUnit.SECONDS))
    else {
      timers.startSingleTimer(Stop, new FiniteDuration(maxDriverWait.getSeconds * 2, TimeUnit.SECONDS))
    }
    this
  }

  /**
   * Method signaled to end the application
   * @return - The next behavior of the actor, same as current behavior
   */
  override def onSignal: PartialFunction[Signal, Behavior[OrderDispatcherActor.SystemCommand]] = {
    case PostStop =>
      context.log.info("Cloud Kitchen's Order Tracker Application stopped")
      this
  }
}
