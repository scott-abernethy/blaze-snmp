# Blaze SNMP

An event-driven, asynchronous and non-blocking SNMP library for Java and Scala, for highly scalable and performant SNMP management of network devices. Blaze is currently a work in progress and not suitable for production systems.

Blaze consists of
1. Blaze IO - an SNMP manager with a focus on high scalability, with mass throughput and low latency.
2. Blaze API - (Future) a high level API for Blaze IO and/or SNMP4J.

Created by Scott Abernethy (github @scott-abernethy).

## Current state 

Blaze implements the SNMP protocol on top of the new Akka v2.2 IO library.

Supported operations:
- SNMP Get. 

```scala
val service = new SnmpServiceImpl(ActorSystem("blaze"))

val address = new InetSocketAddress("192.168.1.1", 161)
val community = "public"
val sysUpTime = ObjectIdentifier(Seq(1,3,6,1,2,1,1,3,0))
val sysName = ObjectIdentifier(Seq(1,3,6,1,2,1,1,5,0))

val response: Future[GetResponse] = service.getRequest(new Target(address, community), List(sysUpTime, sysName)) 
```

## Roadmap for future development

TBD.

## License

Blaze SNMP is distributed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
