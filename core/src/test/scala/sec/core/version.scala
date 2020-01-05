package sec
package core

import cats.implicits._
import cats.kernel.laws.discipline._
import org.scalacheck._
import org.specs2.mutable.Specification
import org.typelevel.discipline.specs2.mutable.Discipline
import sec.Arbitraries._
class VersionSpec extends Specification with Discipline {

  "StreamRevision" >> {

    "Show" >> {

      def test(sr: StreamRevision, expected: String) =
        sr.show shouldEqual expected

      test(StreamRevision.NoStream, "NoStream")
      test(StreamRevision.Any, "Any")
      test(StreamRevision.StreamExists, "StreamExists")
      test(EventNumber.Start, "Exact(0)")
    }

    "Eq" >> {
      implicit val cogen: Cogen[StreamRevision] =
        Cogen[String].contramap[StreamRevision](_.show)

      checkAll("StreamRevision", EqTests[StreamRevision].eqv)
    }
  }

  "EventNumber" >> {

    "apply" >> {
      EventNumber(-1L) shouldEqual EventNumber.End
      EventNumber(0L) shouldEqual EventNumber.exact(0L)
      EventNumber(1L) shouldEqual EventNumber.exact(1L)
    }

    "Exact.apply" >> {
      EventNumber.Exact(-1L) should beLeft("value must be >= 0, but is -1")
      EventNumber.Exact(0L) should beRight(EventNumber.exact(0L))
    }

    "Show" >> {
      (EventNumber.Start: EventNumber).show shouldEqual "EventNumber(0)"
      (EventNumber.End: EventNumber).show shouldEqual "end"
    }

    "Order" >> {
      implicit val cogen: Cogen[EventNumber] =
        Cogen[String].contramap[EventNumber](_.show)

      checkAll("EventNumber", OrderTests[EventNumber].order)
    }
  }

  "Position" >> {

    "apply" >> {
      Position(-1L, -1L) should beRight[Position](Position.End)
      Position(-1L, 0L) should beRight[Position](Position.End)
      Position(0L, -1L) should beRight[Position](Position.End)
      Position(0L, 0L) should beRight(Position.exact(0L, 0L))
      Position(1L, 0L) should beRight(Position.exact(1L, 0L))
      Position(1L, 1L) should beRight(Position.exact(1L, 1L))
      Position(0L, 1L) should beLeft("commit must be >= prepare, but 0 < 1")
    }

    "Exact.apply" >> {
      Position.Exact(-1L, 0L) should beLeft("commit must be >= 0, but is -1")
      Position.Exact(0L, -1L) should beLeft("prepare must be >= 0, but is -1")
      Position.Exact(0L, 1L) should beLeft("commit must be >= prepare, but 0 < 1")
      Position.Exact(0L, 0L) should beRight(Position.exact(0L, 0L))
      Position.Exact(1L, 0L) should beRight(Position.exact(1L, 0L))
    }

    "Show" >> {
      (Position.Start: Position).show shouldEqual "Position(c = 0, p = 0)"
      (Position.End: Position).show shouldEqual "end"
    }

    "Order" >> {
      implicit val cogen: Cogen[Position] =
        Cogen[String].contramap[Position](_.show)

      checkAll("Position", OrderTests[Position].order)
    }
  }

}