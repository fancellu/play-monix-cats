package playpen

import monix.catnap.FutureLift
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

// Task is better than Future, lazy, no need to litter code with implicit ec

object P1{

  lazy val DELAY = 1000

  def sum(list: List[Int]): Int =
    list.sum

  def sumF(list: List[Int])(implicit ec: ExecutionContext): Future[Int] =
    Future(sum(list))

  def sumTaskF(list: List[Int]): Task[Int] =
    Task.deferFutureAction { implicit schedular =>
      val pause = scala.util.Random.nextInt(DELAY)
      println(s"pausef $pause for $list")
      Thread.sleep(pause)
      println(s"adding $list")
      sumF(list)
    }

  // via monix.catnap. Could return cats.IO instead
  def sumTaskF2(list: List[Int])(implicit ec: ExecutionContext): Task[Int] =
    FutureLift.from(Task {
      val pause = scala.util.Random.nextInt(DELAY)
      println(s"pausef2 $pause for $list")
      Thread.sleep(pause)
      println(s"adding $list")
      sumF(list)
    })

  def sumTask(list: List[Int]): Task[Int] = {
    val pause = scala.util.Random.nextInt(DELAY)
    val out = for {
      _ <- Task {
        println(s"pause $pause for $list")
      }
      _ <- Task.sleep(pause.milliseconds)
      t <- Task {
        println(s"adding $list"); sum(list)
      }
    } yield t
    out
  }

  val random: Task[Int] =
    for {
      rand <- Task(scala.util.Random.nextInt(100))
      _ <- Task {
        println(s"random()==$rand")
      }
    } yield rand

  val randomError: Task[Int] =
    Task(Random.nextInt(10)).flatMap {
      case even if even % 2 == 0 => println(s"even $even"); Task.now(even)
      case odd => println(s"odd $odd"); throw new IllegalStateException(odd.toString)
    }

  val randomErrorRestart: Task[Int] =
    Task(Random.nextInt(10)).flatMap {
      case even if even % 2 == 0 => println(s"even2 $even"); Task.now(even)
      case odd => println(s"odd2 $odd"); Task.raiseError(new IllegalStateException(odd.toString))
    }

}
