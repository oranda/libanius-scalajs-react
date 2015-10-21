/*
 * Libanius
 * Copyright (C) 2012-2015 James McCabe <james@oranda.com>
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
            HttpEntity(
              MediaTypes.`text/html`,
              QuizScreen.skeleton.render
            )
          }
        } ~
        path("initialQuizData") {
          parameter("userToken") { userToken =>
            complete {
              val initialDataToClient: InitialDataToClient = QuizService.initialQuizData(userToken)
              upickle.write(initialDataToClient)
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
        path("restoreQuizLocal") {
          handleWith { data: MultipartFormData =>

            val userToken = data.get("userToken").map(_.entity.asString).getOrElse("")
            val strQuizGroup = data.get("fileQuizGroupData").map(_.entity.asString).getOrElse("")

            l.log("userToken: " + userToken)
            l.log("strQuizGroup: " + strQuizGroup)

            upickle.write(QuizService.parseQuiz(strQuizGroup, userToken))
          }
          complete {
            // set a cookie with the userToken
            HttpEntity(
              MediaTypes.`text/html`,
              QuizScreen.skeleton.render // TODO: QuizScreen.skeleton.render(Some(userToken))
            )
          }
        }
      }
    }
  }
}
