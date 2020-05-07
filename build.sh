#!/bin/bash

set -ex

./gradlew --no-daemon -PenableCrossCompilerPlugin=true keel-web:installDist -x test
docker build -t nimak/spinnaker-keel:$1 -f Dockerfile.slim .
docker push nimak/spinnaker-keel:$1
