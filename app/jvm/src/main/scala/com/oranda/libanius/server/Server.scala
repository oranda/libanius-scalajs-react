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

package com.oranda.libanius.server

import akka.actor.ActorSystem
import com.oranda.libanius.dependencies.AppDependencyAccess
import spray.http._
import spray.routing.SimpleRoutingApp
import com.oranda.libanius.scalajs._
import com.typesafe.config.ConfigFactory

object Server extends SimpleRoutingApp with AppDependencyAccess {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    lazy val config = ConfigFactory.load()
    val port = config.getInt("libanius.port")

    startServer("0.0.0.0", port = port) {
      get {
        pathSingleSlash {
          complete{
            HttpEntity(
              MediaTypes.`text/html`,
              QuizScreen.skeleton.render
            )
          }
        } ~
        path("findNextQuizItem") {
          complete {
            upickle.write(QuizService.findNextQuizItem)
          }
        } ~
        // serve other requests directly from the resource directory
        getFromResourceDirectory("")
      } ~
      post {
        path("processUserResponse") {
          extract(_.request.entity.asString) { e =>
            complete {
              val quizItemResponse = upickle.read[QuizItemResponse](e)
              upickle.write(QuizService.processUserResponse(quizItemResponse))
            }
          }
        }
      }
    }
  }
}
