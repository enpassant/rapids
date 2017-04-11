# rapids
 Reducing Microservice Complexity with Kafka and Reactive Streams

## Inspirations

1. [Reducing Microservice Complexity with Kafka and Reactive Streams - by Jim Riecken](https://www.youtube.com/watch?v=k_Y5ieFHGbs)
2. [Øredev 2013 - Fred George - Implementing Micro Service Architecture](https://vimeo.com/79866979)
3. [Benchmarking Apache Kafka: 2 Million Writes Per Second (On Three Cheap Machines)](https://engineering.linkedin.com/kafka/benchmarking-apache-kafka-2-million-writes-second-three-cheap-machines)
4. [Understanding Akka Streams, Back Pressure and Asynchronous Architectures](https://www.lightbend.com/blog/understanding-akka-streams-back-pressure-and-asynchronous-architectures)

## Run in Docker Swarm

```bash
docker swarm init

docker service create --name portainer -p 9090:9000 --constraint 'node.role == manager' --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock portainer/portainer -H unix:///var/run/docker.sock

docker network create -d overlay proxy

docker service create --name haproxy --network proxy --mount target=/var/run/docker.sock,source=/var/run/docker.sock,type=bind -p 81:80 --constraint "node.role == manager" dockercloud/haproxy

docker service create --network proxy -p 2181:2181 -p 9092:9092 --name kafka spotify/kafka

docker service create --name mongodb --network proxy mongo:latest

docker service create --name app1 -e SERVICE_PORTS="8080" -e VIRTUAL_HOST="*" -p 82:8080 --network proxy enpassant/rapids:1.0-SNAPSHOT -Dcasbah-snapshot.mongo-url="mongodb://mongodb/store.snapshots" -Dcasbah-journal.mongo-url="mongodb://mongodb/store.messages" -Ddiscussion.query.builder.mongodb.uri="mongodb://mongodb/blog" -Dblog.query.builder.mongodb.uri="mongodb://mongodb/blog" -Ddiscussion.query.mongodb.uri="mongodb://mongodb/blog" -Dblog.query.mongodb.uri="mongodb://mongodb/blog" -Dmicroservice.kafka.server="kafka:9092"

docker service create --name app1 -e SERVICE_PORTS="8083" -e VIRTUAL_HOST="*" -p 82:8083 --network proxy enpassant/rapids:1.0-SNAPSHOT -main blog.query.BlogQuery -Dcasbah-snapshot.mongo-url="mongodb://mongodb/store.snapshots" -Dcasbah-journal.mongo-url="mongodb://mongodb/store.messages" -Ddiscussion.query.builder.mongodb.uri="mongodb://mongodb/blog" -Dblog.query.builder.mongodb.uri="mongodb://mongodb/blog" -Ddiscussion.query.mongodb.uri="mongodb://mongodb/blog" -Dblog.query.mongodb.uri="mongodb://mongodb/blog" -Dmicroservice.kafka.server="kafka:9092"

```
