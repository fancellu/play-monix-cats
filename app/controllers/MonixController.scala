package controllers

import javax.inject._
import monix.eval.{Fiber, Task}
import monix.execution.Scheduler
import play.api.mvc._
import playpen.P1

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */

@Singleton
class MonixController @Inject()(cc: ControllerComponents)(ec: ExecutionContext) extends AbstractController(cc) {
// note, ec is not implicit, we just need it to build Monix Scheduler later

  import cats._
  import cats.data._
  import cats.implicits._

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  val l1=List(1,2,3)
  val l2=List(2,3,4)
  val l3=List(3,4,5)

  // many ways to  build a scheduler, this is the simplest
  implicit val scheduler=Scheduler(ec)

  def gather = Action.async {
    val t1=P1.sumTask(l1)

    val t2=P1.sumTask(l2)

    val t3=P1.sumTaskF(l3)

    // we return in sequence, but effects are not ordered, so can run in parallel

    val ints: Task[Seq[Int]] =Task.gather(Seq(t1,t2,t3))

    println("about to run gather")
    ints.map{ints=>println(ints);Ok(ints.sum+"")}.runToFuture
  }

  def gatherunordered = Action.async {
    val t1=P1.sumTask(l1)

    val t2=P1.sumTask(l2)

    val t3=P1.sumTaskF(l3)

    // we return unordered, if you don't care about order use this, better perf

    val ints: Task[Seq[Int]] =Task.gatherUnordered(Seq(t1,t2,t3))

    println("about to run gatherunordered")
    ints.map{ints=>println(ints);Ok(ints.sum+"")}.runToFuture
  }

  def racemany = Action.async {
    // make this a tad more sluggish
    val t1=P1.sumTask(l1).delayExecution(100.milliseconds)

    val t2=P1.sumTask(l2)

    // even if this executes first, it never wins race because of result delay
    val t3=P1.sumTask(l3).delayResult(1000.milliseconds)

    val won: Task[Int] = Task.raceMany(Seq(t1,t2,t3))

    println("about to run racemany")
    won.map{i=>Ok(i+"")}.runToFuture
  }

  def sequence = Action.async {
    val t1=P1.sumTask(l1)

    val t2=P1.sumTask(l2)

    val t3=P1.sumTaskF(l3)

    // we run these in sequence
    // we could of course do it by hand with a for comprehension
    // Seq[Task]=>Task[Seq]
    val ints: Task[Seq[Int]] =Task.sequence(Seq(t1,t2,t3))

    println("about to run sequence")
    ints.map{ints=>println(ints);Ok(ints.sum+"")}.runToFuture
  }

  def parallel = Action.async {
    val t1=P1.sumTask(l1)

    val t2=P1.sumTask(l2)

    val t3=P1.sumTaskF(l3)

    // we run these in parallel via cats parallel
    val added: Task[Int] =(t1,t2,t3).parMapN{
      case (x,y,z) => x |+| y |+| z
    }

    Thread.sleep(2000)
    println("about to run parallel")
    added.map(i=>Ok(i+"")).runToFuture
  }

  def eager = Action.async {

    // if it times out, we will throw a timeout exception
    // we also use doOnFinish to handle success/failure
    val t1=P1.sumTask(l1).timeout(750.milliseconds).doOnFinish {
      case None =>
        println("Was success!")
        Task.unit
      case Some(ex) =>
        println(s"Had failure: $ex")
        Task.unit
    }

    // if it times out, we fall back to this strict value
    val t2=P1.sumTask(l2).timeoutTo(300.milliseconds, Task.now(999))

    val t3=P1.sumTaskF(l3)

    // guaranteed to be <10, we retry until it is
    val t4=P1.random.restartUntil(_<10)

    // on any error (we got an odd random number), we replace it with 22
    val t5=P1.randomError.onErrorFallbackTo(Task.now(22))

    // on any error (we got an odd random number), we try again 4 times.
    // Of course it may fail a 5th time and throw an illegal state exception
    val t6=P1.randomErrorRestart.onErrorRestart(maxRetries=4)

    // tasks do not memoize by default

    val m1=t1.memoize
    val m2=t2.memoize
    val m3=t3.memoize

    // we want these to kick off eagerly, in parallel.
    // If we don't, then will happen lazily, on demand. t2 would get run first, because it is referred to first
    // we memoize above, so they don't get run twice!

    List(m1,m2,m3).foreach(_.runToFuture)

    // combining with semigroup.combine

    val t2plust1: Task[Int] =m2 |+| m1 |+| m3 |+| t4 |+| t5 |+| t6

    // combining with for comprehension
    val added=for {
     const<-Task.now(100)
     x<-t2plust1
     y<-P1.sumTask(List(1,2,3,4,x,const))
    } yield y

    Thread.sleep(2000)

    println("about to run eager")

    val res=added.map(i=>Ok(i+"")).runToFuture

    res.onComplete{
      case Success(result)=>println(s"ok $result")
      case Failure(ex)=>println(s"oh dear $ex")
    }

    res
  }
}
