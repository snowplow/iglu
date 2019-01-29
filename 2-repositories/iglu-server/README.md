# Quckstart

Assuming Docker is installed:

```
$ docker run --name igludb -e POSTGRES_PASSWORD=iglusecret -p 5432:5432 -d postgres
$ docker exec -i -t $CONTAINER_ID psql -U postgres -c "CREATE DATABASE igludb"
$ sbt run setup --config application.conf
$ docker exec -i -t $CONTAINER_ID psql -U postgres \                                                                                               17:55:28
    -c "INSERT INTO permissions VALUES ('8f02f01f-3bc1-414b-9277-46d723fb46ad', '', TRUE, 'CREATE_VENDOR'::schema_action, '{"CREATE", "DELETE"}'::key_action[])" \
    igludb
$ sbt run run --config application.conf
```

Navigate to http://localhost:8080/static/swagger-ui/index.html