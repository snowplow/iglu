import shapeless.{::, HNil, HList}

case class Action[T <: HList](t: T)
case class Entity[T <: HList](t: T)
case class Route[T <: HList](router: Entity[T], action: Action[T])

sealed trait RRoute {
  type Whole <: HList

  def :|:[T <: HList](head: Route[T]) =
    RRoute.:|:(head, this)
}

object RRoute {
  case class :|:[T <: HList](head: Route[T], tai: RRoute) extends RRoute {
    type Whole = T :: tai.Whole
  }

  case class End() extends RRoute {
    type Whole = HNil
  }
}


val a = Route(Entity(4 :: "bar" :: HNil), Action(3 :: "foo" :: HNil))
val b = Route(Entity(true :: 4 :: true :: HNil), Action(true :: 3 :: true :: HNil))


val c = a :|: b :|: RRoute.End()

println(c)




case class Request(url: String)
case class Response(body: String)

type HttpService = Request => Response

trait Routes {
  type HRoutes <: HList

  val routes: HRoutes

  def fold: HttpService
}

