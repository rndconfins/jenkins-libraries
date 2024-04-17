/**@
    Build Docker Image For Testing Purpose
    parameters :
        imageName       : Docker image name to push to container registry
        executableName  : executable name for BE project
        cloudType       : Cloud provider name (Alibaba Cloud, Google Cloud, AWS, AWS CLI) -> AWS use file credentials, AWS CLI use access key&secret
        registryURL     : Container Registry endpoint URL
        regionId        : (AWS CLI) AWS Region ID
        credentialsId   : Credentials id for login to Container Registry

*/
def call(Map config = [:]) {
    config.imageName = config.imageName.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")
    if (config.cloudType == "AWS CLI") {
        if (!env.AWS_DEFAULT_REGION) {
            env.AWS_DEFAULT_REGION = config.regionId
        }
        withCredentials([usernamePassword(credentialsId: "${config.credentialsId}", passwordVariable: 'SECRET', usernameVariable: 'KEY')]) {
            sh "aws configure set aws_access_key_id $KEY"
            sh "aws configure set aws_secret_access_key $SECRET"
            sh "aws ecr get-login-password > ~/aws_creds.txt"
            sh "cat ~/aws_creds.txt | docker login --username AWS --password-stdin ${config.registryURL}"
        }
        dockerImageRemote = docker.build("${config.imageName}:build-${env.BUILD_ID}", "--build-arg executableName=${config.executableName} .")
        dockerImageRemote.push()
        dockerImageRemote.push("cloud")
    }
}
