package akka.madness.schema

import java.io.{ BufferedReader, FileReader }

import io.circe._
import io.circe.parser._
import io.circe.generic.semiauto._

import scala.util.Using

object Orders {
  def loadFromFile(filename: String): Orders = {
    val lines: String =
      Using.resource(new BufferedReader(new FileReader(filename))) { reader =>
        Iterator.unfold(())(_ => Option(reader.readLine()).map(_ -> ())).toList
      }.mkString
    new Orders(lines)
  }
}

class Orders(json: String) {
  implicit val orderDecoder: Decoder[Order] = deriveDecoder

  val items: Array[Order] = decode[Array[Order]](json) match {
    case Left(error)   => throw new IllegalArgumentException(error)
    case Right(orders) => orders
  }
}
