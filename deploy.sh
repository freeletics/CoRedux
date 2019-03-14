#!/bin/bash
#
# Deploy a jar, source jar, and javadoc jar to Sonatype's snapshot repo.
#
# Adapted from https://coderwall.com/p/9b_lfq and
# http://benlimmer.com/2013/12/26/automatically-publish-javadoc-to-gh-pages-with-travis-ci/

SLUG="freeletics/CoRedux"
BRANCH="master"

set -e

if [ "$CIRCLE_PROJECT_USERNAME/$CIRCLE_PROJECT_REPONAME" != "$SLUG" ]; then
  echo "Skipping deployment: wrong repository. Expected '$SLUG' but was '$CIRCLE_PROJECT_USERNAME/$CIRCLE_PROJECT_REPONAME'."
elif [[ ! -z "$CIRCLE_PULL_REQUEST" ]]; then
  echo "Skipping deployment: was pull request."
elif [ "$CIRCLE_BRANCH" != "$BRANCH" ]; then
  echo "Skipping  deployment: wrong branch. Expected '$BRANCH' but was '$CIRCLE_BRANCH'."
else
  echo "Deploying ..."
  openssl aes-256-cbc -K $encrypted_06b1e6b9a94a_key -iv $encrypted_06b1e6b9a94a_iv -in freeletics.gpg.enc -out freeletics.gpg -d
  gpg --import freeletics.gpg
  echo "signing.password=$PGP_KEY" >> library/gradle.properties
  echo "signing.secretKeyRingFile=/home/travis/.gnupg/secring.gpg" >> library/gradle.properties
  echo "org.gradle.parallel=false" >> gradle.properties
  echo "org.gradle.configureondemand=false" >> gradle.properties
  ./gradlew --stop
  ./gradlew  --no-daemon uploadArchives -Dorg.gradle.parallel=false -Dorg.gradle.configureondemand=false
  rm freeletics.gpg
  git reset --hard
  echo "Deployed!"
fi
