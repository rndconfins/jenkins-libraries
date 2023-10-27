/**@
    Clean project

    parameters : 
        type : project type (fe/be)
        authToken : npm auth token
        useNodeTool : use npm predefined tool in environment (default: false)
*/
def call(Map config = [:]) {
    if (isUnix()) {
        if (config.type == "be") {
            sh "$DOTNET/dotnet clean"
        } else  {
            npm = "npm"
            if (config.useNodeTool == true) {
                npm = "$NODE/npm"
            }
            sh "${npm} config set //registry.npmjs.org/:_authToken ${config.authToken}"
            sh "${npm} install node-sass@4.14.1"
            sh "${npm} install crypto-js"
            sh "${npm} install --prefer-offline --legacy-peer-deps --no-audit --progress=false"
        }
    } else  {
        if (config.type == "be") {
            bat "dotnet clean"
        } else  {
            npm = "npm"
            if (config.useNodeTool == true) {
                npm = "%NODE%\\npm"
            }
            bat "${npm} config set //registry.npmjs.org/:_authToken ${config.authToken}"
            bat "${npm} install node-sass@4.14.1"
            bat "${npm} install crypto-js"
            bat "${npm} install --prefer-offline --legacy-peer-deps --no-audit --progress=false"
        }
    }
}