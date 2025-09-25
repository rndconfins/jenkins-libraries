/**@
    Pull Image from Container Registry

    parameters :
        credentialsId : Credentials id for login to Container Registry
        cloudType : Cloud provider name (Alibaba Cloud, Google Cloud, AWS, AWS CLI) -> AWS use file credentials, AWS CLI use access key&secret
        registryURL : Container Registry endpoint URL
        imageName : Docker image name to push to container registry
        regionId : (AWS CLI) AWS Region ID

    credentialsId :
        Alibaba Cloud : username&password
        Google Cloud : file
        AWS : file
        AWS CLI : username&password
*/
def call(Map config = [:]) {
    config.imageName = config.imageName.replaceAll("/null/", "/").replaceAll("//", "/").replaceFirst(":/+", "://")
    if (config.cloudType == "Alibaba Cloud") {
        docker.withRegistry("${config.registryURL}", "${config.credentialsId}") {
            dockerImageRemote = docker.image("${config.imageName}").pull() 
        }
    } else if (config.cloudType == "Google Cloud") {
        withCredentials([file(credentialsId: "${config.credentialsId}", variable: 'FILE')]) {
            sh "cat $FILE | docker login -u _json_key --password-stdin ${config.registryURL}"
        }
        dockerImageRemote = docker.image("${config.imageName}").pull() 
    } else if (config.cloudType == "AWS") {
        withCredentials([file(credentialsId: "${config.credentialsId}", variable: 'FILE')]) {
            sh "cat $FILE | docker login --username AWS --password-stdin ${config.registryURL}"
        }
        dockerImageRemote = docker.image("${config.imageName}").pull() 
    } else if (config.cloudType == "AWS CLI") {
        if (!env.AWS_DEFAULT_REGION) {
            env.AWS_DEFAULT_REGION = config.regionId
        }
        withCredentials([usernamePassword(credentialsId: "${config.credentialsId}", passwordVariable: 'SECRET', usernameVariable: 'KEY')]) {
            sh "aws configure set aws_access_key_id $KEY"
            sh "aws configure set aws_secret_access_key $SECRET"
            sh "aws ecr get-login-password > ~/aws_creds.txt"
            sh "cat ~/aws_creds.txt | docker login --username AWS --password-stdin ${config.registryURL}"
            echo "image - ${config.imageName}"
            sh "docker pull ${config.imageName}"
        }
        //dockerImageRemote = docker.image("${config.imageName}").pull()
    } else if (config.cloudType == "Azure") {
        echo "Azure Provider is under maintenance or unavailable"
    }
    else if (config.cloudType == "Local Registry") {
            dockerImageRemote = docker.image("${config.imageName}").pull() 
    }
    else if (config.cloudType == "OIDC") {
        sh '''
        export VAULT_ADDR=https://omnisecret.bfidigital.id
        export VAULT_TOKEN=$(curl -s \
          --request POST \
          --header "Content-Type: application/json" \
          --data '{"role_id": "b6228b16-6700-ffed-1f2b-8e1e0bd354e5", "secret_id": "62b3fa44-2757-e86d-6e99-2fbefae31e30"}' \
          "$VAULT_ADDR/v1/auth/approle/login" | jq -r '.auth.client_token')
        
        export OIDC_TOKEN=$(curl -s \
          --header "X-Vault-Token: $VAULT_TOKEN" \
          "$VAULT_ADDR/v1/identity/oidc/token/role-001" | jq -r '.data.token')
        
        export FEDERATED_TOKEN=$(curl -s -X POST \
          -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
          -d "audience=//iam.googleapis.com/projects/695826792989/locations/global/workloadIdentityPools/vault-pool/providers/oidc-vault" \
          -d "subject_token_type=urn:ietf:params:oauth:token-type:jwt" \
          -d "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
          -d "scope=https://www.googleapis.com/auth/cloud-platform" \
          -d "subject_token=$OIDC_TOKEN" \
          https://sts.googleapis.com/v1beta/token | jq -r '.access_token')
        
        echo "$FEDERATED_TOKEN" | docker login -u oauth2accesstoken --password-stdin asia-southeast2-docker.pkg.dev
        
        '''
        
        dockerImageRemote = docker.image("${config.imageName}").pull() 
    }
}
