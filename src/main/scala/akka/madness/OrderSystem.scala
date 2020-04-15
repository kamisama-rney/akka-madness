package akka.madness

import akka.actor.typed.ActorSystem
import akka.madness.actors.OrderDispatcherActor.{Start, SystemCommand}
import akka.madness.schema.Orders
import akka.madness.actors.OrderDispatcherActor
import akka.madness.actors.OrderDispatcherActor.{AddKitchen, Start, SystemCommand}
import akka.madness.schema.Orders
import com.typesafe.config.Config

import scala.collection.immutable.Queue

/**
 * Driving main class
 */
object OrderSystem {

  def main(args: Array[String]): Unit = {

    var ordersFile = "./orders.json"
    args.sliding(2, 2).toList.collect {
      case Array("--orders", orders: String) => ordersFile = orders
    }

    // Load the orders file
    val orders = Orders.loadFromFile(ordersFile)

    // Create ActorSystem and top level supervisor
    val orderSystem = ActorSystem[SystemCommand](OrderDispatcherActor(Queue.from(orders.items)), "order-dispatcher")
    orderSystem ! AddKitchen("ExcellentFoods")
    orderSystem ! Start
  }
}
