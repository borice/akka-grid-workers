package grid

import akka.actor.{Terminated, ActorRef, ActorLogging, Actor}
import scala.collection.mutable.{Map, Queue}
import scala.concurrent.duration.FiniteDuration
import scala.collection

object Coordinator {
  // messages from workers
  case object Register
  case object WorkDone
  case class WorkFailed(exception: Throwable)

  // internal messages
  private case object JobTimeoutCheckTick
}

class Coordinator(workTimeout: FiniteDuration) extends Actor with ActorLogging {
  import context.dispatcher
  import Coordinator._
  import Producer._

  val system = context.system

  private val workers = Map.empty[ActorRef, Option[Work]]
  private val workQueue = Queue.empty[Work]
  var noMoreWork = false

  var totalWorkReceived = 0
  var workDone = 0

  val jobTimeoutCheckTask = system.scheduler.schedule(
    initialDelay = workTimeout / 2,
    interval = workTimeout / 2,
    receiver = self,
    message = JobTimeoutCheckTick
  )

  override def postStop(): Unit = jobTimeoutCheckTask.cancel()

  override def receive = {
    case Register =>
      require(!workers.contains(sender), s"${sender.path} is already registered")
      context watch sender
      workers += sender -> None
      log.info("Registered worker: {}", sender.path)
      sendWorkIfAvailable(Some(sender)) || stopIfNoMoreWork()

    case WorkDone =>
      require(workers(sender).isDefined, s"WorkDone received for idle worker ${sender.path}")
      workDone += 1
      workers += sender -> None
      sendWorkIfAvailable(Some(sender)) || stopIfNoMoreWork()

    case WorkFailed(exception) =>
      log.error(exception, "WorkFailed on {} from {}", workers(sender).get.payload, sender.path)
      workers += sender -> None
      sendWorkIfAvailable(Some(sender)) || stopIfNoMoreWork()

    case work: Work if !noMoreWork =>
      totalWorkReceived += 1
      workQueue.enqueue(work)
      sendWorkIfAvailable(idleWorker)

    case NoMoreWork =>
      log.info("Producer signaled NoMoreWork")
      noMoreWork = true
      stopIfNoMoreWork()

    case Terminated(worker) =>
      log.warning("Worker TERMINATED: {}", worker.path)
      workers(worker) foreach {
        work =>
          workQueue.enqueue(work)
          sendWorkIfAvailable(idleWorker)
      }
      workers -= worker

    case JobTimeoutCheckTick =>
      // for now I won't worry about job timeouts - i'll use this timer for reporting the coordinator state
      val outstanding = workers.values.filter(_.isDefined).size
      log.info(f"[STATUS] Q: ${workQueue.length}%,d  R: $outstanding%,d  W: ${workers.size}%,d  D: $workDone%,d  T: $totalWorkReceived%,d  noMoreWork: $noMoreWork")
  }

  def idleWorker = workers.find(_._2 == None).map(_._1)

  def sendWorkIfAvailable(someWorker: Option[ActorRef]): Boolean = someWorker match {
    case Some(worker) if workQueue.nonEmpty =>
      val work = workQueue.dequeue()
      worker ! work
      workers += worker -> Some(work)
      true

    case _ => false
  }

  def stopIfNoMoreWork(): Boolean = {
    val finishedWork = noMoreWork && workQueue.isEmpty && workers.find(_._2.isDefined).isEmpty
    if (finishedWork) {
      log.info("Finished all work - shutting down")
      context.stop(self)
    }
    finishedWork
  }
}
