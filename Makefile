check-docs:
	scala-cli compile README.md smithy4s-fetch.scala project.scala

test:
	scala-cli test .

publish-snapshot:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	scala-cli publish . -S 3.3.3 --signer none

publish:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USER env:SONATYPE_PASSWORD
	echo "$PGP_SECRET" | base64 -d -i - > /tmp/signing-key.gpg
	echo "$PGP_PASSPHRASE" | gpg --pinentry-mode loopback --passphrase-fd 0 --import /tmp/signing-key.gpg
	(echo "$PGP_PASSPHRASE"; echo; echo) | gpg --command-fd 0 --pinentry-mode loopback --change-passphrase $(gpg --list-secret-keys --with-colons 2> /dev/null | grep '^sec:' | cut --delimiter ':' --fields 5 | tail -n 1)
	scala-cli publish . -S 3.3.3 --signer gpg --gpg-key 15A7215B6CD4016A

code-check:
	scala-cli fmt . --check

run-example:
	scala-cli run README.md project.scala smithy4s-fetch.scala -M helloWorld

pre-ci:
	scala-cli fmt .
