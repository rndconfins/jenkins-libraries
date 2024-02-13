/**@
    Build Docker Image For Testing Purpose
    parameters :
        imageName : Docker image name to push to container registry
        baseHref  : base href of FE project (default: /)

*/
def call(Map config = [:]) {
    config.imageName = config.imageName.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")
        dockerImageRemote = docker.build("${config.imageName}:build-${env.BUILD_ID}", "--build-arg baseHref=${config.baseHref}", ".")
                    
}
