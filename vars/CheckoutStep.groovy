/**@
    Checkout step from version control

    parameters :
        credentialsId (username&password): credentials id for checkout/pulling repo from remote
        serverUrl : TFS collection URL
        projectPath : TFS project path
        url : GIT repo URL
        branchName : GIT branch name to pull
        workspaceName : custom workspace name (default: Hudson-${JOB_NAME}-${NODE_NAME}-CLIENT)
*/
def call(Map config = [:]) {
    if (config.cleanWorkspace) {
        cleanWs()
    }
    withCredentials([usernamePassword(credentialsId: config.credentialsId, passwordVariable: 'PASS', usernameVariable: 'USER')]) {
        if (config.branchName) {
            checkout([$class: 'GitSCM', branches: [[name: config.branchName]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: config.credentialsId, url: config.url]]])
        } else {
            checkout([
                $class: 'TeamFoundationServerScm', projectPath: config.projectPath, serverUrl: config.serverUrl, useOverwrite: true, useUpdate: true, userName: "$USER", password: hudson.util.Secret.fromString("$PASS"), workspaceName: config.workspaceName 
 ? config.workspaceName: 'Hudson-${JOB_NAME}-${NODE_NAME}-CLIENT'
            ])
        }
    }
    if (!isUnix()) {
        powershell "md -Force publish"
        dir("./publish") {
            if (!fileExists('.gitignore')) {
                echo "Init Repo"
                bat "%SCRIPTS%\\psrun.bat engine-init-repo.ps1 ./"
            }
        }
    }
}