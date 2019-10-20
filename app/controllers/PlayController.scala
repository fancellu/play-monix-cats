package controllers

import javax.inject._
import monix.eval.Task
import monix.execution.Scheduler
import play.api.libs.ws.WSClient
import play.api.mvc._
import playpen.P1

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import models.{BreedList, _}
import play.api.libs.json.{JsPath, Json, JsonValidationError}

@Singleton
class PlayController @Inject()(cc: ControllerComponents)(ec: ExecutionContext, ws: WSClient) extends AbstractController(cc) {
  // note, ec is not implicit, we just need it to build Monix Scheduler later

  implicit val randomImageReads = Json.reads[RandomImage]
  implicit val breedListReads = Json.reads[BreedList]

  val scheduler = Scheduler(ec)

  def index = Action.async {

    Task.deferFutureAction { implicit sched =>

      val dogs = ws.url("https://dog.ceo/api/breeds/list/all").get()

      dogs.map { response =>
        val body = response.json.validate[BreedList].asEither
        body.fold(
          bad => {
            println(bad); InternalServerError(bad.toString)
          },
          breedList => Ok(breedList.toString)
        )
      }
    }.runToFuture(scheduler)
  }

  // This is a val because a Task is a spec, it isn't run until later

  private val randomImageTask: Task[Either[Seq[(JsPath, Seq[JsonValidationError])], RandomImage]] =
    Task.deferFutureAction { implicit sched =>
      val resultF = ws.url("https://dog.ceo/api/breeds/image/random").get()
      resultF.map { response =>
        response.json.validate[RandomImage].asEither
      }
    }

  private def getRandomImageTaskFromBreed(breed:String): Task[Either[Seq[(JsPath, Seq[JsonValidationError])], RandomImage]] =
    Task.deferFutureAction { implicit sched =>
      val resultF = ws.url(s"https://dog.ceo/api/breed/$breed/images/random").get()
      resultF.map { response =>
        response.json.validate[RandomImage].asEither
      }
    }

  def randomimage(breed: Option[String]) = Action.async {
    breed.map(getRandomImageTaskFromBreed).getOrElse(randomImageTask).map(_.fold(
      error => {
        println(error); InternalServerError(error.toString)
      },
      randomImage => Ok(views.html.randomImage(randomImage))
    )).runToFuture(scheduler)
  }

  def randomimagex2 = Action.async {

    // we delay both by 3 seconds, both complete in 5 seconds, because they are parallel

    val taskResult = for {
      eitherList <- Task.gatherUnordered(Seq(randomImageTask.delayExecution(3.seconds), randomImageTask.delayExecution(3.seconds)))
      either1 :: either2 :: Nil = eitherList
    } yield
      (for {
        r1 <- either1
        r2 <- either2
      } yield Ok(views.html.randomImagex2(r1, r2))
        ).fold(error => {
         println(error)
         InternalServerError(error.toString)
        }, identity
      )
    taskResult.runToFuture(scheduler)
  }

}
