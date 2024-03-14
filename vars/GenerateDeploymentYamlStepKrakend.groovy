/**@
    Generate deployment yaml file for kubernetes deployment

    parameters : 
        type : Project type (fe/be)
        deploymentName : Kubernetes deployment/workload name
        namespace : Namespace of workload to deploy
        imageName : Image name to use in container

        # Deployment
        configMapFileName : Configuration file name in container
        configContainerPath (optional) : Path of full path config in container

        # Service
        port : Port to expose in service
        targetPort : Container forwarded port
        serviceType : Kube Service Type
        
        # Configmap
        configPath : Config file name to store in configmap
    env : Config enviroment project
        
*/

import org.yaml.snakeyaml.Yaml
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.JsonBuilder

def call(Map config = [:]) {
    if (isUnix()) {
        configFileProvider([configFile(fileId: 'kube-deployment-yaml', targetLocation: './deployment.yaml', variable: 'deployment'), configFile(fileId: 'kube-service-yaml', targetLocation: './service.yaml', variable: 'service'), configFile(fileId: 'kube-configmap-yaml', targetLocation: './configmap.yaml', variable: 'configmap')]) 
        {
            // Namespace
            Map namespace = [apiVersion: "v1", kind: "Namespace", metadata: [name: config.namespace]]
            if (!fileExists('namespace.yaml')) {
                writeYaml(data: namespace, file: "namespace.yaml")
            }

            config.imageName = config.imageName.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")

            // Deployment
            def deployment = readYaml(file: 'deployment.yaml')
            deployment.metadata.name = config.deploymentName
            deployment.metadata.namespace = config.namespace
            deployment.metadata.labels.app = config.deploymentName
            deployment.spec.selector.matchLabels.app = config.deploymentName
            deployment.spec.template.metadata.labels.app = config.deploymentName
            deployment.spec.template.spec.volumes[0].name = """${config.deploymentName}-volume"""
            deployment.spec.template.spec.volumes[0].configMap.name = """${config.deploymentName}-appsettings"""
            deployment.spec.template.spec.containers[0].name = config.deploymentName
            deployment.spec.template.spec.containers[0].image = config.imageName
            deployment.spec.template.spec.containers[0].volumeMounts[0].name = """${config.deploymentName}-volume"""
            deployment.spec.template.spec.containers[0].volumeMounts[0].mountPath = "/etc/krakend/${config.configMapFileName}"
            deployment.spec.template.spec.containers[0].volumeMounts[0].subPath = config.configMapFileName
            deployment.spec.template.spec.containers[0].ports[0].containerPort = 8080
            deployment.spec.template.spec.containers[0].ports[1].containerPort = 5000

            sh "rm ./deployment.yaml"
            writeYaml(data: deployment, file: "deployment.yaml")

            // Service
            def service = readYaml(file: 'service.yaml')
            service.metadata.name = config.deploymentName
            service.metadata.namespace = config.namespace
            service.spec.selector.app = config.deploymentName
            service.spec.ports[0].port = config.krakendPort
            service.spec.ports[0].targetPort = 8080
            service.spec.ports[0].port = config.appPort
            service.spec.ports[0].targetPort = 5000
            service.spec.type = config.serviceType

            sh "rm ./service.yaml"
            writeYaml(data: service, file: "service.yaml")

            // ConfigMap
            def configmap = readYaml(file: 'configmap.yaml')
            configmap.metadata.name = """${config.deploymentName}-appsettings"""
            configmap.metadata.namespace = config.namespace
            def data = config.configPath ? readFile(config.configPath): "{}"
            Map configData = [(config.configMapFileName): data]
            configmap.data = configData

            // Mengubah JSON kembali menjadi string
            data = JsonOutput.toJson(jsonAppSetting)
            }
            else if (config.type == 'fe')
            {
                def jsonString = data
                def jsonSetting = readFile(file: 'GeneralConfig_FrontEnd.json')
                
                // Membaca JSON
                jsonString = jsonString.replace("\\", "/")
                jsonSetting = jsonSetting.replace("\\", "/")
                
                //hapus comment di json
                def jsonAppSettingWithoutComments = jsonString.replaceAll(/\/\*(?:[^*]|(?:\*+[^*\/]))*\*\//, '')
                def jsonConfSettingWithoutComments = jsonSetting.replaceAll(/\/\*(?:[^*]|(?:\*+[^*\/]))*\*\//, '')
                
                def jsonAppSetting = new JsonSlurper().parseText(jsonAppSettingWithoutComments)
                def jsonConfSetting = new JsonSlurper().parseText(jsonConfSettingWithoutComments)
                
                // Ubah URL FrontEnd
                for(KeyNameSetting in jsonAppSetting) {
                 targetField = KeyNameSetting.key;
                 if (targetField in jsonConfSetting.keySet())
                 {
                     jsonAppSetting."$targetField" = jsonConfSetting."$targetField"                 
                 }
                }
                
                // Mengubah JSON kembali menjadi string
                data = JsonOutput.toJson(jsonAppSetting)
            }
            else if(config.configMapFileName == 'application.yaml')
            {

                def StringYaml = data
                def jsonSetting = readFile(file: 'GeneralConfig.json')                
                def yaml = new Yaml()
                def jsonAppSetting = yaml.load(StringYaml)                
                def jsonConfSetting = new JsonSlurper().parseText(jsonSetting)
              
                // Ubah URL FrontEnd
                if("camunda" in jsonAppSetting.keySet()) {
                     jsonAppSetting."foundation-url" = jsonConfSetting."Camunda"."foundation-url"
                     jsonAppSetting."data-base-type" = jsonConfSetting."Camunda"."data-base-type"
                     jsonAppSetting.spring.datasource.username = jsonConfSetting."Camunda"."username"
                     jsonAppSetting.spring.datasource.password = jsonConfSetting."Camunda"."password"
                     jsonAppSetting.spring.datasource.url = jsonConfSetting."Camunda"."url"

                     if (jsonConfSetting."Camunda"."data-base-type" == "SSMS")
                     {
                         jsonAppSetting.spring.datasource."driver-class-name" = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                         jsonAppSetting.spring.jpa."database-platform" = "org.hibernate.dialect.SQLServerDialect"
                     }
                     else if (jsonConfSetting."Camunda"."data-base-type" == "POSTGRESQL")
                     {
                         jsonAppSetting.spring.datasource."driver-class-name" = "org.postgresql.Driver"
                         jsonAppSetting.spring.jpa."database-platform" = "org.hibernate.dialect.PostgreSQL9Dialect"
                     }
                }
                
                // Mengubah JSON kembali menjadi string
                data = yaml.dump(jsonAppSetting)
            }
            
            Map configData = [(config.configMapFileName): data]
            configmap.data = configData

            sh "rm ./configmap.yaml"
            writeYaml(data: configmap, file: "configmap.yaml")

            sh "cat ./deployment.yaml"
            sh "cat ./service.yaml"
            sh "cat ./configmap.yaml"

            // Simpan objek gabungan ke dalam file baru
            writeYaml(datas: [deployment, service, configmap], file: "deploymentservice.yaml")
                            
            sh "cat ./deploymentservice.yaml"
            
            }
        }
}

// Fungsi untuk memecah string menjadi pasangan kunci-nilai
def parseKeyValuePairs(DBString) {
    def DBStringSplitData = DBString.split(';')
    def keyValuePairs = [:]

    DBStringSplitData.each { pair ->
    def keyValue = pair.split('=')
    def key = keyValue[0].trim()
    def value = keyValue[1].trim()
    keyValuePairs[key] = value
    }
    
    return keyValuePairs    
}
