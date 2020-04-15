package akka.madness.config

object ConfigurationKeys {

  // Base key for all configuration elements
  final val ConfigBase = "madness"

  // Configuration Keys for OrderDispatcher Actor
  final val OrderBatchSize = s"$ConfigBase.orderBatchSize"
  final val TimeBetweenBatches = s"$ConfigBase.timeBetweenBatches"

  // Configuration Keys for Kitchen
  final val KitchenBase = s"$ConfigBase.kitchen"
  final val KitchenName = s"$KitchenBase.name"

  // Configuration Keys for Shelves
  final val ShelveBase = s"$ConfigBase.kitchen.shelves"
  final val FrozenShelve = s"$ShelveBase.frozen"
  final val ColdShelve = s"$ShelveBase.cold"
  final val HotShelve = s"$ShelveBase.hot"
  final val OverflowShelve = s"$ShelveBase.overflow"

  // Configuration Keys for Drivers
  final val DriversBase = s"$ConfigBase.drivers"
  final val MinWait = s"$DriversBase.minWait"
  final val MaxWait = s"$DriversBase.maxWait"
}
