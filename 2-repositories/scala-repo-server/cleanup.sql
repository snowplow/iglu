delete from schemas where vendor = 'com.unittest' or vendor = 'com.benfradet';
delete from apikeys where vendor_prefix = 'com.unittest' or vendor_prefix = 'com.no.idea';
delete from schemas where name like 'unit_%';

