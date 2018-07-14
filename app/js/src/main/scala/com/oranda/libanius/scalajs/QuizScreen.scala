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

package com.oranda.libanius.scalajs

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.document
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.window

import scala.scalajs.js.timers._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.{Failure, Success}
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
import upickle.{default => upickle}

@JSExportTopLevel("QuizScreen")
object QuizScreen {

  case class State(
      userToken: String,
      appVersion: String,
      availableQuizGroups: Seq[QuizGroupKey] = Seq.empty,
      currentQuizItem: Option[QuizItemReact] = None,
      prevQuizItem: Option[QuizItemReact] = None,
      scoreText: String = "",
      chosen: Option[String] = None,
      status: String = "") {

    def quizNotStarted = !currentQuizItem.isDefined && !prevQuizItem.isDefined
    def quizEnded = !currentQuizItem.isDefined && prevQuizItem.isDefined

    def onNewQuizItem(newQuizItem: Option[QuizItemReact], score: String): State =
      State(userToken, appVersion, availableQuizGroups, newQuizItem, currentQuizItem, score)

    def otherQuizGroups =
      currentQuizItem.map { cqi =>
        availableQuizGroups.filterNot(_ == QuizGroupKey(cqi.promptType, cqi.responseType))
      }.getOrElse(availableQuizGroups)
  }

  private[this] def newQuizStateFromStaticData(userToken: String, responseText: String): State = {
    val quizData = upickle.read[StaticDataToClient](responseText)
    val appVersion = quizData.appVersion
    val availableQuizGroups: Seq[QuizGroupKey] = quizData.quizGroupHeaders
    State(userToken, appVersion, availableQuizGroups, None, None, "0.0%")
  }

  private[this] def newQuizStateFromQuizItem(responseText: String, state: State): State = {
    val newQuizItem = upickle.read[NewQuizItemToClient](responseText)
    state.onNewQuizItem(newQuizItem.quizItemReact, newQuizItem.scoreText)
  }

  class Backend($: BackendScope[String, State]) {
    def start() =
      $.state.map { s =>
        def withUserToken(url: String) = s"$url?userToken=${s.userToken}"
        val loadStaticQuizData = Ajax.get(withUserToken("/staticQuizData"))
        loadStaticQuizData.onComplete {
          case Success(xhr) =>
            val state = newQuizStateFromStaticData(s.userToken, xhr.responseText)
            Ajax.get(withUserToken("/findNextQuizItem")).foreach { xhr =>
              $.setState(newQuizStateFromQuizItem(xhr.responseText, state)).runNow()
            }
          case Failure(e) => println(e.toString)
        }
      }

    def render(state: State): VdomElement =
      state.currentQuizItem match {
        // Only show the page if there is a quiz item
        case Some(currentQuizItem: QuizItemReact) =>
          <.div(
            <.span(^.id := "header-wrapper", ScoreText(state.scoreText),
              <.span(^.className := "alignright",
                <.button(^.id := "delete-button",
                  ^.onClick --> removeCurrentWordAndShowNextItem(state, currentQuizItem),
                  "DELETE WORD"))
            ),
            QuestionArea(Question(currentQuizItem.prompt,
              currentQuizItem.responseType,
              currentQuizItem.numCorrectResponsesInARow)),
            <.span(currentQuizItem.allChoices.toTagMod { choice =>
              <.div(
                <.p(<.button(
                  ^.className := "response-choice-button",
                  ^.className := cssClassForChosen(choice, state.chosen,
                    currentQuizItem.correctResponse),
                  ^.onClick --> submitResponse(state, choice, currentQuizItem), choice))
              )
            }),
            PreviousQuizItemArea(state.prevQuizItem),
            StatusText(state.status),
            <.br(), <.br(),
            QuizPersistenceArea(state.userToken),
            <.br(), <.br(), <.br(), <.br(),
            <.span(
              <.span(^.id := "other-quiz-groups-header", "Other Quiz Groups"),
              <.br(), <.br(), <.br(),
              state.otherQuizGroups.toTagMod(qgKey =>
                <.span(
                  ^.onClick --> loadNewQuiz(state, qgKey),
                  ^.className := "other-quiz-group-text",
                  <.a(s"${qgKey.promptType} - ${qgKey.responseType}"),
                  <.br(), <.br()))
            ),
            <.br(), <.br(), <.br(), <.br(),
            DescriptiveText(state.appVersion)
          )
        case None =>
          if (!state.quizEnded)
            <.div("Loading...")
          else
            <.div(s"Congratulations! Quiz complete. Score: ${state.scoreText}")
      }

    def submitResponse(state: State, choice: String, curQuizItem: QuizItemReact) = Callback {
      $.modState(_.copy(chosen = Option(choice))).runNow()
      val url = "/processUserResponse"
      val response = QuizItemAnswer.construct(state.userToken, curQuizItem, choice)
      val data = upickle.write(response)

      val sleepMillis: Double = if (response.isCorrect) 200 else 1000
      Ajax.post(url, data).foreach { xhr =>
        setTimeout(sleepMillis) {
          $.setState(updatedStateNewQuizItem(state, xhr.responseText)).runNow()
        }
      }
    }

