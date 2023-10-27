/**@
    Deploy to server step

    parameters :
        credentialsId (username&password): Credentials id used to login to server
        driveLetter : Drive Letter to use to mount deployment path
        buildReleasePath : Path of build release project (release/dist)
        deploymentPath : Path on server to deploy (example : \\\\app-server\\C\\inetpub\\wwwroot\\DEPLOY_HERE)
*/
def call(Map config = [:]) {
    script {
        withCredentials([usernamePassword(credentialsId: "${config.credentialsId}", passwordVariable: 'PASS', usernameVariable: 'USER')]) {
            try {
                bat "net use ${config.driveLetter}: ${config.deploymentPath} $PASS /user:$USER"
                bat "net use ${config.driveLetter}: /delete"
            } catch (Exception ex) {
                bat "net use ${config.driveLetter}: /delete"
            }

            try {
                bat "net use ${config.driveLetter}: ${config.deploymentPath} $PASS /user:$USER"
                bat returnStatus: true, script: "robocopy ${config.buildReleasePath} ${config.driveLetter}:/ *.* /MIR"
            } catch (Exception e) {
                echo e
            } finally {
                bat "net use ${config.driveLetter}: /delete"
            }
        }
    }
}