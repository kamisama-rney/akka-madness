package akka.madness.schema

/**
 * Data class that represents a food order within the system
 *
 * @param id - A unique identifier (UUID) for the order
 * @param name - The name of the menu element
 * @param temp - The preferred storage temperature (frozen, cold, hot)
 * @param shelfLife - The shelf life of this item in seconds
 * @param decayRate - A decay rate modifier used when calculating remaining value
 */
final case class Order(id: String, name: String, temp: String, shelfLife: Int, decayRate: Double,
    age: Option[Int], kitchen: Option[String]) {

  /**
   * A method used to calculate the remaining value given an age and an aging modifier (1 or 2)
   * @param shelfDecayModifier - A modifier to the aging equation
   * @return - A double representing the remaining value of this item
   */
  def value(shelfDecayModifier: Int = 1): Double = {
    (shelfLife - decayRate * age.getOrElse(0) * shelfDecayModifier) / shelfLife
  }

  // Age the order by the given amount and return a copy. Since `age` is of Option type check for default
  // value first
  def ageOrder(time: Int): Order = this.copy(age = Some(this.age.getOrElse(0) + time))
}

object Order extends Ordering[Order] {
  def compare(a: Order, b: Order): Int = a.value() compare b.value()
}