package com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s

object Formats {
  /**
    * json4s formats for all JSON Schema properties
    */
  implicit val allFormats: org.json4s.Formats =
    org.json4s.DefaultFormats +
      StringSerializers.FormatSerializer +
      StringSerializers.MinLengthSerializer +
      StringSerializers.MaxLengthSerializer +
      StringSerializers.PatternSerializer +
      ObjectSerializers.PropertiesSerializer +
      ObjectSerializers.AdditionalPropertiesSerializer +
      ObjectSerializers.RequiredSerializer +
      ObjectSerializers.PatternPropertiesSerializer +
      CommonSerializers.TypeSerializer +
      CommonSerializers.EnumSerializer +
      CommonSerializers.OneOfSerializer +
      CommonSerializers.DescriptionSerializer +
      NumberSerializers.MaximumSerializer +
      NumberSerializers.MinimumSerializer +
      NumberSerializers.MultipleOfSerializer +
      ArraySerializers.AdditionalPropertiesSerializer +
      ArraySerializers.MaxItemsSerializer +
      ArraySerializers.MinItemsSerializer +
      ArraySerializers.ItemsSerializer
}
