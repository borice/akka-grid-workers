package grid

import akka.actor.{ActorLogging, Actor}
import resource._
import scala.Some
import java.util.zip.{ZipEntry, ZipFile}
import java.io.{InputStreamReader, BufferedReader, File}
import collection.JavaConversions._
import com.google.common.base.Charsets
import com.google.common.io.Files
import edu.illinois.i3.emop.apps.pageevaluator.txt.TxtPage
import opennlp.tools.tokenize.SimpleTokenizer
import com.github.tototoshi.csv.CSVWriter

class Executor(outputDir: String) extends Actor with ActorLogging {
  import Worker._

  val PageNumber = """(\p{N}+)$""".r

  override def receive = {
    case Work(zipFilePath, htid, docId) =>
      for (zipFile <- managed(new ZipFile(new File(zipFilePath), Charsets.UTF_8))) {
        val stats = zipFile.entries().filterNot(_.isDirectory).withFilter(getPageNumber(_).isDefined).map(ze => {
          val pageId = getPageNumber(ze).get
          val pageStream = zipFile.getInputStream(ze)
          val pageReader = new BufferedReader(new InputStreamReader(pageStream, Charsets.UTF_8))
          val ocrPage = TxtPage.parse(pageReader, pageId.toString, SimpleTokenizer.INSTANCE)

          pageId -> ocrPage.calculateStatistics()
        }).toIndexedSeq.sortBy(_._1)

        val prefix = htid.takeWhile(_ != '.')
        val outDir = s"$outputDir/$prefix"

        val csvFile = new File(outDir, s"$htid.csv")
        for (writer <- managed(CSVWriter.open(csvFile))) {
          writer.writeRow(List("docId", "page", "tokenCount", "correctableScore", "qualityScore"))
          writer.writeAll(stats.map {
            case (pageId, stats) =>
              val cscore = stats.getCorrectableScore
              val qscore = stats.getQualityScore
              val corrScore = if (cscore.isNaN) """\N""" else cscore.toString
              val qualScore = if (qscore.isNaN) """\N""" else qscore.toString
              List(docId.toString, pageId.toString, stats.getTokenCount.toString, corrScore, qualScore)
          })
        }
      }

      sender ! ExecuteDone(None)
  }

  def getPageNumber(entry: ZipEntry): Option[Int] = {
    val pageFilePath = entry.getName
    val pageName = Files.getNameWithoutExtension(pageFilePath)
    PageNumber.findFirstIn(pageName) match {
      case Some(n) => Some(n.toInt)
      case None => None
    }
  }
}