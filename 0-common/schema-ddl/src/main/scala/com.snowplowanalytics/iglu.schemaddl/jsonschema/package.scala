package com.snowplowanalytics.iglu.schemaddl

package object jsonschema {
  implicit class TypeMatcher(val jsonType: CommonProperties.Type) extends AnyVal {
    def precisely(matchingType: CommonProperties.Type): Boolean =
      jsonType == matchingType
    def nullable: Boolean =
      jsonType match {
        case CommonProperties.Type.Product(union) => union.toSet.contains(CommonProperties.Type.Null)
        case CommonProperties.Type.Null => true
        case _ => false
      }
    def nullable(matchingType: CommonProperties.Type): Boolean =
      jsonType match {
        case CommonProperties.Type.Product(union) => union.toSet == Set(CommonProperties.Type.Null, matchingType)
        case _ => false
      }
    def possiblyWithNull(matchingType: CommonProperties.Type): Boolean =
      precisely(matchingType) || nullable(matchingType)
  }
}
