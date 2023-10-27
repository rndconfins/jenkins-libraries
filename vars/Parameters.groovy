def call(Map config = [:]){
    parameters {
        choice(name: "SonarQube", choices: ["Bypass", "Weekly"], description: "SonarQube Scan Type")
        booleanParam(name: 'CleanWorkspace', defaultValue: false, description: 'Clean Workspace')
        choice(choices: ['Alibaba Cloud', 'Google Cloud', 'AWS', 'AWS CLI', 'Azure'], name: 'CloudType')
        string(name: 'CredentialsId', description: 'Cloud Credentials ID(make sure you have created the credentials)')
        string(name: 'RegistryURL', description: 'Cloud Registry URL')
        string(name: 'ImageName', description: 'Docker Image Name (ex. repo/imagename)')
        string(name: 'KubeNamespace', description: 'Kubernetes Deployment Namespace', defaultValue: 'default')
    }
}