logLevel := Level.Warn

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.15")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
// This is an sbt plugin to help automate releases to Sonatype and Maven Central from GitHub Actions.
// See more: https://github.com/sbt/sbt-ci-release
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
