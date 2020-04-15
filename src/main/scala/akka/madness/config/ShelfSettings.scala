package akka.madness.config

import com.typesafe.config.Config
import scala.jdk.CollectionConverters._

final case class ShelfSettings(name: String, size: Int, ageRate: Int, overflow: Boolean, overflowShelf: String = "")

object ShelfSettings {
  def fromConfig(settings: Config): Map[String, ShelfSettings] = {

    settings.getConfigList(ConfigurationKeys.ShelveBase).asScala.map(shelf => {

      // This shelf isn't the overflow if a path is set
      val overflow = !shelf.hasPath("overflowShelf")

      // Construct a settings object from the configuration object
      val name = getStringOption(shelf, "name").getOrElse("unknown")
      name -> new ShelfSettings(
        name,
        getIntOption(shelf, "size").getOrElse(10),
        getIntOption(shelf, "ageRate").getOrElse(1),
        overflow,
        getStringOption(shelf, "overflowShelf").getOrElse("none")
      )
    }).toMap
  }
}

