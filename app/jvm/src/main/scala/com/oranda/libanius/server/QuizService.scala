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


import com.oranda.libanius.model.Quiz
import com.oranda.libanius.model.action.{modelComponentsAsQuizItemSources, QuizItemSource, NoParams}
import com.typesafe.config.ConfigFactory
import com.oranda.libanius.model.quizgroup.{QuizGroupHeader, WordMapping}
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.util.{StringUtil, Util}
import com.oranda.libanius.model.quizitem._
import com.oranda.libanius.scalajs._

import QuizItemSource._
import modelComponentsAsQuizItemSources._


object QuizService extends AppDependencyAccess {

  val config = ConfigFactory.load().getConfig("libanius")

  val promptType = config.getString("promptType")
  val responseType = config.getString("responseType")

  //val useMultipleChoiceUntil = config.getString("useMultipleChoiceUntil")

  val qgh = dataStore.findQuizGroupHeader(promptType, responseType, WordMapping)
  val qghReverse = dataStore.findQuizGroupHeader(responseType, promptType, WordMapping)

  // Persistent (immutable) data structure used in this single-user local web application.
  var quiz: Quiz = loadQuiz


  private def loadQuiz: Quiz = {
    val quizGroupHeaders = Seq(qgh, qghReverse).flatten
    Quiz(quizGroupHeaders.map(qgh => (qgh, dataStore.loadQuizGroup(qgh))).toMap)
  }

  private def findPresentableQuizItem: Option[QuizItemViewWithChoices] =
      produceQuizItem(quiz, NoParams())

  def findNextQuizItem: DataToClient = {
    val quizItemReact = findPresentableQuizItem.map { qiv =>
      val promptResponseMap = makePromptResponseMap(qiv.allChoices, qiv.quizGroupHeader)
      QuizItemReact.construct(qiv, promptResponseMap)
    }
    DataToClient(quizItemReact, score)
  }

  def score: String = StringUtil.formatScore(quiz.scoreSoFar)

  def processUserResponse(qir: QuizItemResponse): DataToClient = {

    for {
      qgh <- dataStore.findQuizGroupHeader(qir.promptType, qir.responseType)
      quizItem <- quiz.findQuizItem(qgh, qir.prompt, qir.correctResponse)
    } yield Util.stopwatch(quiz = quiz.updateWithUserResponse(qir.isCorrect, qgh, quizItem))

    findNextQuizItem
  }

  private def makePromptResponseMap(choices: Seq[String], quizGroupHeader: QuizGroupHeader):
      Seq[(String, String)] =
    choices.map(promptToResponses(_, quizGroupHeader))

  private def promptToResponses(choice: String, quizGroupHeader: QuizGroupHeader):
      Tuple2[String, String] = {
    val values = (quiz.findPromptsFor(choice, quizGroupHeader) match {
      case Nil => quiz.findResponsesFor(choice, quizGroupHeader.reverse)
      case values => values
    }).toList
    (choice, values.mkString(", "))
  }
}
