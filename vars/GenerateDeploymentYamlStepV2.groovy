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
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def call(Map config = [:]) {
    if (isUnix()) {
            configFileProvider([configFile(fileId: 'kube-deployment-yaml', targetLocation: './deployment.yaml', variable: 'deployment'), configFile(fileId: 'kube-service-yaml', targetLocation: './service.yaml', variable: 'service'), configFile(fileId: 'kube-configmap-yaml', targetLocation: './configmap.yaml', variable: 'configmap'), configFile(fileId: 'GeneralConfig', targetLocation: './GeneralConfig.json', variable: 'GeneralConfig'), configFile(fileId: 'GeneralConfig_FrontEnd', targetLocation: './GeneralConfig_FrontEnd.json', variable: 'GeneralConfig_FrontEnd')]) {
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

            if (config.type != 'fe' && config.configMapFileName == 'appsettings.json')
            {
                def jsonString = data
                def jsonSetting = readFile(file: 'GeneralConfig.json')
                
                // Membaca JSON
                jsonString = jsonString.replace("\\", "/")
                
                //hapus comment di json
                def jsonStringWithoutComments = jsonString.replaceAll(/\/\*(?:[^*]|(?:\*+[^*\/]))*\*\//, '')
                
                def jsonAppSetting = new JsonSlurper().parseText(jsonStringWithoutComments)
                def jsonConfSetting = new JsonSlurper().parseText(jsonSetting)
                
                //Ubah Logging
                if("Logging" in jsonAppSetting."ConnectionStrings".keySet())
                {
                    if(jsonConfSetting."Logging"."DataBaseType" == "POSTGRESQL")
                    {
                        def DBString = jsonAppSetting."ConnectionStrings"."Logging"."DataBasePostgreSQL"
			def keyValuePairs = parseKeyValuePairs(DBString)
                        
                        keyValuePairs['Host'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Host"
                        keyValuePairs['User ID'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."User ID"
                        keyValuePairs['Password'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Password"
                        keyValuePairs['Port'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Port"
                        keyValuePairs['Database'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."DatabaseName"
                        
                        def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                        
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBaseType" = jsonConfSetting."Logging"."DataBaseType"
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBasePostgreSQL" = data2
                    }
                    else if(jsonConfSetting."Logging"."DataBaseType" == "SSMS")
                    {
                        def DBString = jsonAppSetting."ConnectionStrings"."Logging"."DataBaseSSMS"
			def keyValuePairs = parseKeyValuePairs(DBString)
                        
                        keyValuePairs['Server'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Server"
                        keyValuePairs['User ID'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."User ID"
                        keyValuePairs['Password'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Password"
                        keyValuePairs['Database'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."DatabaseName"
                        
                        def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                        
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBaseType" = jsonConfSetting."Logging"."DataBaseType"
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBaseSSMS" = data2
                    }
                }

                //Ubah LicenseConfig
                if("LicenseConfig" in jsonAppSetting.keySet())
                {
                    if (jsonConfSetting."LicenseConfig"."DataBaseType" == "POSTGRESQL")
                    {
                        if('DataBasePostgreSQL' in jsonAppSetting."LicenseConfig")
                        {
                            def DBString = jsonAppSetting."LicenseConfig"."DataBasePostgreSQL"
			    def keyValuePairs = parseKeyValuePairs(DBString)
                                                        
                            keyValuePairs['Host'] = jsonConfSetting."LicenseConfig"."DataBasePostgreSQL"."Host"
                            keyValuePairs['User ID'] = jsonConfSetting."LicenseConfig"."DataBasePostgreSQL"."User ID"
                            keyValuePairs['Password'] = jsonConfSetting."LicenseConfig"."DataBasePostgreSQL"."Password"
                            keyValuePairs['Port'] = jsonConfSetting."LicenseConfig"."DataBasePostgreSQL"."Port"
                            keyValuePairs['Database'] = jsonConfSetting."LicenseConfig"."DataBasePostgreSQL"."DatabaseName"
                            
                            def data3 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                            
                            jsonAppSetting."LicenseConfig"."DataBaseType" = jsonConfSetting."LicenseConfig"."DataBaseType"
                            jsonAppSetting."LicenseConfig"."DataBasePostgreSQL" = data3
                        }
                        
                    }
                    else if(jsonConfSetting."LicenseConfig"."DataBaseType" == "SSMS")
                    {
                        if('DataBaseSSMS' in jsonAppSetting."LicenseConfig")
                        {
                            def DBString = jsonAppSetting."LicenseConfig"."DataBaseSSMS"
			    def keyValuePairs = parseKeyValuePairs(DBString)
                            
                            keyValuePairs['Server'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."Server"
                            keyValuePairs['User ID'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."User ID"
                            keyValuePairs['Password'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."Password"
                            keyValuePairs['Database'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."DatabaseName"
                            
                            def data3 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                            
                            jsonAppSetting."LicenseConfig"."DataBaseType" = jsonConfSetting."LicenseConfig"."DataBaseType"
                            jsonAppSetting."LicenseConfig"."DataBaseSSMS" = data3
                        }
                        else
                        {
                            def DBString = jsonAppSetting."LicenseConfig"."DataBase"
			    def keyValuePairs = parseKeyValuePairs(DBString)
                            
                            keyValuePairs['Server'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."Server"
                            keyValuePairs['User ID'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."User ID"
                            keyValuePairs['Password'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."Password"
                            keyValuePairs['Database'] = jsonConfSetting."LicenseConfig"."DataBaseSSMS"."DatabaseName"
                            
                            def data3 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                            jsonAppSetting."LicenseConfig"."DataBase" = data3
                        }
                    }
                    
                    jsonAppSetting."LicenseConfig"."EnableLicense" = jsonConfSetting."LicenseConfig"."EnableLicense"
                }
                
                // Ubah Database
                for(DatabaseName in jsonAppSetting."ConnectionStrings") {
                 targetField = DatabaseName.key;
                 if (jsonConfSetting."Database"."DataBaseType" == "POSTGRESQL") {
			def postgresConfig = jsonConfSetting."Database"."DataBasePostgreSQL"
			if (DatabaseName.key in postgresConfig."DatabaseName".keySet()) {
				for (itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField") {
					if (itemsSetting.key == "DataBasePostgreSQL") {
						def DBString = itemsSetting.value
						def keyValuePairs = parseKeyValuePairs(DBString)
												
						keyValuePairs['Host'] = postgresConfig."Host"
						keyValuePairs['User ID'] = postgresConfig."User ID"
						keyValuePairs['Port'] = postgresConfig."Port"
						keyValuePairs['Password'] = postgresConfig."Password"
						keyValuePairs['Database'] = postgresConfig."DatabaseName"."$targetField"
						
						def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
						jsonAppSetting."ConnectionStrings"."$targetField"."DataBasePostgreSQL" = data2
					} else if (itemsSetting.key == "SQLConnSPPostgreSQL" || itemsSetting.key == "POSTGRESQLConnSP" || itemsSetting.key.contains("TableAdapters")) {
						def DBString = itemsSetting.value
						def keyValuePairs = parseKeyValuePairs(DBString)
												
						keyValuePairs['Data Source'] = postgresConfig."Server"
						keyValuePairs['user id'] = postgresConfig."User ID"
						keyValuePairs['Password'] = postgresConfig."Password"

						if (itemsSetting.key.contains("TableAdapters")) {
							keyValuePairs['Initial Catalog'] = postgresConfig."DatabaseName"."$targetField"."$itemsSetting.key"
						} else {
							keyValuePairs['Initial Catalog'] = postgresConfig."DatabaseName"."$targetField"
						}

						def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
						
						if (itemsSetting.key.contains("TableAdapters")) {
							jsonAppSetting."ConnectionStrings"."$targetField"."$itemsSetting.key" = data2
						} else {
							jsonAppSetting."ConnectionStrings"."$targetField"."$itemsSetting.key" = data2
						}
					}
				}
			}
		}
                 else if (jsonConfSetting."Database"."DataBaseType" == "SSMS")
                 {
                     if('DataBaseSSMS' in jsonAppSetting."ConnectionStrings"."$targetField")
                     {
                         if (DatabaseName.key in jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName".keySet())
                         {
                            for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                            {
                                if(itemsSetting.key == 'DataBaseSSMS')
                                {
				    def DBString = itemsSetting.value
				    def keyValuePairs = parseKeyValuePairs(DBString)
                                    
                                    keyValuePairs['Server'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
                                    keyValuePairs['User ID'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
                                    keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
                                    keyValuePairs['Database'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
                                    
                                    def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                    jsonAppSetting."ConnectionStrings"."$targetField"."DataBaseSSMS" = data2
                                    
                                }
                                else if(itemsSetting.key == 'SQLConnSPSSMS' || itemsSetting.key.contains("TableAdapters"))
                                {
	                                def DBString = itemsSetting.value
					def keyValuePairs = parseKeyValuePairs(DBString)
	                                    
	                                keyValuePairs['Data Source'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
	                                keyValuePairs['user id'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
	                                keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
					
					if (itemsSetting.key.contains("TableAdapters")) {
						keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"."$itemsSetting.key"
					} else {
						keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
					}
	
					def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
					
					if (itemsSetting.key.contains("TableAdapters")) {
						jsonAppSetting."ConnectionStrings"."$targetField"."$itemsSetting.key" = data2
					} else {
						jsonAppSetting."ConnectionStrings"."$targetField"."SQLConnSPSSMS" = data2
					}  
                                }                                
                            }                         
                        }
                     }
                     else
                     {
                         if (DatabaseName.key in jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName".keySet())
                         {
                            for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                            {
                                if(itemsSetting.key == 'DataBase')
                                {
                                    def DBString = itemsSetting.value
				    def keyValuePairs = parseKeyValuePairs(DBString)
                                    
                                    keyValuePairs['Server'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
                                    keyValuePairs['User ID'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
                                    keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
                                    keyValuePairs['Database'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"                                    
                                    
                                    def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')                                    
                                    jsonAppSetting."ConnectionStrings"."$targetField"."DataBase" = data2                                    
                                    
                                }
                                else if(itemsSetting.key == 'SQLConnSP' || itemsSetting.key.contains("TableAdapters"))
                                {
	                                def DBString = itemsSetting.value
					def keyValuePairs = parseKeyValuePairs(DBString)
	                                    
	                                keyValuePairs['Data Source'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
	                                keyValuePairs['user id'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
	                                keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"

					if (itemsSetting.key.contains("TableAdapters")) {
						keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"."$itemsSetting.key"
					} else {
						keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
					}
	
					def data2 = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
					
					if (itemsSetting.key.contains("TableAdapters")) {
						jsonAppSetting."ConnectionStrings"."$targetField"."$itemsSetting.key" = data2
					} else {
						jsonAppSetting."ConnectionStrings"."$targetField"."SQLConnSP" = data2
					}  
                                }
                            }
                        }
                     }                 
                 }                 
                }
                
                //Ubah URL
                for(BeUrlName in jsonAppSetting."ConnectionStrings") {
                 if (BeUrlName.key in jsonConfSetting."URL".keySet())
                 {                     
                    targetField = BeUrlName.key;
                     
                    for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                    {
                        if(itemsSetting.key == 'URL')
                        {
                            def UrlString = jsonConfSetting."URL"."$targetField"
                            jsonAppSetting."ConnectionStrings"."$targetField"."URL" = UrlString
                        }
                    }                     
                 }
                }
                
                //Ubah Connection String Redis
                jsonAppSetting."RedisConfig"."ConnStringRedis" = jsonConfSetting."Redis"."ConnStringRedis"

                //Ubah Connection Queue
                if("Queue" in jsonAppSetting.keySet())
                {
                    if (jsonConfSetting."Queue"."Provider" == "RMQ")
                    {
                        if("RMQSettings" in jsonAppSetting.keySet())
                        {
                            jsonAppSetting."Queue"."Provider" = jsonConfSetting."Queue"."Provider"
                            jsonAppSetting."RMQSettings"."RMQConnect"."Username" = jsonConfSetting."Queue"."RMQSettings"."UserName"
                            jsonAppSetting."RMQSettings"."RMQConnect"."Password" = jsonConfSetting."Queue"."RMQSettings"."Password"
                            jsonAppSetting."RMQSettings"."RMQConnect"."VirtualHostName" = jsonConfSetting."Queue"."RMQSettings"."VHost"
                            jsonAppSetting."RMQSettings"."RMQConnect"."QueueName" = jsonConfSetting."Queue"."RMQSettings"."Name"
                            jsonAppSetting."RMQSettings"."RMQConnect"."Endpoints" = jsonConfSetting."Queue"."RMQSettings"."HostName"
                            jsonAppSetting."RMQSettings"."RMQConnect"."Port" = jsonConfSetting."Queue"."RMQSettings"."Port"                            
                        }
			    
                        if("HostName" in jsonAppSetting."Queue".keySet())
                        {
                            jsonAppSetting."Queue"."Provider" = jsonConfSetting."Queue"."Provider"
                            jsonAppSetting."Queue"."IsUseIntegration" = jsonConfSetting."Queue"."RMQSettings"."IsUseIntegration"
                            jsonAppSetting."Queue"."UserName" = jsonConfSetting."Queue"."RMQSettings"."UserName"
                            jsonAppSetting."Queue"."Password" = jsonConfSetting."Queue"."RMQSettings"."Password"
                            jsonAppSetting."Queue"."VHost" = jsonConfSetting."Queue"."RMQSettings"."VHost"
                            jsonAppSetting."Queue"."Name" = jsonConfSetting."Queue"."RMQSettings"."Name"
                            jsonAppSetting."Queue"."HostName" = jsonConfSetting."Queue"."RMQSettings"."HostName"
                            jsonAppSetting."Queue"."Port" = jsonConfSetting."Queue"."RMQSettings"."Port"
                            jsonAppSetting."Queue"."MaxRetry" = jsonConfSetting."Queue"."RMQSettings"."MaxRetry"
                        }                        
                    }
                    else if (jsonConfSetting."Queue"."Provider" == "MNS")
                    {
                        if("AlibabaMnsSettings" in jsonAppSetting.keySet())
                        {
                            jsonAppSetting."Queue"."Provider" = jsonConfSetting."Queue"."Provider"
                            jsonAppSetting."AlibabaMnsSettings"."RegionId" = jsonConfSetting."Queue"."AlibabaMnsSettings"."RegionId"
                            jsonAppSetting."AlibabaMnsSettings"."Endpoint" = jsonConfSetting."Queue"."AlibabaMnsSettings"."Endpoint"
                            jsonAppSetting."AlibabaMnsSettings"."AccessKeyId" = jsonConfSetting."Queue"."AlibabaMnsSettings"."AccessKeyId"
                            jsonAppSetting."AlibabaMnsSettings"."AccessKeySecret" = jsonConfSetting."Queue"."AlibabaMnsSettings"."AccessKeySecret"
                        }                        
                    }
                    else if (jsonConfSetting."Queue"."Provider" == "SQS")
                    {
                        if("AlibabaMnsSettings" in jsonAppSetting.keySet())
                        {
                            jsonAppSetting."Queue"."Provider" = jsonConfSetting."Queue"."Provider"
                            jsonAppSetting."AmazonSqsSettings"."Region" = jsonConfSetting."Queue"."AmazonSqsSettings"."Region"
                            jsonAppSetting."AmazonSqsSettings"."ARN" = jsonConfSetting."Queue"."AmazonSqsSettings"."ARN"
                            jsonAppSetting."AmazonSqsSettings"."Endpoint" = jsonConfSetting."Queue"."AmazonSqsSettings"."Endpoint"
                            jsonAppSetting."AmazonSqsSettings"."AccessKeyId" = jsonConfSetting."Queue"."AmazonSqsSettings"."AccessKeyId"
                            jsonAppSetting."AmazonSqsSettings"."AccessKeySecret" = jsonConfSetting."Queue"."AmazonSqsSettings"."AccessKeySecret"
                        }                        
                    }                    
                }
                else if("RMQSettings" in jsonAppSetting.keySet())
                {
                    jsonAppSetting."RMQSettings"."RMQConnect"."Endpoints" = jsonConfSetting."Queue"."RMQSettings"."HostName"
                    jsonAppSetting."RMQSettings"."RMQConnect"."Username" = jsonConfSetting."Queue"."RMQSettings"."UserName"
                    jsonAppSetting."RMQSettings"."RMQConnect"."Password" = jsonConfSetting."Queue"."RMQSettings"."Password"
                    jsonAppSetting."RMQSettings"."RMQConnect"."VirtualHostName" = jsonConfSetting."Queue"."RMQSettings"."VHost"
                    jsonAppSetting."RMQSettings"."RMQConnect"."Port" = jsonConfSetting."Queue"."RMQSettings"."Port"
                } 

		//Ubah Report Settings
		if("ReportSettings" in jsonAppSetting.keySet())
                {
			jsonAppSetting."ReportSettings"."ProviderType" = jsonConfSetting."ReportSettings"."ProviderType"
			jsonAppSetting."ReportSettings"."TemplatePath" = jsonConfSetting."ReportSettings"."TemplatePath"
			jsonAppSetting."ReportSettings"."LibraryPath" = jsonConfSetting."ReportSettings"."LibraryPath"
			jsonAppSetting."ReportSettings"."StartingTemplatePath" = jsonConfSetting."ReportSettings"."StartingTemplatePath"
			jsonAppSetting."ReportSettings"."GeneratingAsyncReportPath" = jsonConfSetting."ReportSettings"."GeneratingAsyncReportPath"
			jsonAppSetting."ReportSettings"."GeneratingScheduleReportPath" = jsonConfSetting."ReportSettings"."GeneratingScheduleReportPath"
		}
                
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

            //sh "type ./deployment.yaml"
            //sh "type ./service.yaml"
            //sh "type ./configmap.yaml"

            // Simpan objek gabungan ke dalam file baru
            writeYaml(datas: [deployment, service, configmap], file: "deploymentservice.yaml")
                            
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
