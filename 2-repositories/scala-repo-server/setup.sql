insert into apikeys (uid, vendor_prefix, permission, createdat) values ('d0ca1d61-f6a8-4b40-a421-dbec5b9cdbad', 'com.benfradet','super',current_timestamp);
insert into apikeys (uid, vendor_prefix, permission, createdat) values ('83e7c051-cd68-4e44-8b36-09182fa158d5', 'com.benfradet','write',current_timestamp);
insert into apikeys (uid, vendor_prefix, permission, createdat) values ('a89c5f27-fe76-4754-8a07-d41884af1074', 'com.snowplowanalytics','write',current_timestamp);
insert into apikeys (uid, vendor_prefix, permission, createdat) values ('6eadba20-9b9f-4648-9c23-770272f8d627', 'com.snowplowanalytics','read',current_timestamp);

insert into schemas values (0, 'com.snowplowanalytics.self-desc','schema','jsonschema','1-0-0','{
  "$schema": "http:\/\/json-schema.org\/draft-04\/schema#",
  "description": "Meta-schema for self-describing JSON schema",
  "self": {
    "vendor": "com.snowplowanalytics.self-desc",
    "name": "schema",
    "format": "jsonschema",
    "version": "1-0-0"
  },
  "allOf": [
    {
      "properties": {
        "self": {
          "type": "object",
          "properties": {
            "vendor": {
              "type": "string",
              "pattern": "^[a-zA-Z0-9-_.]+$"
            },
            "name": {
              "type": "string",
              "pattern": "^[a-zA-Z0-9-_]+$"
            },
            "format": {
              "type": "string",
              "pattern": "^[a-zA-Z0-9-_]+$"
            },
            "version": {
              "type": "string",
              "pattern": "^[0-9]+-[0-9]+-[0-9]+$"
            }
          },
          "required": [
            "vendor",
            "name",
            "format",
            "version"
          ],
          "additionalProperties": false
        }
      },
      "required": [
        "self"
      ]
    },
    {
      "$ref": "http:\/\/json-schema.org\/draft-04\/schema#"
    }
  ]
  }', current_timestamp,current_timestamp,'t');

insert into schemas values(1, 'com.snowplowanalytics.snowplow', 'ad_click', 'jsonschema', '1-0-0', '{
	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
	"description": "Schema for an ad click event",
	"self": {
		"vendor": "com.snowplowanalytics.snowplow",
		"name": "ad_click",
		"format": "jsonschema",
		"version": "1-0-0"
	},

	"type": "object",
	"properties": {
		"clickId": {
			"type": "string"
		},
		"impressionId": {
			"type": "string"
		},
		"zoneId": {
			"type": "string"
		},
		"bannerId": {
			"type": "string"
		},
		"campaignId": {
			"type": "string"
		},
		"advertiserId": {
			"type": "string"
		},
		"targetUrl": {
			"type": "string",
			"minLength": 1
		},
		"costModel": {
			"enum": ["cpa", "cpc", "cpm"]
		},
		"cost": {
			"type": "number",
			"minimum": 0
		}
	},
	"required": ["targetUrl"],
	"dependencies": {"cost": ["costModel"]},
	"additionalProperties": false
}', current_timestamp, current_timestamp, 'f');

insert into schemas values(2, 'com.snowplowanalytics.snowplow', 'ad_click', 'jsonschema', '1-0-1', '{
   "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
   "description": "Schema for an ad click event",                                                       
   "self": {                                                                                            
     "vendor": "com.snowplowanalytics.snowplow",                                                        
     "name": "ad_click",                                                                                
     "format": "jsonschema",                                                                            
     "version": "1-0-1"                                                                                 
   },                                                                                                   
                                                                                                        
   "type": "object",                                                                                    
   "properties": {                                                                                      
     "clickId": {                                                                                       
       "type": "string"                                                                                 
     },                                                                                                 
     "impressionId": {                                                                                  
       "type": "string"                                                                                 
     },                                                                                                 
     "zoneId": {                                                                                        
       "type": "string"                                                                                 
     },                                                                                                 
     "bannerId": {                                                                                      
       "type": "string"                                                                                 
     },                                                                                                 
     "campaignId": {                                                                                    
       "type": "string"                                                                                 
     },                                                                                                 
     "advertiserId": {                                                                                  
       "type": "string"                                                                                 
     },                                                                                                 
     "targetUrl": {                                                                                     
       "type": "string",                                                                                
       "minLength": 1                                                                                   
     },                                                                                                 
     "costModel": {                                                                                     
       "enum": ["cpa", "cpc", "cpm"]                                                                    
     },                                                                                                 
     "cost": {                                                                                          
       "type": "number",                                                                                
       "minimum": 0                                                                                     
     }                                                                                                  
   },                                                                                                   
   "required": ["targetUrl"],                                                                           
   "dependencies": {"cost": ["costModel"]},                                                             
   "additionalProperties": false                                                                        
}', current_timestamp, current_timestamp, 'f');

