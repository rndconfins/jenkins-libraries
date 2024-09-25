CLOUD_TYPE="$1"
DOCKER_REGISTRY_URL="$2"
USERNAME="$3"
PASSWORD="$4"
KEYFILE_URL="$5"

if [ "${CLOUD_TYPE}" = "AWS" ]
then	
  apk add --no-cache aws-cli
  aws configure set aws_access_key_id "${USERNAME}"
  aws configure set aws_secret_access_key "${PASSWORD}"
  aws ecr get-login-password > aws_creds.txt
  cat aws_creds.txt | docker login --username AWS --password-stdin "${DOCKER_REGISTRY_URL}"
elif [ "${CLOUD_TYPE}" = "ALIBABA" ]
then
  docker login -u "${USERNAME}" -p "${PASSWORD}" "${DOCKER_REGISTRY_URL}"
elif [ "${CLOUD_TYPE}" = "GCP" ]
then
  curl -o keyfile.json "${KEYFILE_URL}"
  cat keyfile.json | docker login -u _json_key --password-stdin "${DOCKER_REGISTRY_URL}"
fi