    private def updatedStateNewQuizItem(state: State, responseText: String): State =
      upickle.read[NewQuizItemToClient](responseText) match {
        case quizItemData: NewQuizItemToClient =>
          val newQuizItem = quizItemData.quizItemReact
          // Make a new quiz state with the new quiz item: curQuizItem becomes the prevQuizItem
          state.onNewQuizItem(newQuizItem, quizItemData.scoreText)
      }

    private def removeCurrentWordAndShowNextItem(state: State, curQuizItem: QuizItemReact) = Callback {
      val url = "/removeCurrentWordAndShowNextItem"
      val response = QuizItemAnswer.construct(state.userToken, curQuizItem, "")
      val data = upickle.write(response)
      Ajax.post(url, data).foreach { xhr =>
        $.setState(updatedStateNewQuizItem(state, xhr.responseText)).runNow()
      }
    }

    private def loadNewQuiz(state: State, qgKey: QuizGroupKey) = Callback {
      val url = "/loadNewQuiz"
      val data = upickle.write(LoadNewQuizRequest(state.userToken, qgKey))
      Ajax.post(url, data).foreach { xhr =>
        $.setState(newQuizStateFromStaticData(state.userToken, xhr.responseText))
        Ajax.get(s"/findNextQuizItem?userToken=${state.userToken}").foreach { xhr =>
          $.setState(newQuizStateFromQuizItem(xhr.responseText, state)).runNow()
        }
      }
    }

    private def saveQuizLocal(userToken: String) = Callback {
      window.location.assign(s"/saveQuizLocal?userToken=$userToken")
    }
  }

  val ScoreText = ScalaComponent.builder[String]("ScoreText")
    .render_P(scoreText => <.span(^.id := "score-text", ^.className := "alignleft",
        s"Score: $scoreText"))
    .build

  case class Question(promptWord: String, responseType: String, numCorrectResponsesInARow: Int)

  val QuestionArea = ScalaComponent.builder[Question]("Question")
    .render_P(question =>
      <.span(
        <.span(^.id :=  "prompt-word", question.promptWord),
        <.p(^.id :=  "question-text",
          "What is the ", <.span(^.id := "response-type", question.responseType), "? ",
          <.span("(correctly answered ", question.numCorrectResponsesInARow, " times)")),
        <.br()))
    .build

  val PreviousPrompt = ScalaComponent.builder[String]("PreviousPrompt")
    .render_P(prevPrompt => <.span(^.id := "prev-prompt", ^.className := "alignleft",
        "PREV: ",
         <.span(prevPrompt)
      )
    )
    .build

  val PreviousChoices = ScalaComponent.builder[Seq[(String, String)]]("PreviousChoices")
    .render_P(prevChoices =>
      <.span(^.className := "alignright",
        prevChoices.toTagMod { case (prevPrompt, prevResponses) =>
          TagMod(<.span(
            <.div(^.className := "alignleft prev-choice",
              s"$prevPrompt = $prevResponses"
            ), <.br()
          ))
        })).build

  val PreviousQuizItemArea = ScalaComponent.builder[Option[QuizItemReact]]("PreviousQuizItem")
    .render_P(_ match {
        case Some(previousQuizItem: QuizItemReact) =>
          <.span(^.id := "footer-wrapper",
            PreviousPrompt(previousQuizItem.prompt),
            PreviousChoices(previousQuizItem.promptResponseMap))
        case None => <.span()
      }).build

  val QuizPersistenceArea = ScalaComponent.builder[(String)]("QuizPersistenceArea")
    .render_P(userToken => {
        <.span(^.id := "quiz-persistence-area",
          <.span(^.id := "restore-data",
            <.span("Restore quiz group state from local disk: "),
            <.br(),
            <.br(),
            <.form(
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
            <.br(),
            <.button(^.id := "save-button",
              ^.onClick --> Callback { window.location.assign(s"/saveQuizLocal?userToken=$userToken")},
              "Save"))
    )}).build

  val StatusText = ScalaComponent.builder[String]("StatusText")
    .render_P(statusText => <.p(^.className := "status-text", statusText))
    .build

  val DescriptiveText = ScalaComponent.builder[String]("DescriptiveText")
    .render_P(appVersion => <.span(^.id := "descriptive-text",
        <.a(^.href := "https://github.com/oranda/libanius-scalajs-react",
          "libanius-scalajs-react"),
        <.span(s" v$appVersion by "),
        <.a(^.href := "https://scala-bility.blogspot.de/", "James McCabe")))
    .build

  private[this] def cssClassForChosen(
      buttonValue: String,
      chosen: Option[String],
      correctResponse: String): String =
    chosen match {
      case None => ""
      case Some(chosenResponse) =>
        if (correctResponse == buttonValue) "correct-response"
        else {
          if (chosenResponse != buttonValue) "" else "incorrect-response"
        }
    }

  private[this] def getOrGenerateUserToken(userToken: String) =
    if (userToken.nonEmpty) userToken
    else s"${System.currentTimeMillis}${scala.util.Random.nextInt(1000)}"


  val QuizScreen = ScalaComponent.builder[String]("QuizScreen")
    .initialStateFromProps(props => State(getOrGenerateUserToken(props), ""))
    .backend(new Backend(_))
    .renderBackend
    .componentDidMount(_.backend.start)
    .build

  @JSExport
  def main(userToken: String): Unit =
    // userToken may be empty, in which case a new unique userToken will be generated
    QuizScreen(userToken).renderIntoDOM(document.getElementById("container"))
}

