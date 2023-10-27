/**@
    Deploy application workload to kubernetes

    parameters : 
        credentialsId : Credentials id for login to kubernetes cluster
        cloudType : Cloud provider (Google Cloud, AWS CLI)
        zone : (Google Cloud) zone id
        clusterName : Kubernetes cluster name
        serviceAccountName : (Google Cloud) IAM service account name for kubernetes deployment
        regionId : (AWS CLI) EKS region id
        resetConfigmap: reset configmap and all deployment to default value (default: false)
        autoRestart : Auto restart deployment after deploy

    credentialsId :
        Google Coud : file
        AWS CLI : username&password
        
*/
def call(Map config = [:]) {
    if (config.cloudType) {
        if (config.cloudType == "Google Cloud") {
            withCredentials([file(credentialsId: "${config.credentialsId}", variable: 'FILE')]) {
                sh "gcloud auth activate-service-account ${config.serviceAccountName} --key-file=%FILE%"
                sh "gcloud container clusters get-credentials ${config.clusterName} --zone ${config.zone}"
            }
        } else if (config.cloudType == "AWS CLI") {
            if (!env.AWS_DEFAULT_REGION) {
                env.AWS_DEFAULT_REGION = config.regionId
            }
            withCredentials([usernamePassword(credentialsId: "${config.credentialsId}", passwordVariable: 'SECRET', usernameVariable: 'KEY')]) {
                sh "aws configure set aws_access_key_id $KEY"
                sh "aws configure set aws_secret_access_key $SECRET"
                sh "aws eks update-kubeconfig --name ${config.clusterName}"
            }
        }

        if (config.resetConfigmap) {
            sh "kubectl apply -f namespace.yaml"
            sh "kubectl apply -f deployment.yaml"
            sh "kubectl apply -f service.yaml"
            sh "kubectl apply -f configmap.yaml"
        }

        if (config.autoRestart) {
            def deployment = readYaml(file: "deployment.yaml")
            sh "kubectl rollout restart deployments/${deployment.metadata.labels.app} -n ${deployment.metadata.namespace}"
        }
    }
}