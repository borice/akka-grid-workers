package grid

import akka.actor._
import akka.cluster.Cluster
import scala.Some
import scala.concurrent.duration._
import akka.actor.RootActorPath
import akka.actor.SupervisorStrategy.{Restart, Stop}

object Worker {
  private case class CoordinatorReady(actor: ActorRef)

  // messages from executor
  case class ExecuteDone(result: Option[Any])
}

class Worker extends Actor with ActorLogging {
  import Coordinator._
  import Worker._
  import context.dispatcher

  val system = context.system
  val cluster = Cluster(system)
  var coordinator = ActorRef.noSender

  cluster.state.roleLeader("coordinator") match {
    case Some(address) =>
      context.actorSelection(RootActorPath(address) / "user" / "watcher" / "coordinator")
        .resolveOne(10.seconds).onSuccess {
          case actor => self ! CoordinatorReady(actor)
        }
    case _ =>
  }

  val executor = context.watch(context.actorOf(Props[Executor], "executor"))

  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException           => Stop
    case e: Throwable =>
      coordinator ! WorkFailed(e)
      context become idle
      Restart
  }

  override def receive = waitForCoordinator

  def waitForCoordinator: Receive = {
    case CoordinatorReady(actor) =>
      log.info("Coordinator found at {}", actor.path)
      coordinator = context.watch(actor)
      coordinator ! Register
      context become idle
  }

  def idle: Receive = {
    case work: Work =>
      executor ! work
      context become working
  }

  def working: Receive = {
    case ExecuteDone(result) =>
      coordinator ! WorkDone
      context become idle
  }

  override def unhandled(message: Any): Unit = message match {
    case Terminated(_) => context.stop(self)
    case _ => super.unhandled(message)
  }
}
