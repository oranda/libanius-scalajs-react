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

import scala.concurrent._
import scalatags.Text.all._

object QuizScreen {
  //val boot =

 // @JSExport
  //def main(): Unit =
    //"com.oranda.libanius.scalajs.HelloMessageExample().main(document.getElementById('container'))"
    //"com.oranda.libanius.scalajs.QuizScreen().main(document.getElementById('container'))"
  val skeleton =
    html(
      head(
        link(
          rel:="stylesheet",
          href:="quiz.css"
        ),
        script(src:="/app-jsdeps.js")
      ),
      body(
        script(src:="/app-fastopt.js"),
        //onload:=boot,
        div(cls:="center", id:="container"),
        //script("com.oranda.libanius.scalajs.HelloMessageExample().main(document.getElementById('container'))")
        script("com.oranda.libanius.scalajs.QuizScreen().main()")

      )
    )



  /*
  def showNextQuizItem(quizItem: QuizItemViewWithChoices, prevPrompt: String,
                       prevChoiceStrings: Array[String], prevResponse: String, prevCorrectResponse: String):
  Result = {

    future { dataStore.saveQuiz(quiz, conf.filesDir) }

    Ok(Json.toJson(DataToClient(promptType = quizItem.promptType,
      responseType = quizItem.responseType,
      score = score,
      prompt = quizItem.prompt.value,
      numCorrectResponsesInARow = quizItem.numCorrectAnswersInARow,
      choices = Array(quizItem.allChoices(0), quizItem.allChoices(1), quizItem.allChoices(2)),
      correctResponse = quizItem.correctResponse.value,
      prevPrompt = prevPrompt,
      prevChoices = prevChoiceStrings,
      prevResponse = prevResponse,
      prevCorrectResponse = prevCorrectResponse)))
  }
  */
}

/*
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Libanius: Quiz Screen</title>
    <link rel="stylesheet" type="text/css" href="quiz.css"/>
</head>
<body>
    <div class="center" id="container">

    </div>

<P class="status-text"/>
<!-- Include Scala.js compiled code -->
<script type="text/javascript" src="./target/scala-2.11/libanius-scala-js-front-end-fastopt.js"></script>
<!-- Run tutorial.webapp.TutorialApp -->
<script type="text/javascript">
      com.oranda.libanius.web.QuizScreen().main(document.getElementById('container'));
</script>
</body>
</html>
 */
