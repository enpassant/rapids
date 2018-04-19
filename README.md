# rapids
 Reducing Microservice Complexity with Kafka and Reactive Streams

## Inspirations

1. [Reducing Microservice Complexity with Kafka and Reactive Streams - by Jim Riecken](https://www.youtube.com/watch?v=k_Y5ieFHGbs)
2. [Øredev 2013 - Fred George - Implementing Micro Service Architecture](https://vimeo.com/79866979)
3. [Benchmarking Apache Kafka: 2 Million Writes Per Second (On Three Cheap Machines)](https://engineering.linkedin.com/kafka/benchmarking-apache-kafka-2-million-writes-second-three-cheap-machines)
4. [Understanding Akka Streams, Back Pressure and Asynchronous Architectures](https://www.lightbend.com/blog/understanding-akka-streams-back-pressure-and-asynchronous-architectures)

## Run in Docker Swarm

### Init
```bash
docker swarm init

docker service create --name portainer -p 9090:9000 --constraint 'node.role == manager' --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock portainer/portainer -H unix:///var/run/docker.sock

mkdir -p ~/mongodbdata
mkdir -p ~/kafkadata
```

### Start infra
```bash
docker stack deploy --compose-file infra-dc.yml rapids
```

### Start app (as monolith)
```bash
docker stack deploy --compose-file app-dc.yml rapids
```

### Start app (as microservices)
```bash
docker stack deploy --compose-file ms-dc.yml rapids
```

### Stop
```bash
docker stack rm rapids
```

## Show (consume) kafka messages:
```bash
docker exec -it `docker container ls | grep rapids_kafka | cut -d ' ' -f1` kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic blog-event --from-beginning --property print.key=true
```

### Used topics

blog-command, client-commands, blog-event, performance, discussion-event, web-app, user, discussion-command

## Use docker for development

```bash
docker run --name mongodb -p 27017:27017 mongo:latest
```

In sbt console:
```bash
~runMain Main -t
```
