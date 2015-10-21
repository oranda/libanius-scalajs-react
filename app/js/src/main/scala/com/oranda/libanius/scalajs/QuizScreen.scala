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

package com.oranda.libanius.scalajs

import com.oranda.libanius.dependencies.AppDependencyAccess
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.document
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.window
import scala.scalajs.js.timers._
import japgolly.scalajs.react._

import scala.scalajs.js.annotation.JSExport

@JSExport
object QuizScreen {

  case class State(userToken: String, appVersion: String,
      availableQuizGroups: Seq[QuizGroupKey] = Seq.empty,
      currentQuizItem: Option[QuizItemReact] = None,
      prevQuizItem: Option[QuizItemReact] = None, scoreText: String = "",
      chosen: Option[String] = None, status: String = "") {

    def quizNotStarted = !currentQuizItem.isDefined && !prevQuizItem.isDefined
    def quizEnded = !currentQuizItem.isDefined && prevQuizItem.isDefined

    def onNewQuizItem(newQuizItem: Option[QuizItemReact], score: String): State =
      State(userToken, appVersion, availableQuizGroups, newQuizItem, currentQuizItem, score)

    def onNewQuiz(appVersion: String, availableQuizGroups: Seq[QuizGroupKey],
        newQuizItem: Option[QuizItemReact]): State =
      State(userToken, appVersion, availableQuizGroups, newQuizItem, None, "0.0%")

    def otherQuizGroups =
      currentQuizItem.map { cqi =>
        availableQuizGroups.filterNot(_ == QuizGroupKey(cqi.promptType, cqi.responseType))
      }.getOrElse(availableQuizGroups)
  }


  private[this] def newQuizState(responseText: String, state: State): State = {
    val quizData = upickle.read[InitialDataToClient](responseText)
    val appVersion = quizData.appVersion
    val availableQuizGroups: Seq[QuizGroupKey] = quizData.quizGroupHeaders
    val newQuizItem = quizData.quizItemReact
    state.onNewQuiz(appVersion, availableQuizGroups, newQuizItem)
  }

  class Backend(scope: BackendScope[Unit, State]) {

    def submitResponse(choice: String, curQuizItem: QuizItemReact) {
      scope.modState(_.copy(chosen = Some(choice)))
      val url = "/processUserResponse"
      val response = QuizItemAnswer.construct(scope.state.userToken, curQuizItem, choice)
      val data = upickle.write(response)
      val sleepMillis: Double = if (response.isCorrect) 200 else 1000
      Ajax.post(url, data).foreach { xhr =>
        setTimeout(sleepMillis) {
          scope.setState(updatedStateNewQuizItem(xhr.responseText, scope.state))
        }
      }
    }


    private[this] def updatedStateNewQuizItem(responseText: String, state: State): State =
      upickle.read[NewQuizItemToClient](responseText) match {
        case quizItemData: NewQuizItemToClient =>
          val newQuizItem = quizItemData.quizItemReact
          // Make a new quiz state with the new quiz item: curQuizItem becomes the prevQuizItem
          state.onNewQuizItem(newQuizItem, quizItemData.scoreText)
      }

    def removeCurrentWordAndShowNextItem(curQuizItem: QuizItemReact) {
      val url = "/removeCurrentWordAndShowNextItem"
      val response = QuizItemAnswer.construct(scope.state.userToken, curQuizItem, "")
      val data = upickle.write(response)
      Ajax.post(url, data).foreach { xhr =>
        scope.setState(updatedStateNewQuizItem(xhr.responseText, scope.state))
      }
    }

    def loadNewQuiz(qgKey: QuizGroupKey) {
      val url = "/loadNewQuiz"
      val data = upickle.write(LoadNewQuizRequest(scope.state.userToken, qgKey))
      Ajax.post(url, data).foreach { xhr =>
        scope.setState(newQuizState(xhr.responseText, scope.state))
      }
    }
  }

  val ScoreText = ReactComponentB[String]("ScoreText")
    .render(scoreText => <.span(^.id := "score-text", ^.className := "alignleft",
        "Score: " + scoreText))
    .build

  case class Question(promptWord: String, responseType: String, numCorrectResponsesInARow: Int)

  val QuestionArea = ReactComponentB[Question]("Question")
    .render(question =>
      <.span(
        <.span(^.id :=  "prompt-word", question.promptWord),
        <.p(^.id :=  "question-text",
          "What is the ", <.span(^.id := "response-type", question.responseType), "? ",
          <.span("(correctly answered ", question.numCorrectResponsesInARow, " times)")),
        <.br()))
    .build

  val PreviousPrompt = ReactComponentB[String]("PreviousPrompt")
    .render(prevPrompt => <.span(^.id := "prev-prompt", ^.className := "alignleft",
        "PREV: ",
         <.span(prevPrompt)
      )
    )
    .build

  val PreviousChoices = ReactComponentB[Seq[(String, String)]]("PreviousChoices")
    .render(prevChoices =>
      <.span(^.className := "alignright",
        prevChoices.map { case (prevPrompt, prevResponses) =>
          <.span(
            <.div(^.className := "alignleft prev-choice",
              prevPrompt + " = " + prevResponses
            ), <.br()
          )
        })
    )
    .build

