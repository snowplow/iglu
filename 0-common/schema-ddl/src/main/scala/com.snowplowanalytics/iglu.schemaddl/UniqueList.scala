package com.snowplowanalytics.iglu.schemaddl

import cats.Monoid

case class UniqueList[A] private(value: List[A]) extends AnyVal {
  def add(a: A): UniqueList[A] =
    if (value.contains(a)) this else UniqueList(a :: value)
  def merge(other: UniqueList[A]): UniqueList[A] =
    other.value.foldLeft(this) { (acc, elem) => acc.add(elem) }
}

object UniqueList { o =>
  def empty[A] = UniqueList(List.empty[A])

  def build[A](as: List[A]): UniqueList[A] =
    UniqueList(as.distinct)

  implicit def igluUniqueSetMonoid[A]: Monoid[UniqueList[A]] =
    new Monoid[UniqueList[A]] {
      def empty: UniqueList[A] = o.empty[A]
      def combine(x: UniqueList[A], y: UniqueList[A]): UniqueList[A] =
        x.merge(y)
    }
}

