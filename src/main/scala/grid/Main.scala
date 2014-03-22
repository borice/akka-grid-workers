package grid

import akka.kernel.Bootable
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import akka.cluster.Cluster
import com.typesafe.scalalogging.slf4j.Logging
import java.net.InetAddress
import scala.Some
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

abstract class BootableCluster extends Bootable {
  import ActorWatcher._

  var system: Option[ActorSystem] = None
  var cluster: Option[Cluster] = None
  var watcher: Option[ActorRef] = None

  protected def fallbackConfig: Config
  protected def memberUp: Unit

  private val hostname = InetAddress.getLocalHost.getHostName
  val config = ConfigFactory
    .parseString(s"akka.remote.netty.tcp.hostname=$hostname")
    .withFallback(fallbackConfig)
  
  override def startup() = {
    system = Some(ActorSystem("cluster", config))
    cluster = Some(Cluster(system.get))

    cluster.get registerOnMemberUp memberUp

    if (config.getList("akka.cluster.seed-nodes").isEmpty)
      cluster.get.join(cluster.get.selfAddress)
  }

  override def shutdown() = {
    if (watcher.isDefined) {
      watcher.get ! Shutdown
      system foreach (_.awaitTermination())
    } else
      system foreach (_.shutdown())
  }
}

class CoordinatorMain extends BootableCluster with Logging {
  override def fallbackConfig = ConfigFactory.load("coordinator")

  override def memberUp: Unit = {
    logger.info("MemberUp!")
    val workTimeout = config.getDuration("coordinator.workTimeout", TimeUnit.SECONDS).seconds
    watcher = Some(system.get.actorOf(Props(classOf[ActorWatcher], Props(classOf[Coordinator], workTimeout), "coordinator"), "watcher"))
  }
}

class WorkerMain extends BootableCluster with Logging {
  override def fallbackConfig = ConfigFactory.load("worker")

  override def memberUp: Unit = {
    logger.info("MemberUp!")
    watcher = Some(system.get.actorOf(Props(classOf[ActorWatcher], Props[Worker], "worker"), "watcher"))
  }
}

class ProducerMain extends BootableCluster with Logging {
  override def fallbackConfig = ConfigFactory.load("producer")

  override def memberUp: Unit = {
    logger.info("MemberUp!")
    val count = config.getInt("producer.workCount")
    watcher = Some(system.get.actorOf(Props(classOf[ActorWatcher], Props(classOf[Producer], count), "producer"), "watcher"))
  }
}