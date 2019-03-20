package com.snowplowanalytics.iglu.schemaddl.jsonschema

sealed trait Keyword {
  def name: Symbol

  /** Can contain another schema */
  def recursive: Boolean
}


object Keyword {
  // increased/decresed

  // added/removed (type, enum)

  // copy of a Schema
  // with prop: (was, became)

  case object MultipleOf extends Keyword {
    val name = 'multipleOf
    val recursive = false
  }
  case object Minimum extends Keyword {
    val name = 'minimum
    val recursive = false
  }
  case object Maximum extends Keyword {
    val name = 'maximum
    val recursive = false
  }

  case object MaxLength extends Keyword {
    val name = 'maxLength
    val recursive = false
  }
  case object MinLength extends Keyword {
    val name = 'minLength
    val recursive = false
  }
  case object Pattern extends Keyword {
    val name = 'pattern
    val recursive = false
  }
  case object Format extends Keyword {
    val name = 'format
    val recursive = false
  }

  case object Items extends Keyword {
    val name = 'items
    val recursive = true
  }
  case object AdditionalItems extends Keyword {
    val name = 'additionalItems
    val recursive = true
  }
  case object MinItems extends Keyword {
    val name = 'minItems
    val recursive = false
  }
  case object MaxItems extends Keyword {
    val name = 'maxItems
    val recursive = false
  }

  case object Properties extends Keyword {
    val name = 'properties
    val recursive = true
  }
  case object AdditionalProperties extends Keyword {
    val name = 'additionalProperties
    val recursive = true
  }
  case object Required extends Keyword {
    val name = 'required
    val recursive = false
  }
  case object PatternProperties extends Keyword {
    val name = 'patternProperties
    val recursive = true
  }

  case object Type extends Keyword {
    val name = 'type
    val recursive = false
  }
  case object Enum extends Keyword {
    val name = 'enum
    val recursive = false
  }
  case object OneOf extends Keyword {
    val name = 'oneOf
    val recursive = true
  }
  case object Description extends Keyword {
    val name = 'description
    val recursive = false
  }
}
