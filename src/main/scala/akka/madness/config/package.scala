package akka.madness

import com.typesafe.config.Config
import java.time.Duration

package object config {
  def getIntOption(config: Config, key: String): Option[Int] = {
    if (config.hasPath(key))
      Some(config.getInt(key))
    else
      None
  }

  def getBooleanOption(config: Config, key: String): Option[Boolean] = {
    if (config.hasPath(key))
      Some(config.getBoolean(key))
    else
      None
  }

  def getDurationOption(config: Config, key: String): Option[Duration] = {
    if (config.hasPath(key))
      Some(config.getDuration(key))
    else
      None
  }

  def getStringOption(config: Config, key: String): Option[String] = {
    if (config.hasPath(key))
      Some(config.getString(key))
    else
      None
  }
}
