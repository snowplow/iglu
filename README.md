# Iglu Schema Repository

Iglu is a schema repository for [JSON Schema] [json-schema]. A schema repository (sometimes called a registry) is like npm or Maven or git but holds data schemas instead of code.

### Elevator pitch

A RESTful schema registry based on vschema:

```
GET endpoint/schema/<vendor>/<object>/<x>
GET endpoint/schema/<vendor>/<object>/<x:y>
GET endpoint/schema/<vendor>/<object>/<x:y:z>
```

### Limitations

* Currently has no authentication. You'll have to whitelist access.
* Currently assumes you are happy to store schemas in a pre-existing DynamoDB table.
* Currently only supports JSON Schema. Avro, Thrift etc planned (maybe not RESTful-based, TBC).
* Currently only supports reads (GETs). You'll have to store schemas manually.

### Prior art

* https://github.com/linkedin/camus/tree/master/camus-schema-registry

[json-schema]: http://json-schema.org/
