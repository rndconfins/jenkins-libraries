/**@
    Pull Image from Container Registry

    parameters :
        oldImage : old image that will be retagged
        newImage : the name of the new image 
        tagImage : the tag of the new iamge
*/
def call(Map config = [:]) {

    def arr = "${config.tagImage}".split(";")
    config.oldImage = config.oldImage.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")
    config.newImage = config.imageName.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")
    arr.each{ element ->
        sh "docker tag ${config.oldImage} ${config.newImage}:${element}"
    }
    
}
