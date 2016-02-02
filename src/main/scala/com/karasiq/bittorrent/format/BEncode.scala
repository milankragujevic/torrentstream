package com.karasiq.bittorrent.format

import akka.util.ByteString
import org.parboiled2._

sealed trait BEncodedValue
case class BEncodedString(string: String) extends BEncodedValue
case class BEncodedNumber(number: Long) extends BEncodedValue
case class BEncodedArray(values: Seq[BEncodedValue]) extends BEncodedValue
case class BEncodedDictionary(values: Map[String, BEncodedValue]) extends BEncodedValue

class BEncode(val input: ParserInput) extends Parser {
  def Number: Rule1[Long] = rule { capture(optional('-') ~ oneOrMore(CharPredicate.Digit)) ~> ((s: String) ⇒ s.toLong) }

  def NumericValue: Rule1[BEncodedNumber] = rule { 'i' ~ Number ~ 'e' ~> BEncodedNumber }

  def StringValue: Rule1[BEncodedString] = rule { Number ~ ':' ~> (length ⇒ test(length >= 0) ~ capture(length.toInt.times(CharPredicate.All))) ~> BEncodedString }

  def ArrayValue: Rule1[BEncodedArray] = rule { 'l' ~ oneOrMore(Value) ~ 'e' ~> BEncodedArray }

  def DictionaryValue: Rule1[BEncodedDictionary] = rule { 'd' ~ oneOrMore(StringValue ~ Value ~> { (s, v) ⇒ s.string → v }) ~ 'e' ~> ((values: Seq[(String, BEncodedValue)]) ⇒ BEncodedDictionary(values.toMap)) }

  def Value: Rule1[BEncodedValue] = rule { NumericValue | StringValue | ArrayValue | DictionaryValue }

  def EncodedFile: Rule1[Seq[BEncodedValue]] = rule { oneOrMore(Value) }
}

object BEncode {
  def parse(bytes: ParserInput): Seq[BEncodedValue] = {
    new BEncode(bytes).EncodedFile.run().getOrElse(Nil)
  }
}

object BEncodeImplicits {
  implicit class BEncodedValueOps(val value: BEncodedValue) extends AnyVal {
    def asDict: Map[String, BEncodedValue] = value match {
      case BEncodedDictionary(values) ⇒
        values

      case _ ⇒
        Map.empty
    }

    def asArray: Seq[BEncodedValue] = value match {
      case BEncodedArray(values) ⇒
        values

      case _ ⇒
        Nil
    }

    def asString: String = value match {
      case BEncodedString(str) ⇒
        str

      case _ ⇒
        throw new IllegalArgumentException
    }

    def asNumber: Long = value match {
      case BEncodedNumber(num) ⇒
        num

      case _ ⇒
        throw new IllegalArgumentException
    }

    def asByteString: ByteString = ByteString(asString.getBytes("ASCII"))
  }

  implicit class BEncodedDictOps(val dict: Map[String, BEncodedValue]) extends AnyVal {
    def string(key: String): Option[String] = dict.get(key).collect {
      case BEncodedString(str) ⇒
        str
    }

    def number(key: String): Option[Long] = dict.get(key).collect {
      case BEncodedNumber(num) ⇒
        num
    }

    def array(key: String): Seq[BEncodedValue] = dict.get(key).map(_.asArray).getOrElse(Nil)

    def dict(key: String): Map[String, BEncodedValue] = dict.get(key).map(_.asDict).getOrElse(Map.empty)
  }
}






























