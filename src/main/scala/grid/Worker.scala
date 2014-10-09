package grid

import akka.actor._
import akka.cluster.Cluster
import scala.Some
import scala.concurrent.duration._
import akka.actor.RootActorPath
import akka.actor.SupervisorStrategy.{Restart, Stop}

object Worker {
  private case class CoordinatorReady(actor: ActorRef)
  private case object IdentifyTimeout

  // messages from executor
  case class ExecuteDone(result: Option[Any])
}

class Worker(executorProps: Props) extends Actor with ActorLogging {
  import Coordinator._
  import Worker._
  import context.dispatcher

  val system = context.system
  val cluster = Cluster(system)
  var coordinator = ActorRef.noSender

  val coordinatorPath = cluster.state.roleLeader("coordinator") match {
    case Some(address) => RootActorPath(address) / "user" / "watcher" / "coordinator"
    case _ => throw new Exception("No cluster node with role 'coordinator' found!")
  }

  findCoordinator()

  def findCoordinator() = {
    context.actorSelection(coordinatorPath) ! Identify(coordinatorPath)
    context.system.scheduler.scheduleOnce(3.seconds, self, IdentifyTimeout)
  }

  val executor = context.watch(context.actorOf(executorProps, "executor"))

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
    case ActorIdentity(`coordinatorPath`, Some(actor)) =>
      log.info("Coordinator found at {}", actor.path)
      coordinator = context.watch(actor)
      coordinator ! Register
      context become idle

    case ActorIdentity(`coordinatorPath`, None) =>
      log.warning("Coordinator not yet available: {}", coordinatorPath)

    case IdentifyTimeout => findCoordinator()
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
    case IdentifyTimeout =>  // ignore
    case _ => super.unhandled(message)
  }
}
