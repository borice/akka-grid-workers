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

  val producerTimes = collection.mutable.ListBuffer.empty[Long]
  val workerTimes = collection.mutable.ListBuffer.empty[Long]
  var totalWorkReceived = 0

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
      workerTimes += (System.currentTimeMillis() - workers(sender).get.submitTime)
      workers += sender -> None
      sendWorkIfAvailable(Some(sender)) || stopIfNoMoreWork()

    case WorkFailed(exception) =>
      log.error(exception, "WorkFailed from {}", sender.path)
      workers += sender -> None
      sendWorkIfAvailable(Some(sender)) || stopIfNoMoreWork()

    case work: Work if !noMoreWork =>
      totalWorkReceived += 1
      producerTimes += (System.currentTimeMillis() - work.submitTime)
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
      log.info(s"[STATUS] Queue length: ${workQueue.length}, running: $outstanding, noMoreWork: $noMoreWork, workers: ${workers.size}, workReceived: $totalWorkReceived")
  }

  def idleWorker = workers.find(_._2 == None).map(_._1)

  def sendWorkIfAvailable(someWorker: Option[ActorRef]): Boolean = someWorker match {
    case Some(worker) if workQueue.nonEmpty =>
      val work = workQueue.dequeue().copy(submitTime = System.currentTimeMillis())
      worker ! work
      workers += worker -> Some(work)
      true

    case _ => false
  }

  def stopIfNoMoreWork(): Boolean = {
    val finishedWork = noMoreWork && workQueue.isEmpty && workers.find(_._2.isDefined).isEmpty
    if (finishedWork) {
      log.info("Finished all work - shutting down")
      // output the times so that they can be extracted from the logs and loaded into Excel for analysis
      producerTimes.zip(workerTimes).foreach(time => println(s"${time._1} ${time._2}"))
      context.stop(self)
    }
    finishedWork
  }
}
