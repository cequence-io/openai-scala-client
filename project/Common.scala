/*
 * Copyright (c) 2022 Felipe Bonezi <https://about.me/felipebonezi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object Common extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = JvmPlugin

  val gitAccount = "cequence-io"
  val repoName = "openai-scala-client"

  override def globalSettings: Seq[Setting[_]] =
    Seq(
      // project
      description := "Scala client for OpenAI API",
      // organization
      organization := "cequence.io",
      organizationName := "Cequence",
      organizationHomepage := Some(
        url(s"https://github.com/$gitAccount/$repoName")
      ),
     // legal
      licenses := Seq(
        "MIT" ->
          url(s"https://github.com/$gitAccount/$repoName/blob/main/LICENSE")
      ),
      // on the web
      homepage := Some(url(s"https://github.com/$gitAccount/$repoName")),
      developers += Developer(
        "contributors",
        "Contributors",
        s"https://github.com/$gitAccount/$repoName/graphs/contributors",
        url(s"https://github.com/$gitAccount")
      )
    )

}
