Libanius-ScalaJs-React
======================

Libanius is an app to aid learning. Basically it presents "quiz items" to the user, and for each one the user must select the correct answer option. Quiz items are presented at random according to a certain algorithm. An item has to be answered correctly several times before it is considered learnt.

The core use is as a vocabulary builder in a new language, but it is designed to be flexible enough to present questions and answers of all types.

The implementation of Libanius is in Scala. There are Android and Web-based interfaces.

This project is the second attempt at a Web interface to Libanius. It is implemented using Scala.js and React. (The former version was based on Play Framework and AngularJS). The core Libanius code is located here: https://github.com/oranda/libanius

Suggestions for new features and code improvements will be happily received by:

James McCabe <james@oranda.com>


Install
=======

You need to have Scala installed to run Libanius-ScalaJs-React. It has been tested with Scala 2.11.6, Java 8, and sbt 0.13.6.

To install, either download the zip file for this project or clone it with git:

    git clone git://github.com/oranda/libanius-scalajs-react

Then cd to the libanius-scalajs-react directory and run it:

    sbt appJVM/run

Then just open your browser at http://localhost:8080/

Different users will get their own separate instances of the quiz.


Implementation
==============

This front-end to Libanius uses Scala.js to convert Scala code to JavaScript and HTML on the front-end (app/js folder).

The scala-js-react library is used to model front-end components and event handling according to Facebook's React framework.

Ajax calls are made to the QuizService which runs on a spray server (app/jvm folder). The service 
communicates with the Libanius core library to fetch quiz items. 

As usual, the data store maintained by the Libanius core for the quiz is just a file or collection of 
files representing quiz groups.


Screenshots
===========

![Libanius](https://github.com/oranda/libanius-scalajs-react/raw/master/docs/libanius-scalajs-react-v0.2-screenshot.png)


License
=======

Most Libanius-ScalaJs-React source files are made available under the terms of the GNU Affero General Public License (AGPL).
See individual files for details.

Attribution info is in [SOURCES](SOURCES.md).
