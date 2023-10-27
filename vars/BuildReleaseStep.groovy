/**@
    Build project step
    
    Parameters
        executableName : dll file name as entry point
        dockerfile : dockerfile config id (default: dockerfile-fe/dockerfile-be)
        baseHref : base href of FE project (default: /)
        nginxconfig : nginx config id to override default nginx config (default: nginx-fe)
        useNodeTool : use nodejs from environment tool
        //skipBuildEvent: skip dotnet build event (default: false)
*/
def call(Map config = [:]) {
    if (isUnix()) {
        if (config.executableName) {
            //def optionalParams = config.skipBuildEvent ? "/p:PreBuildEvent=" : ""
            sh "DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 $DOTNET/dotnet build -c Release"
            sh "DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 $DOTNET/dotnet publish -c Release --output ./publish/release"
            configFileProvider([configFile(fileId: config.dockerfile ? config.dockerfile: 'dockerfile-be', targetLocation: 'publish/release/Dockerfile', variable: 'dockerfile'), configFile(fileId: 'swagger-xml', targetLocation: "publish/release/${config.executableName}.xml", variable: 'swagger')]) {
                sh "chmod 0777 publish/release/Dockerfile"
                sh "echo ENTRYPOINT [\"\\\"\"dotnet\"\\\"\", \"\\\"\"${config.executableName}.dll\"\\\"\"] >> publish/release/Dockerfile"
                //sh "echo CMD [\"\\\"\"dumb-init\"\\\"\", \"\\\"\"dotnet\"\\\"\", \"\\\"\"${config.executableName}.dll\"\\\"\"] >> publish/release/Dockerfile"
            }
            
            stash includes: 'publish/**', name: 'app'
        } else {
            npm = "npm"
            if (config.useNodeTool == true) {
                npm = "NODE"
            }
            def baseHref = config.baseHref ? config.baseHref: "/"
            sh "node --max_old_space_size=8048 ./node_modules/@angular/cli/bin/ng build --base-href ${baseHref} --deploy-url ${baseHref}"
            configFileProvider([configFile(fileId: config.dockerfile ? config.dockerfile: 'dockerfile-fe', targetLocation: 'dist/Dockerfile', variable: 'dockerfile'), configFile(fileId: config.nginxconfig ? config.nginxconfig: 'nginx-fe', targetLocation: "dist/default.conf", variable: 'nginx')]) {
                sh "echo env copied"
            }
            stash includes: 'dist/**', name: 'app'
        }
    } else {
        if (config.executableName) {
            //def optionalParams = config.skipBuildEvent ? "/p:PreBuildEvent= /p:PostBuildEvent=" : "" 
            bat "dotnet build -c Release"
            bat "dotnet publish -c Release --output ./publish/release"
            configFileProvider([configFile(fileId: config.dockerfile ? config.dockerfile: 'dockerfile-be', targetLocation: 'publish/release/Dockerfile', variable: 'dockerfile'), configFile(fileId: 'swagger-xml', targetLocation: "publish/release/${config.executableName}.xml", variable: 'swagger')]) {
                bat "echo ENTRYPOINT [\"dotnet\", \"${config.executableName}.dll\"] >> publish\\release\\Dockerfile"
            }
            
            stash includes: 'publish/**', name: 'app'
        } else {
            npm = "npm"
            if (config.useNodeTool == true) {
                npm = "$NODE"
            }
            def baseHref = config.baseHref ? config.baseHref: "/"
            bat "${npm}/node --max_old_space_size=8048 ./node_modules/@angular/cli/bin/ng build --base-href ${baseHref} --deploy-url ${baseHref}"
            configFileProvider([configFile(fileId: config.dockerfile ? config.dockerfile: 'dockerfile-fe', targetLocation: 'dist/Dockerfile', variable: 'dockerfile'), configFile(fileId: config.nginxconfig ? config.nginxconfig: 'nginx-fe', targetLocation: "dist/default.conf", variable: 'nginx')]) {
                bat "echo env copied"
            }
            stash includes: 'dist/**', name: 'app'
        }
    }
}
