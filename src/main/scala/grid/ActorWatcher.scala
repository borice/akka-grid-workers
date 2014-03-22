package grid

import akka.actor._
import akka.cluster.ClusterEvent.{MemberRemoved, InitialStateAsEvents}
import akka.cluster.Cluster

object ActorWatcher {
  case object Shutdown
}

final class ActorWatcher(actorProps: Props, actorName: String) extends Actor {
  import ActorWatcher._

  val system = context.system
  val cluster = Cluster(system)
  val watchee = context.watch(context.actorOf(actorProps, actorName))

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
  override def preStart() = cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberRemoved])
  override def postStop() = cluster.unsubscribe(self)

  override def receive = {
    case Terminated(`watchee`) => cluster.leave(cluster.selfAddress)
    case MemberRemoved(member, _) if member.address == cluster.selfAddress => system.shutdown()
    case Shutdown => context.stop(watchee)
  }
}