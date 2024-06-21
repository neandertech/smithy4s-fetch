check-docs:
	scala-cli compile README.md smithy4s-fetch.scala project.scala

test:
	scala-cli test .

publish-snapshot:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	scala-cli publish . -S 3.3.3 --signer none

publish:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	./.github/workflows/import-gpg.sh
	scala-cli publish . -S 3.3.3 --signer gpg --gpg-key 15A7215B6CD4016A

code-check:
	scala-cli fmt . --check

run-example:
	scala-cli run README.md project.scala smithy4s-fetch.scala -M helloWorld

pre-ci:
	scala-cli fmt .
