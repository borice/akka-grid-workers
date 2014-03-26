package grid

import scala.concurrent.duration._
import akka.actor._
import akka.cluster.Cluster
import java.nio.file._
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.actor.ActorIdentity
import scala.Some
import akka.actor.Identify
import java.nio.file.attribute.BasicFileAttributes

object Producer {
  private case object StartProduce
  private case object IdentifyTimeout

  case object NoMoreWork
}

class Producer(dataDir: String, initialDelay: FiniteDuration) extends Actor with ActorLogging {
  import Producer._
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

  override def receive = {
    case ActorIdentity(`coordinatorPath`, Some(actor)) =>
      log.info("Coordinator found at {}", actor.path)
      coordinator = context.watch(actor)
      context.system.scheduler.scheduleOnce(initialDelay, self, StartProduce)

    case StartProduce => produceWork()

    case ActorIdentity(`coordinatorPath`, None) =>
      log.warning("Coordinator not yet available: {}", coordinatorPath)

    case IdentifyTimeout =>
      if (coordinator == ActorRef.noSender)
        findCoordinator()

    case Terminated(_) => context.stop(self)
  }

  def produceWork(): Unit = {
    log.info("Producing work...")
    var workCount = 0
    val dataPath = FileSystems.getDefault().getPath(dataDir)

    Files.walkFileTree(dataPath, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        val f = file.toString
        if (f.toLowerCase.endsWith(".zip")) {
          coordinator ! Work(f)
          workCount += 1
        }
        FileVisitResult.CONTINUE
      }
    })

    coordinator ! NoMoreWork
    log.info(f"Producer completed: $workCount%,d work items produced")
  }
}
