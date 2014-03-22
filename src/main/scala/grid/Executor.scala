package grid

import akka.actor.{ActorLogging, Actor}

class Executor extends Actor with ActorLogging {
  import Worker._

  override def receive = {
    case Work(payload, _) =>
      sender ! ExecuteDone(None)
  }
}