insert into schemas values(3, 'com.snowplowanalytics.snowplow', 'ad_click2', 'jsonschema', '1-0-1', '{
   "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
   "description": "Schema for an ad click event",                                                       
   "self": {                                                                                            
     "vendor": "com.snowplowanalytics.snowplow",                                                        
     "name": "ad_click2",                                                                                
     "format": "jsonschema",                                                                            
     "version": "1-0-1"                                                                                 
   },                                                                                                   
                                                                                                        
   "type": "object",                                                                                    
   "properties": {                                                                                      
     "clickId": {                                                                                       
       "type": "string"                                                                                 
     },                                                                                                 
     "impressionId": {                                                                                  
       "type": "string"                                                                                 
     },                                                                                                 
     "zoneId": {                                                                                        
       "type": "string"                                                                                 
     },                                                                                                 
     "bannerId": {                                                                                      
       "type": "string"                                                                                 
     },                                                                                                 
     "campaignId": {                                                                                    
       "type": "string"                                                                                 
     },                                                                                                 
     "advertiserId": {                                                                                  
       "type": "string"                                                                                 
     },                                                                                                 
     "targetUrl": {                                                                                     
       "type": "string",                                                                                
       "minLength": 1                                                                                   
     },                                                                                                 
     "costModel": {                                                                                     
       "enum": ["cpa", "cpc", "cpm"]                                                                    
     },                                                                                                 
     "cost": {                                                                                          
       "type": "number",                                                                                
       "minimum": 0                                                                                     
     }                                                                                                  
   },                                                                                                   
   "required": ["targetUrl"],                                                                           
   "dependencies": {"cost": ["costModel"]},                                                             
   "additionalProperties": false                                                                        
}', current_timestamp, current_timestamp, 'f');

insert into schemas values(4, 'com.benfradet.ben', 'ad_click2', 'jsonschema', '1-0-0', '{
   "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
   "description": "Schema for an ad click event",                                                       
   "self": {                                                                                            
     "vendor": "com.benfradet.ben",                                                        
     "name": "ad_click2",                                                                                
     "format": "jsonschema",                                                                            
     "version": "1-0-1"                                                                                 
   },                                                                                                   

	"type": "object",
	"properties": {
		"clickId": {
			"type": "string"
		},
		"impressionId": {
			"type": "string"
		},
		"zoneId": {
			"type": "string"
		},
		"bannerId": {
			"type": "string"
		},
		"campaignId": {
			"type": "string"
		},
		"advertiserId": {
			"type": "string"
		},
		"targetUrl": {
			"type": "string",
			"minLength": 1
		},
		"costModel": {
			"enum": ["cpa", "cpc", "cpm"]
		},
		"cost": {
			"type": "number",
			"minimum": 0
		}
	},
	"required": ["targetUrl"],
	"dependencies": {"cost": ["costModel"]},
	"additionalProperties": false                                                                     
}', current_timestamp, current_timestamp, 't');



insert into schemas values(5, 'com.benfradet.ben', 'ad_click', 'jsonschema', '1-0-0', '{
   "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
   "description": "Schema for an ad click event",                                                       
   "self": {                                                                                            
     "vendor": "com.benfradet.ben",                                                        
     "name": "ad_click",                                                                                
     "format": "jsonschema",                                                                            
     "version": "1-0-0"                                                                                 
   },                                                                                                   

	"type": "object",
	"properties": {
		"clickId": {
			"type": "string"
		},
		"impressionId": {
			"type": "string"
		},
		"zoneId": {
			"type": "string"
		},
		"bannerId": {
			"type": "string"
		},
		"campaignId": {
			"type": "string"
		},
		"advertiserId": {
			"type": "string"
		},
		"targetUrl": {
			"type": "string",
			"minLength": 1
		},
		"costModel": {
			"enum": ["cpa", "cpc", "cpm"]
		},
		"cost": {
			"type": "number",
			"minimum": 0
		}
	},
	"required": ["targetUrl"],
	"dependencies": {"cost": ["costModel"]},
	"additionalProperties": false                                                                     
}', current_timestamp, current_timestamp, 'f');