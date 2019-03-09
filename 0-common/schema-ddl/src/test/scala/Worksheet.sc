import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.int._
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.either._
import cats.syntax.foldable._
import com.snowplowanalytics.iglu.core.SchemaVer
import com.snowplowanalytics.iglu.schemaddl.VersionTree.{Additions, Revisions}

// List's index is an order
// Tuple's i is a SchemaVer root
type Model = Int
type Revision = Int
type Addition = Int

Revisions(NonEmptyList.of((1, Additions(NonEmptyList.of(0)))))
