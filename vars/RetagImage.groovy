/**@
    Pull Image from Container Registry

    parameters :
        oldImage : old image that will be retagged
        newImage : the name of the image 
*/
def call(Map config = [:]) {
    sh "docker tag ${config.oldImage} ${config.newImage} "
}
