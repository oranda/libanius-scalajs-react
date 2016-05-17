/*
 * Libanius
 * Copyright (C) 2012-2016 James McCabe <james@oranda.com>
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

package com.oranda.libanius.sprayserver

import akka.actor.ActorSystem
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.scalajs._
import com.oranda.libanius.sprayserver.QuizService._
import com.typesafe.config.ConfigFactory
import spray.http.MediaTypes._
import spray.http._
import spray.http.HttpHeaders._
import spray.http.ContentType
import spray.http.MediaTypes

import spray.routing.SimpleRoutingApp

case class ImageUploaded(size: Int)

object Server extends SimpleRoutingApp with AppDependencyAccess {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()

    val config = ConfigFactory.load().getConfig("libanius")

    val port = config.getInt("port") // for Heroku compatibility

    startServer("0.0.0.0", port = port) {
      get {
        pathSingleSlash {
          complete {
            l.log("Server pathSingleSlash")
            HttpEntity(
              MediaTypes.`text/html`,
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
            respondWithHeader(`Content-Disposition`("attachment", Map(("filename", "quiz.txt")))) {
              complete(QuizService.quizDataToSave(userToken))
            }
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
          extract(_.request.entity.asString) { e =>
            complete {
              val quizItemAnswer = upickle.read[QuizItemAnswer](e)
              val userResponse: NewQuizItemToClient = QuizService.processUserResponse(quizItemAnswer)
              upickle.write(userResponse)
            }
          }
        } ~
          path("removeCurrentWordAndShowNextItem") {
            extract(_.request.entity.asString) { e =>
              complete {
                val quizItemAnswer = upickle.read[QuizItemAnswer](e)
                upickle.write(QuizService.removeCurrentWordAndShowNextItem(quizItemAnswer))
              }
            }
          } ~
          path("loadNewQuiz") {
            extract(_.request.entity.asString) { e =>
              complete {
                val lnqRequest = upickle.read[LoadNewQuizRequest](e)
                upickle.write(QuizService.loadNewQuiz(lnqRequest))
              }
            }
          } ~
          /*path("restoreQuizLocal") {
          post {
            entity(as[MultipartFormData]) { formData =>
              l.log("restoreQuizLocal called")
              complete {
                l.log("complete restoreQuizLocal")
                formData.fields.map { _.entity.asString }.flatten.foldLeft("")(_ + _)
              }
            }
          }
        }*/
          path("restoreQuizLocal") {
            post {
              entity(as[MultipartFormData]) { data =>
                complete {
                  val userToken = data.get("userToken").map(_.entity.asString).getOrElse("")
                  val strQuizGroup = data.get("fileQuizGroupData").map(_.entity.asString).getOrElse("")
                  QuizService.parseQuiz(strQuizGroup, userToken)

                  // We would like to just write back a new userToken here in Ajax style, but for multipart
                  // data this requires XHR2 -- which org.scalajs.dom doesn't appear to support.
                  // So instead, render the page again with the userToken.

                  HttpEntity(
                    MediaTypes.`text/html`,
                    //QuizScreen.skeleton.render
                    QuizScreen.skeleton(Some(userToken)).render
                  )
                }
              }
            }
          }
      }
    }
  }
}
