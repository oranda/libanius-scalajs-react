/*
 * Libanius
 * Copyright (C) 2012-2019 James McCabe <james@oranda.com>
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

import scalatags.Text.all._

object QuizScreen {
  /*
   * By default the userToken is None here, and is generated on initialization,
   * but there is a possibility of loading data for an existing user.
   */
  def skeleton(userToken: Option[String] = None) =
    html(
      head(
        link(
          rel := "stylesheet",
          href := "quiz.css"
        ),
        script(src := "/app-jsdeps.js")
      ),
      body(
        script(src := "/app-fastopt.js"),
        div(cls := "center", id := "container"),
        script(s"QuizScreen.main('${userToken.getOrElse("")}')")
      )
    )
}
