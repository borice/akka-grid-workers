package grid

import scala.concurrent.duration._
import akka.actor._
import akka.cluster.Cluster
import scala.Some
import akka.actor.RootActorPath

object Producer {
  private case object CoordinatorReady

  case object NoMoreWork
}

class Producer(count: Int) extends Actor with ActorLogging {
  import Producer._
  import context.dispatcher

  val system = context.system
  val cluster = Cluster(system)
  var coordinator = ActorRef.noSender

  cluster.state.roleLeader("coordinator") match {
    case Some(address) =>
      context.actorSelection(RootActorPath(address) / "user" / "watcher" / "coordinator")
        .resolveOne(5.seconds).onSuccess {
          case actor =>
            log.info("Coordinator found at {}", actor.path)
            coordinator = context.watch(actor)
            self ! CoordinatorReady
        }
    case _ =>
  }

  override def receive = {
    case CoordinatorReady => produceWork()
    case Terminated(_) => context.stop(self)
  }

  def produceWork(): Unit = {
    log.info("Producing work...")
    for (i <- 1 to count) {
      coordinator ! Work(i)
    }
    coordinator ! NoMoreWork
    log.info("Producer completed")
  }
}
