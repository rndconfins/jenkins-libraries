/**@
    Deploy to server step
    parameters :
        buildReleasePath : Path of build release project (release/dist)
        deploymentPath : Path on server to deploy (example : \\\\app-server\\C\\inetpub\\wwwroot\\DEPLOY_HERE)
        excludeFileConfig : File on Path of build release project server will exclude to deploy
    Note: The folder destination must be shared on the network to make this method work
*/
def call(Map config = [:]) {
    script {
            try {
                    bat returnStatus: true, script: "robocopy ${config.buildReleasePath} ${config.deploymentPath} *.* /MIR /XN /XF ${config.excludeFileConfig}"
                }                
            catch (Exception e) {
                echo e
            } 
        }
    }
