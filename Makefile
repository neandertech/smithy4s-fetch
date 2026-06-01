SCALA_VERSION ?= 3.3.7

check-docs:
	scala-cli compile README.md smithy4s-fetch.scala project.scala --scala $(SCALA_VERSION)

compile:
	scala-cli compile . --scala $(SCALA_VERSION)

test:
	scala-cli test . --scala 3.4.2

publish-snapshot:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	scala-cli publish . -S 3.3.7 --signer none

publish:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	./.github/workflows/import-gpg.sh
	scala-cli publish . -S 3.3.7 --signer gpg --gpg-key 15A7215B6CD4016A

code-check:
	scala-cli fmt . --check

run-example:
	scala-cli run README.md project.scala smithy4s-fetch.scala -M helloWorld --scala $(SCALA_VERSION)

pre-ci:
	scala-cli fmt .
