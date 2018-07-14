/*
 * Libanius
 * Copyright (C) 2012-2018 James McCabe <james@oranda.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oranda.libanius.server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer
import com.oranda.libanius.scalajs._
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.Properties

import upickle.{default ⇒ upickle}

case class ImageUploaded(size: Int)

object Server extends HttpApp {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def routes: Route = {
    get {
      pathSingleSlash {
        complete {
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            QuizScreen.skeleton().render
          )
        }
      } ~
      path("staticQuizData") {
        parameter("userToken") { userToken =>
          complete {
            val staticDataToClient: StaticDataToClient = QuizService.staticQuizData(userToken)
            upickle.write(staticDataToClient)
          }
        }

      } ~
      path("saveQuizLocal") {
        parameter("userToken") { userToken =>
          // cause a "Save As" dialog
          respondWithHeader(`Content-Disposition`.apply(
            ContentDispositionTypes.attachment,
            Map(("filename", "quiz.txt")))) { complete(QuizService.quizDataToSave(userToken)) }
        }
      } ~
      path("findNextQuizItem") {
        parameter("userToken") { userToken =>
          complete {
            val newQuizItemToClient: NewQuizItemToClient = QuizService.findNextQuizItem(userToken)
            upickle.write(newQuizItemToClient)
          }
        }
      } ~
      // serve other requests directly from the resource directory
      getFromResourceDirectory("")
    } ~
    post {
      path("processUserResponse") {
        entity(as[String]) { e =>
          complete {
            val quizItemAnswer = upickle.read[QuizItemAnswer](e)
            val userResponse: NewQuizItemToClient = QuizService.processUserResponse(quizItemAnswer)
            upickle.write(userResponse)
          }
        }
      } ~
      path("removeCurrentWordAndShowNextItem") {
        entity(as[String]) { e =>
          complete {
            val quizItemAnswer = upickle.read[QuizItemAnswer](e)
            upickle.write(QuizService.removeCurrentWordAndShowNextItem(quizItemAnswer))
          }
        }
      } ~
      path("loadNewQuiz") {
        entity(as[String]) { e =>
          complete {
            val lnqRequest = upickle.read[LoadNewQuizRequest](e)
            upickle.write(QuizService.loadNewQuiz(lnqRequest))
          }
        }
      } ~
      path("restoreQuizLocal") {
        post {
          entity(as[Multipart.FormData]) { data =>
            complete {
              val extractedData: Future[Map[String, Any]] = data.parts.mapAsync[(String, Any)](1) {
                case data: BodyPart => data.toStrict(2.seconds).map(strict =>
                  data.name -> strict.entity.data.utf8String)
              }.runFold(Map.empty[String, Any])((map, tuple) => map + tuple)

              extractedData.map { data =>
                val userToken = data.get("userToken").getOrElse("").toString
                val strQuizGroup = data.get("fileQuizGroupData").getOrElse("").toString
                QuizService.parseQuiz(strQuizGroup, userToken)

                // We would like to just write back a new userToken here in Ajax style, but for multipart
                // data this requires XHR2 -- which org.scalajs.dom doesn't appear to support.
                // So instead, render the page again with the userToken.
                HttpEntity(
                  ContentTypes.`text/html(UTF-8)`,
                  QuizScreen.skeleton(Option(userToken)).render
                )
              }
            }
          }
        }
      }
    }
  }


  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load().getConfig("libanius")

    // If on Heroku, the application must get the port from the environment.
    val port = Properties.envOrElse("PORT", config.getString("port")).toInt
    startServer("0.0.0.0", port = port)
  }
}
