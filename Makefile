check-docs:
	scala-cli compile README.md smithy4s-fetch.scala project.scala

test:
	scala-cli test .

publish:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USER env:SONATYPE_PASSWORD
	scala-cli publish . -S 3.3.3

code-check:
	scala-cli fmt . --check

run-example:
	scala-cli run README.md project.scala smithy4s-fetch.scala -M helloWorld

pre-ci:
	scala-cli fmt .
