package common.web

import akka.actor._
import colossus.controller.Encoding
import colossus.core.{IOSystem, InitContext, ServerContext, WorkerRef}
import colossus.protocols.http.Http
import colossus.protocols.http.HttpMethod._
import colossus.protocols.http.UrlParsing._
import colossus.protocols.http.{HttpServer, Initializer, RequestHandler}
import colossus.protocols.websocket.WebsocketHttpHandler
import colossus.protocols.websocket.WebsocketInitializer
import colossus.service.Callback
import colossus.service.GenRequestHandler.PartialHandler

object WebService {
  def start[E <: Encoding](
    name: String,
    port: Int,
    httpHandler: PartialHandler[Http])
    (implicit system: ActorSystem, ioSystem: IOSystem) =
  {
    HttpServer.start(name, port){ context => new Initializer(context) {
      override def onConnect: RequestHandlerFactory =
        serverContext =>
          new RequestHandler(serverContext) {
            override def handle: PartialHandler[Http] = httpHandler
          }
    }}
  }

  def wsStart[E <: Encoding](
    name: String,
    port: Int,
    upgradePath: String = "/websocket",
    origins: List[String] = List.empty,
    httpHandler: PartialHandler[Http])
    (init: WorkerRef => WebsocketInitializer[E])
    (implicit system: ActorSystem, ioSystem: IOSystem) =
  {
    HttpServer.start(name, port){ context => new Initializer(context) {
      val websockinit : WebsocketInitializer[E] = init(context.worker)
      def onConnect =
        new WebsocketHttpHandler(_, websockinit, upgradePath, origins)
      {
        override def handle = super.handle orElse httpHandler
      }
    }}

  }
}
