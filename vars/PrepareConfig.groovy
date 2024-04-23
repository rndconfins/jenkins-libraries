/**@
    Build project step
    
    Parameters
        type : use to determine whether be or fe (be, fe)
        useGeneralConfig : use to determine whether the fe use general config or not 

*/
def call(Map config = [:]) {
    if (config.type == "be") {
      configFileProvider([configFile(fileId: 'dockerfile-be', targetLocation: 'Dockerfile', variable: 'dockerfile')]) 
		  {
                    echo 'Env Copied'
      }
    }
    else if (config.type == "fe") {

      if(config.useGeneralConfig == true) {
          configFileProvider(
          [configFile(fileId: 'GeneralConfig_FrontEnd', targetLocation: './src/assets/config/enviConfig.json', variable: 'enviConfig')]) {
          echo 'Env Copied'
          }
      }
      
      configFileProvider(
          [configFile(fileId: 'dockerfile-fe', targetLocation: 'Dockerfile', variable: 'dockerfile'),
      configFile(fileId: 'nginx-fe', targetLocation: 'default.conf', variable: 'nginx')]) {
          echo 'Env Copied'
      }
    }
}
