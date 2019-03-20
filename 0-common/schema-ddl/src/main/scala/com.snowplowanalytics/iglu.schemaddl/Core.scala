package com.snowplowanalytics.iglu.schemaddl

import com.snowplowanalytics.iglu.core.SchemaKey

// All entities should be migrated to core
object Core {
  /** List of schema keys fetched from a repository, proven to be non-empty and have same vendor/name */
  case class SchemaList private(schemas: List[SchemaKey]) extends AnyVal

  sealed trait VersionPoint
  object VersionPoint {
    case object Model extends VersionPoint
    case object Revision extends VersionPoint
    case object Addition extends VersionPoint
  }
}
