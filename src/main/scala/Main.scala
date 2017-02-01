import common._

import akka.actor._

object Main extends App {
	val system = ActorSystem("Main")

	WebApp.start(system)
	TopicCommandApp.start(system)

	scala.io.StdIn.readLine()
	system.terminate
}
