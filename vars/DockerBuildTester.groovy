/**@
    Build Docker Image For Testing Purpose
    parameters :
        imageName : Docker image name to push to container registry

*/
def call(Map config = [:]) {
    config.imageName = config.imageName.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")
        dockerImageRemote = docker.build "${config.imageName}:build-${env.BUILD_ID}"
}
