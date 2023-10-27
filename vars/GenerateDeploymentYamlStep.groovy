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
*/

import org.yaml.snakeyaml.Yaml

def call(Map config = [:]) {
    if (isUnix()) {
            configFileProvider([configFile(fileId: 'kube-deployment-yaml', targetLocation: './deployment.yaml', variable: 'deployment'), configFile(fileId: 'kube-service-yaml', targetLocation: './service.yaml', variable: 'service'), configFile(fileId: 'kube-configmap-yaml', targetLocation: './configmap.yaml', variable: 'configmap')]) {
            def matchers = ~ /.*-(frontend|fe)/
            config.type = config.type ? config.type: (matchers.matcher(config.deploymentName).matches() ? "fe": "be")

            // Namespace
            Map namespace = [apiVersion: "v1", kind: "Namespace", metadata: [name: config.namespace]]
            if (!fileExists('namespace.yaml')) {
                writeYaml(data: namespace, file: "namespace.yaml")
            }

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
            deployment.spec.template.spec.containers[0].volumeMounts[0].mountPath = (config.configContainerPath ? config.configContainerPath: (config.type == 'fe' ? "/usr/share/nginx/html/assets/config/${config.configMapFileName}": "/app/${config.configMapFileName}"))
            deployment.spec.template.spec.containers[0].volumeMounts[0].subPath = config.configMapFileName

            sh "rm ./deployment.yaml"
            writeYaml(data: deployment, file: "deployment.yaml")

            // Service
            def service = readYaml(file: 'service.yaml')
            service.metadata.name = config.deploymentName
            service.metadata.namespace = config.namespace
            service.spec.selector.app = config.deploymentName
            service.spec.ports[0].port = config.port
            service.spec.ports[0].targetPort = config.targetPort
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


            sh "rm ./configmap.yaml"
            writeYaml(data: configmap, file: "configmap.yaml")

            //sh "type ./deployment.yaml"
            //sh "type ./service.yaml"
            //sh "type ./configmap.yaml"
            
            //def deployment2 = new File('./deployment.yaml')
            //def deployment2 = readYaml(file: 'deployment.yaml')
            //def datadeployment = deployment2.text
                
            //def service2 = new File('./service.yaml')
            //def service2 = readYaml(file: 'service.yaml')
            //def dataservice = service2.text
                
            //def configmap2 = new File('./configmap.yaml')
            //def configmap2 = readYaml(file: 'configmap.yaml')
            //def dataconfigmap = configmap2.text
            
            // Gabungkan konten kedua file menjadi satu teks
            //def merged_data = "${deployment}\n ------- \n${service}\n ------- \n${configmap}"

            // Konversi teks gabungan menjadi objek YAML
            //def yaml = new Yaml()
            //def obj = yaml.load(merged_data)

            // Simpan objek gabungan ke dalam file baru
            writeYaml(datas: [deployment, service, configmap], file: "deploymentservice.yaml")
            //def output = new File('deploymentservice.yaml')
            //yaml.dump(obj, output.newWriter())
                
            //sh "type ./deploymentservice.yaml"
            
            }
        } else {
            configFileProvider([configFile(fileId: 'kube-deployment-yaml', targetLocation: './deployment.yaml', variable: 'deployment'), configFile(fileId: 'kube-service-yaml', targetLocation: './service.yaml', variable: 'service'), configFile(fileId: 'kube-configmap-yaml', targetLocation: './configmap.yaml', variable: 'configmap')]) {
            def matchers = ~ /.*-(frontend|fe)/
            config.type = config.type ? config.type: (matchers.matcher(config.deploymentName).matches() ? "fe": "be")

            // Namespace
            Map namespace = [apiVersion: "v1", kind: "Namespace", metadata: [name: config.namespace]]
            if (!fileExists('namespace.yaml')) {
                writeYaml(data: namespace, file: "namespace.yaml")
            }

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
            deployment.spec.template.spec.containers[0].volumeMounts[0].mountPath = (config.configContainerPath ? config.configContainerPath: (config.type == 'fe' ? "/usr/share/nginx/html/assets/config/${config.configMapFileName}": "/app/${config.configMapFileName}"))
            deployment.spec.template.spec.containers[0].volumeMounts[0].subPath = config.configMapFileName

            bat "del deployment.yaml"
            writeYaml(data: deployment, file: "deployment.yaml")

            // Service
            def service = readYaml(file: 'service.yaml')
            service.metadata.name = config.deploymentName
            service.metadata.namespace = config.namespace
            service.spec.selector.app = config.deploymentName
            service.spec.ports[0].port = config.port
            service.spec.ports[0].targetPort = config.targetPort
            service.spec.type = config.serviceType

            bat "del service.yaml"
            writeYaml(data: service, file: "service.yaml")

            // ConfigMap
            def configmap = readYaml(file: 'configmap.yaml')
            configmap.metadata.name = """${config.deploymentName}-appsettings"""
            configmap.metadata.namespace = config.namespace
            def data = config.configPath ? readFile(config.configPath): "{}"
            Map configData = [(config.configMapFileName): data]
            configmap.data = configData


            bat "del configmap.yaml"
            writeYaml(data: configmap, file: "configmap.yaml")

            bat "type deployment.yaml"
            bat "type service.yaml"
            bat "type configmap.yaml"
           }
        }
}
