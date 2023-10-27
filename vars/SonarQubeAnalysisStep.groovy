/**@
    SonarQube analysis step

    parameters :
        sonarQubeEnv : SonarQube environment name
        credentialsId (string): SonarQube credentialsId for login to SonarQube (sonar token)
        type : Project type (fe/be)
        projectKey : SonarQube Project key
*/
def call(Map config = [:]) {
    withSonarQubeEnv(config.sonarQubeEnv) {
        withCredentials([string(credentialsId: config.credentialsId, variable: 'TOKEN')]) {
            if (isUnix()) {
                if (config.type == "be") {
                    sh "DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 $DOTNET/dotnet $SONAR/SonarScanner.MSBuild.dll begin /key:${config.projectKey} /d:sonar.login=$TOKEN"
                    sh "DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 $DOTNET/dotnet build"
                    sh "DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 $DOTNET/dotnet $SONAR/SonarScanner.MSBuild.dll end /d:sonar.login=$TOKEN"
                } else {
                    sh "$SONAR/bin/sonar-scanner -Dsonar.projectKey=${config.projectKey} -Dsonar.login=$TOKEN"
                }
            } else {
                if (config.type == "be") {
                    bat "dotnet %TOOLS%\\hudson.plugins.sonar.MsBuildSQRunnerInstallation\\SonarScanner_.Net_Framework_Core\\SonarScanner.MSBuild.dll begin /key:${config.projectKey} /d:sonar.login=%TOKEN%"
                    bat 'dotnet build'
                    bat "dotnet %TOOLS%\\hudson.plugins.sonar.MsBuildSQRunnerInstallation\\SonarScanner_.Net_Framework_Core\\SonarScanner.MSBuild.dll end /d:sonar.login=%TOKEN%"
                } else {
                    bat "%TOOLS%\\hudson.plugins.sonar.SonarRunnerInstallation\\SonarQube_Scanner\\bin\\sonar-scanner.bat -Dsonar.projectKey=${config.projectKey} -Dsonar.login=%TOKEN%"
                }
            }
        }
    }
}