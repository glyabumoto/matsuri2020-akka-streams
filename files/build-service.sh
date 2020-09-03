#!/bin/bash

PROJECT=$1
ROOT_DIR=$2
REPOSITORY="scalamatsuri-${PROJECT}"

echo "build stream service"
echo "repository:${REPOSITORY}"

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

projectVal="applications_${PROJECT//-/_}"
sbt "${projectVal}/assembly"
check "sbt assembly ${PROJECT}" $?

pushd ${ROOT_DIR}

aws ecr get-login-password | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-1.amazonaws.com/${REPOSITORY}:latest"
check "ecs login" $?

aws ecr describe-repositories --repository-names "${REPOSITORY}"
status=$?

if [[ ! "${status}" -eq 0 ]]; then
  aws ecr create-repository --repository-name "${REPOSITORY}"
  check "create repository ${REPOSITORY}" $?
fi

docker build -t "${REPOSITORY}" --build-arg SYSENV="${SYSENV}"  .
check "docker build" $?

docker tag "${REPOSITORY}:latest" "${AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-1.amazonaws.com/${REPOSITORY}:latest"
check "docker tag" $?

docker push "${AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-1.amazonaws.com/${REPOSITORY}:latest"
check "docker push" $?

jobDef=`aws ecs list-task-definitions --family-prefix ${REPOSITORY} | jq -r '.taskDefinitionArns | length'`

if [ "${jobDef}" = "0" ]; then

cat << EOF > job-definition.spec.json
[
  {
    "environment": [],
    "name": "${REPOSITORY}",
    "mountPoints": [
      {
        "sourceVolume": "logs",
        "containerPath": "/var/log/scalamatsuri"
      }
    ],
    "image": "995375717683.dkr.ecr.ap-northeast-1.amazonaws.com/${REPOSITORY}:latest",
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-region": "ap-northeast-1",
        "awslogs-stream-prefix": "ecs",
        "awslogs-group": "/ecs/${REPOSITORY}"
      }
    },
    "cpu": 0,
    "portMappings": [],
    "entryPoint": [
      "sh",
      "/root/startup.sh"
    ],
    "essential": true,
    "volumesFrom": []
  }
]
EOF

  aws ecs register-task-definition \
    --family "${REPOSITORY}" \
    --task-role-arn "arn:aws:iam::${AWS_ACCOUNT_ID}:role/BatchRole" \
    --execution-role-arn "arn:aws:iam::${AWS_ACCOUNT_ID}:role/BatchRole" \
    --container-definitions file://job-definition.spec.json \
    --volumes "[{\"host\": {\"sourcePath\": \"/var/log/scalamatsuri\"}, \"name\": \"logs\"}]" \
    --requires-compatibilities EC2 \
    --cpu 2048 \
    --memory 3700 \
    --tags "[{\"key\":\"CmBillingGroup\",\"value\":\"scalamatsuri\"}]"

  check "create repository ${REPOSITORY}" $?

  aws logs create-log-group --log-group-name "/ecs/${REPOSITORY}"
  check "create log group /ecs/${REPOSITORY}" $?

fi

echo "----- project stream ${PROJECT} -----"
echo "arn:aws:ecs:ap-northeast-1:${AWS_ACCOUNT_ID}:task-definition/${REPOSITORY}"
echo "----------"

popd
