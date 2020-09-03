#!/bin/bash

PROJECT_ARG=$1
PROJECT_ALL="all"

PROJECT=${PROJECT_ARG:-${PROJECT_ALL}}

check() {
  name=$1
  code=$2

  if [ $code -ne 0 ]; then
    echo "${name} error!!"
    exit 1
  else
    echo "${name} complete"
  fi
}

sbt clean compile
check "sbt compile" $?

if [ "${PROJECT}" = "all" ]; then
  TARGET_DIR=$(ls -F applications/ | grep / | cut -d'/' -f1)
else
  TARGET_DIR=$(ls -F applications/ | grep / | grep "${PROJECT}" | cut -d'/' -f1)
fi

for project in ${TARGET_DIR}; do
  sh ./files/build-service.sh ${project} "applications/${project}"
done
