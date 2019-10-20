## play-monix-cats

Example Play application using Monix, Circe, Cats

### To run

`sbt run`

### Endpoints

#### Monix Task examples

GET     /eager                      controllers.MonixController.eager
GET     /parallel                   controllers.MonixController.parallel
GET     /sequence                   controllers.MonixController.sequence
GET     /gather                     controllers.MonixController.gather
GET     /gatherunordered            controllers.MonixController.gatherunordered
GET     /racemany                   controllers.MonixController.racemany

#### Circe+Monix examples

We do some Circe work against https://dog.ceo/dog-api/

GET     /circe                      controllers.CirceController.index
GET     /circe/randomimage          controllers.CirceController.randomimage(breed: Option[String])
GET     /circe/randomimagex2        controllers.CirceController.randomimagex2

### PlayJson+Monix examples

We implement the above dog endpoints using native play-json

GET     /playjson                   controllers.PlayController.index
GET     /playjson/randomimage       controllers.PlayController.randomimage(breed: Option[String])
GET     /playjson/randomimagex2     controllers.PlayController.randomimagex2