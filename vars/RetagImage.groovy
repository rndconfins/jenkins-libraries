/**@
    Pull Image from Container Registry

    parameters :
        oldImage : old image that will be retagged
        newImage : the name of the new image 
        tagImage : the tag of the new iamge
*/
def call(Map config = [:]) {

    def arr = "${config.newImage}".split(";")
    arr.each{ element ->
        sh "docker tag ${config.oldImage} ${config.tagImage}:(element)"
    }
    
}
