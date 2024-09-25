CLOUD_TYPE="$1"
DOCKER_REGISTRY_URL="$2"
USERNAME="$3"
PASSWORD="$4"
GCP_KEY="$5"
GCP_PROJECT="$6"
APP_NAME="$7"
VERSION="$8"

if [ "${CLOUD_TYPE}" = "AWS" ]
then	
  apk add --no-cache aws-cli
  aws configure set aws_access_key_id "${USERNAME}"
  aws configure set aws_secret_access_key "${PASSWORD}"
  aws ecr get-login-password > aws_creds.txt
  cat aws_creds.txt | docker login --username AWS --password-stdin "${DOCKER_REGISTRY_URL}"
  docker tag newimage:latest ${DOCKER_REGISTRY_URL}/${APP_NAME}:${VERSION}
  docker push ${DOCKER_REGISTRY_URL}/${APP_NAME}:${VERSION}
elif [ "${CLOUD_TYPE}" = "ALIBABA" ]
then
  docker login -u "${USERNAME}" -p "${PASSWORD}" "${DOCKER_REGISTRY_URL}"
  docker tag newimage:latest ${DOCKER_REGISTRY_URL}/${APP_NAME}:${VERSION}
  docker push ${DOCKER_REGISTRY_URL}/${APP_NAME}:${VERSION}
elif [ "${CLOUD_TYPE}" = "GCP" ]
then
  echo ${GCP_KEY} > gcloud-service-key.json
  gcloud auth activate-service-account --key-file=gcloud-service-key.json
  gcloud --quiet config set project ${GCP_PROJECT}
  gcloud --quiet auth configure-docker "${DOCKER_REGISTRY_URL}"
  docker tag newimage:latest ${DOCKER_REGISTRY_URL}/$GCP_PROJECT/${APP_NAME}:${VERSION}
  docker push ${DOCKER_REGISTRY_URL}/$GCP_PROJECT/${APP_NAME}:${VERSION}
fi