  val PreviousQuizItemArea = ReactComponentB[Option[QuizItemReact]]("PreviousQuizItem")
    .render(_ match {
        case Some(previousQuizItem: QuizItemReact) =>
          <.span(^.id := "footer-wrapper",
            PreviousPrompt(previousQuizItem.prompt),
            PreviousChoices(previousQuizItem.promptResponseMap))
        case None => <.span()
      }).build

  val QuizPersistenceArea = ReactComponentB[(String, Backend)]("QuizPersistenceArea")
    .render(P => {
        val (userToken, backend) = P
        <.span(^.id := "quiz-persistence-area",
          <.span(^.id := "restore-data",
            <.form("Restore quiz group state from local disk: ",
              ^.action := "/restoreQuizLocal",
              ^.method := "post",
              ^.encType := "multipart/form-data",
              // hidden input with userToken
              <.input(^.name := "userToken", ^.`type` := "hidden", ^.value := userToken),
              <.input(^.id := "restore-button", ^.`type` := "file", ^.name := "fileQuizGroupData"),
              <.input(^.`type` := "submit")
            )),
          <.span(^.id := "save-data",
            <.span("Save quiz group state to local disk: "),
            <.br(),
            <.button(^.id := "save-button",
              ^.onClick --> window.location.assign("/saveQuizLocal?userToken=" + userToken),
              "Save"))
    )}).build

  val StatusText = ReactComponentB[String]("StatusText")
    .render(statusText => <.p(^.className := "status-text", statusText))
    .build

  val DescriptiveText = ReactComponentB[String]("DescriptiveText")
    .render(appVersion => <.span(^.id := "descriptive-text",
        <.a(^.href := "https://github.com/oranda/libanius-scalajs-react",
          "libanius-scalajs-react"),
        <.span(" v" + appVersion + " by "),
        <.a(^.href := "https://scala-bility.blogspot.de/", "James McCabe")))
    .build

  private[this] def cssClassForChosen(buttonValue: String, chosen: Option[String], correctResponse: String):
      String =
    chosen match {
      case None => ""
      case Some(chosenResponse) =>
        if (correctResponse == buttonValue) "correct-response"
        else {
          if (chosenResponse != buttonValue) "" else "incorrect-response"
        }
    }

  // TODO: check if a userToken already exists in a cookie
  // println("raw cookies = " + dom.document.cookie)
  // println(cookies)

  private[this] def generateUserToken =
    System.currentTimeMillis + "" + scala.util.Random.nextInt(1000)

  val QuizScreen = ReactComponentB[Unit]("QuizScreen")
    .initialState(State(generateUserToken, ""))
    .backend(new Backend(_))
    .render((_, state, backend) => state.currentQuizItem match {
      // Only show the page if there is a quiz item
      case Some(currentQuizItem: QuizItemReact) =>
        <.div(
          <.span(^.id := "header-wrapper", ScoreText(state.scoreText),
            <.span(^.className := "alignright",
              <.button(^.id := "delete-button",
                ^.onClick --> backend.removeCurrentWordAndShowNextItem(currentQuizItem),
                    "DELETE WORD"))
          ),
          QuestionArea(Question(currentQuizItem.prompt,
              currentQuizItem.responseType,
              currentQuizItem.numCorrectResponsesInARow)),
          <.span(currentQuizItem.allChoices.map { choice =>
            <.div(
              <.p(<.button(
                ^.className := "response-choice-button",
                ^.className := cssClassForChosen(choice, state.chosen,
                    currentQuizItem.correctResponse),
                ^.onClick --> backend.submitResponse(choice, currentQuizItem), choice))
            )}),
          PreviousQuizItemArea(state.prevQuizItem),
          StatusText(state.status),
          <.br(),<.br(),
          QuizPersistenceArea((state.userToken, backend)),
          <.br(),<.br(),<.br(),<.br(),
          <.span(
            <.span(^.id := "other-quiz-groups-header", "Other Quiz Groups"),
            <.br(), <.br(), <.br(),
            state.otherQuizGroups.map { qgKey =>
              <.span(
                ^.onClick --> backend.loadNewQuiz(qgKey),
                ^.className := "other-quiz-group-text",
                <.a(qgKey.promptType + " - " + qgKey.responseType),
                <.br(), <.br())
            }),
          <.br(),<.br(),<.br(),<.br(),
          DescriptiveText(state.appVersion)
        )
      case None =>
        if (!state.quizEnded)
          <.div("Loading...")
        else
          <.div("Congratulations! Quiz complete. Score: " + state.scoreText)
    })
    .componentDidMount(scope => {
      // Make the Ajax call for the first quiz item
      val url = "/initialQuizData?userToken=" + scope.state.userToken
      Ajax.get(url).foreach { xhr =>
        scope.setState(newQuizState(xhr.responseText, scope.state))
      }}
    )
    .buildU

  @JSExport
  def main(): Unit = {
    QuizScreen() render document.getElementById("container")
  }
}

