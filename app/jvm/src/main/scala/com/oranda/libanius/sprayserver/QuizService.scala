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

import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.Quiz
import com.oranda.libanius.model.action.QuizItemSource._
import com.oranda.libanius.model.action.modelComponentsAsQuizItemSources._
import com.oranda.libanius.model.action.{NoParams, QuizItemSource, modelComponentsAsQuizItemSources}
import com.oranda.libanius.model.quizgroup.{QuizGroupHeader, WordMapping}
import com.oranda.libanius.model.quizitem._
import com.oranda.libanius.scalajs._
import com.oranda.libanius.util.{StringUtil, Util}
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object QuizService extends AppDependencyAccess {

  private[this] val config = ConfigFactory.load().getConfig("libanius")

  private[this] val promptType = config.getString("promptType")
  private[this] val responseType = config.getString("responseType")

  private[this] val qgh = dataStore.findQuizGroupHeader(promptType, responseType, WordMapping)
  private[this] val qghReverse = dataStore.findQuizGroupHeader(responseType, promptType,
      WordMapping)

  // Map of user tokens to quizzes (in lieu of a proper database)
  private[this] var userQuizMap: Map[String, Quiz] = ListMap()

  private[this] val initialQuiz = loadQuiz

  def findFirstQuizItem(userToken: String) =
    findNextQuizItem(userToken, initialize = true)

  def processUserResponse(qia: QuizItemAnswer): DataToClient = {
    val quiz = getQuiz(qia.userToken)
    for {
      qgh <- dataStore.findQuizGroupHeader(qia.promptType, qia.responseType)
      quizItem <- quiz.findQuizItem(qgh, qia.prompt, qia.correctResponse)
    } yield Util.stopwatch(updateWithUserResponse(qia.userToken, qia.isCorrect, qgh, quizItem))

    saveQuiz(qia.userToken)
    findNextQuizItem(qia.userToken)
  }

  def removeCurrentWordAndShowNextItem(qia: QuizItemAnswer): DataToClient = {
    qgh.foreach(removeWord(qia.userToken, _, qia.prompt, qia.correctResponse))
    saveQuiz(qia.userToken)
    findNextQuizItem(qia.userToken)
  }

  private[this] def loadQuiz: Quiz = {
    val quizGroupHeaders = Seq(qgh, qghReverse).flatten
    Quiz(quizGroupHeaders.map(qgh => (qgh, dataStore.initQuizGroup(qgh))).toMap)
  }

  private[this] def findPresentableQuizItem(quiz: Quiz): Option[QuizItemViewWithChoices] =
    produceQuizItem(quiz, NoParams())

  private[this] def findNextQuizItem(userToken: String, initialize: Boolean = false):
      DataToClient = {
    val quiz = getQuiz(userToken, initialize)
    DataToClient(findQuizItem(quiz), scoreText(quiz))
  }

  private[this] def findQuizItem(quiz: Quiz): Option[QuizItemReact] =
    findPresentableQuizItem(quiz).map { qiv =>
      val promptResponseMap = makePromptResponseMap(quiz, qiv.allChoices, qiv.quizGroupHeader)
      QuizItemReact.construct(qiv, promptResponseMap)
    }

  private[this] def scoreText(quiz: Quiz): String = StringUtil.formatScore(quiz.scoreSoFar)

  private[this] def getQuiz(userToken: String, initialize: Boolean = false): Quiz = {
    def dealWithNoQuizFound: Quiz = {
      if (!initialize) l.logError("Quiz expected for userToken " + userToken + " but not found")
      updateUserQuizMap(userToken, initialQuiz)
      initialQuiz
    }
    userQuizMap.getOrElse(userToken, dealWithNoQuizFound)
  }

  private[this] def updateUserQuizMap(userToken: String, quiz: Quiz) {
    userQuizMap.synchronized {
      userQuizMap += (userToken -> quiz)
    }
  }

  private[this] def updateWithUserResponse(userToken: String, isCorrect: Boolean,
      qgh: QuizGroupHeader, quizItem: QuizItem) {
    val quiz = getQuiz(userToken)
    val updatedQuiz = quiz.updateWithUserResponse(isCorrect, qgh, quizItem)
    updateUserQuizMap(userToken, updatedQuiz)
  }

  private[this] def makePromptResponseMap(quiz: Quiz, choices: Seq[String],
      quizGroupHeader: QuizGroupHeader): Seq[(String, String)] =
    choices.map(promptToResponses(quiz, _, quizGroupHeader))

  private[this] def promptToResponses(quiz: Quiz, choice: String, quizGroupHeader: QuizGroupHeader):
      Tuple2[String, String] = {

    val values = quiz.findPromptsFor(choice, quizGroupHeader) match {
      case Nil => quiz.findResponsesFor(choice, quizGroupHeader.reverse)
      case v => v
    }

    (choice, values.mkString(", "))
  }

  private[this] def removeWord(userToken: String, qgh: QuizGroupHeader, prompt: String,
     correctResponse: String) {

    val quiz = getQuiz(userToken)
    val quizItem = QuizItem(prompt, correctResponse)
    val (updatedQuiz, wasRemoved) = quiz.removeQuizItem(quizItem, qgh)
    updateUserQuizMap(userToken, updatedQuiz)
    if (wasRemoved) l.log("Deleted quiz item " + quizItem)
    else l.logError("Failed to remove " + quizItem)
  }

  private[this] def saveQuiz(userToken: String) {
    val quiz = getQuiz(userToken)
    Future { dataStore.saveQuiz(quiz, conf.filesDir, userToken) }
  }
}
