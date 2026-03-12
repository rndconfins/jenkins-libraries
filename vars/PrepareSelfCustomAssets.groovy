def call(Map config = [:]) {
    // Required parameters
    def s3Bucket = config.s3Bucket
    def credentialsId = config.credentialsId
    def regionId = config.regionId
    
    // Extract bucket name from s3://bucket-name
    def bucketName = s3Bucket.replaceAll('^s3://', '')
    
    // Auto-generate cacheDir based on bucket name
    def cacheDir = "${env.JENKINS_HOME}/cache/${bucketName}"
    
    // Hardcoded values
    def targetDir = 'src/assets/form-template'
    def awsCliPath = isUnix() ? 'aws' : 'C:\\Program Files\\Amazon\\AWSCLIV2\\aws.exe'
    
    // Optional parameters
    def pageVersionFile = config.pageVersionFile ?: 'page-version.json'
    def lookupVersionFile = config.lookupVersionFile ?: 'lookup-version.json'
    def customComponentVersionFile = config.customComponentVersionFile ?: 'custom-component-version.json'
    def skipValidation = config.skipValidation ?: false
    
    echo "S3 Bucket: ${s3Bucket}"
    echo "Cache Directory: ${cacheDir}"
    echo "Target Directory: ${targetDir}"
    echo "AWS CLI Path: ${awsCliPath}"
    
    // Step 1: Validate Versions
    if (!skipValidation) {
        echo "=== Validating Versions ==="
        validateVersions(pageVersionFile, lookupVersionFile, customComponentVersionFile)
    }
    
    // Step 2: Download from S3
    echo "=== Downloading from S3 ==="
    downloadFromS3(s3Bucket, targetDir, cacheDir, credentialsId, regionId, awsCliPath, 
                   pageVersionFile, lookupVersionFile, customComponentVersionFile)
}

def validateVersions(pageVersionFile, lookupVersionFile, customComponentVersionFile) {
    def pageVersion = readJSON file: pageVersionFile
    def lookupVersion = readJSON file: lookupVersionFile
    def customComponentVersion = readJSON file: customComponentVersionFile
    
    def hasDraft = false
    def draftList = []
    
    pageVersion.each { path, version ->
        if (version.contains('draft')) {
            hasDraft = true
            draftList.add("${pageVersionFile}: ${path} = ${version}")
        }
    }
    
    lookupVersion.each { name, version ->
        if (version.contains('draft')) {
            hasDraft = true
            draftList.add("${lookupVersionFile}: ${name} = ${version}")
        }
    }
    
    customComponentVersion.each { name, version ->
        if (version.contains('draft')) {
            hasDraft = true
            draftList.add("${customComponentVersionFile}: ${name} = ${version}")
        }
    }
    
    if (hasDraft) {
        error("Build failed: Draft versions detected:\n" + draftList.join('\n'))
    }
    
    echo "✓ Version validation passed - no draft versions found"
}

def downloadFromS3(s3Bucket, targetDir, cacheDir, credentialsId, regionId, awsCliPath,
                   pageVersionFile, lookupVersionFile, customComponentVersionFile) {
    
    // Detect OS
    def isWindows = isUnix() ? false : true
    
    if (!env.AWS_DEFAULT_REGION) {
        env.AWS_DEFAULT_REGION = regionId
    }
    
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'SECRET', usernameVariable: 'KEY')]) {
        // Configure AWS CLI
        if (isWindows) {
            def awsCmd = awsCliPath.contains(' ') ? "\"${awsCliPath}\"" : awsCliPath
            bat "${awsCmd} configure set aws_access_key_id %KEY%"
            bat "${awsCmd} configure set aws_secret_access_key %SECRET%"
            bat "${awsCmd} configure set region ${regionId}"
            
            // Download files
            downloadFilesWindows(awsCmd, s3Bucket, targetDir, cacheDir, pageVersionFile, lookupVersionFile, customComponentVersionFile)
        } else {
            sh "${awsCliPath} configure set aws_access_key_id \$KEY"
            sh "${awsCliPath} configure set aws_secret_access_key \$SECRET"
            sh "${awsCliPath} configure set region ${regionId}"
            
            // Download files
            downloadFilesLinux(awsCliPath, s3Bucket, targetDir, cacheDir, pageVersionFile, lookupVersionFile, customComponentVersionFile)
        }
    }
    
    echo "✓ Download from S3 completed"
}

def downloadFilesWindows(awsCmd, s3Bucket, targetDir, cacheDir, pageVersionFile, lookupVersionFile, customComponentVersionFile) {
    // Download page files
    echo "Downloading page files..."
    def pageVersion = readJSON file: pageVersionFile
    bat "if not exist \"${targetDir}\" mkdir \"${targetDir}\""
    
    def pageCount = 0
    pageVersion.each { path, version ->
        def fileName = path.tokenize('/').last()
        def cacheFile = "${cacheDir}\\page\\${version}\\${path}.json".replace('/', '\\')
        def targetFile = "${targetDir}\\${fileName}.json".replace('/', '\\')
        
        if (fileExists(cacheFile)) {
            echo "  ✓ Using cached: ${fileName}.json"
            bat "copy /Y \"${cacheFile}\" \"${targetFile}\""
            pageCount++
        } else {
            def s3Path = "${s3Bucket}/page/${version}/${path}.json"
            echo "  ↓ Downloading: ${fileName}.json"
            def cacheFileDir = cacheFile.replaceAll('\\\\[^\\\\]+$', '')
            bat "if not exist \"${cacheFileDir}\" mkdir \"${cacheFileDir}\""
            
            def downloadStatus = bat(script: "${awsCmd} s3 cp ${s3Path} \"${cacheFile}\"", returnStatus: true)
            if (downloadStatus == 0) {
                bat "copy /Y \"${cacheFile}\" \"${targetFile}\""
                pageCount++
            } else {
                echo "  ✗ WARNING: Failed to download ${s3Path}"
            }
        }
    }
    echo "  Total page files: ${pageCount}"
    
    // Download lookup files
    echo "Downloading lookup files..."
    def lookupVersion = readJSON file: lookupVersionFile
    bat "if not exist \"${targetDir}\\lookup\" mkdir \"${targetDir}\\lookup\""
    
    def lookupCount = 0
    lookupVersion.each { name, version ->
        def cacheFile = "${cacheDir}\\lookup\\${version}\\${name}.json".replace('/', '\\')
        def targetFile = "${targetDir}\\lookup\\${name}.json".replace('/', '\\')
        
        if (fileExists(cacheFile)) {
            echo "  ✓ Using cached: ${name}.json"
            bat "copy /Y \"${cacheFile}\" \"${targetFile}\""
            lookupCount++
        } else {
            def s3Path = "${s3Bucket}/lookup/${version}/${name}.json"
            echo "  ↓ Downloading: ${name}.json"
            def cacheFileDir = "${cacheDir}\\lookup\\${version}".replace('/', '\\')
            bat "if not exist \"${cacheFileDir}\" mkdir \"${cacheFileDir}\""
            
            def downloadStatus = bat(script: "${awsCmd} s3 cp ${s3Path} \"${cacheFile}\"", returnStatus: true)
            if (downloadStatus == 0) {
                bat "copy /Y \"${cacheFile}\" \"${targetFile}\""
                lookupCount++
            } else {
                echo "  ✗ WARNING: Failed to download ${s3Path}"
            }
        }
    }
    echo "  Total lookup files: ${lookupCount}"
    
    // Download custom-component files
    echo "Downloading custom-component files..."
    def customComponentVersion = readJSON file: customComponentVersionFile
    bat "if not exist \"${targetDir}\\custom-component\" mkdir \"${targetDir}\\custom-component\""
    
    def componentCount = 0
    customComponentVersion.each { name, version ->
        def cacheFile = "${cacheDir}\\custom-component\\${version}\\${name}.json".replace('/', '\\')
        def targetFile = "${targetDir}\\custom-component\\${name}.json".replace('/', '\\')
        
        if (fileExists(cacheFile)) {
            echo "  ✓ Using cached: ${name}.json"
            bat "copy /Y \"${cacheFile}\" \"${targetFile}\""
            componentCount++
        } else {
            def s3Path = "${s3Bucket}/custom-component/${version}/${name}.json"
            echo "  ↓ Downloading: ${name}.json"
            def cacheFileDir = "${cacheDir}\\custom-component\\${version}".replace('/', '\\')
            bat "if not exist \"${cacheFileDir}\" mkdir \"${cacheFileDir}\""
            
            def downloadStatus = bat(script: "${awsCmd} s3 cp ${s3Path} \"${cacheFile}\"", returnStatus: true)
            if (downloadStatus == 0) {
                bat "copy /Y \"${cacheFile}\" \"${targetFile}\""
                componentCount++
            } else {
                echo "  ✗ WARNING: Failed to download ${s3Path}"
            }
        }
    }
    echo "  Total custom-component files: ${componentCount}"
}

def downloadFilesLinux(awsCmd, s3Bucket, targetDir, cacheDir, pageVersionFile, lookupVersionFile, customComponentVersionFile) {
    // Download page files
    echo "Downloading page files..."
    def pageVersion = readJSON file: pageVersionFile
    sh "mkdir -p ${targetDir}"
    
    def pageCount = 0
    pageVersion.each { path, version ->
        def fileName = path.tokenize('/').last()
        def cacheFile = "${cacheDir}/page/${version}/${path}.json"
        def targetFile = "${targetDir}/${fileName}.json"
        
        if (fileExists(cacheFile)) {
            echo "  ✓ Using cached: ${fileName}.json"
            sh "cp ${cacheFile} ${targetFile}"
            pageCount++
        } else {
            def s3Path = "${s3Bucket}/page/${version}/${path}.json"
            echo "  ↓ Downloading: ${fileName}.json"
            def cacheFileDir = cacheFile.replaceAll('/[^/]+$', '')
            sh "mkdir -p ${cacheFileDir}"
            
            def downloadStatus = sh(script: "${awsCmd} s3 cp ${s3Path} ${cacheFile}", returnStatus: true)
            if (downloadStatus == 0) {
                sh "cp ${cacheFile} ${targetFile}"
                pageCount++
            } else {
                echo "  ✗ WARNING: Failed to download ${s3Path}"
            }
        }
    }
    echo "  Total page files: ${pageCount}"
    
    // Download lookup files
    echo "Downloading lookup files..."
    def lookupVersion = readJSON file: lookupVersionFile
    sh "mkdir -p ${targetDir}/lookup"
    
    def lookupCount = 0
    lookupVersion.each { name, version ->
        def cacheFile = "${cacheDir}/lookup/${version}/${name}.json"
        def targetFile = "${targetDir}/lookup/${name}.json"
        
        if (fileExists(cacheFile)) {
            echo "  ✓ Using cached: ${name}.json"
            sh "cp ${cacheFile} ${targetFile}"
            lookupCount++
        } else {
            def s3Path = "${s3Bucket}/lookup/${version}/${name}.json"
            echo "  ↓ Downloading: ${name}.json"
            def cacheFileDir = "${cacheDir}/lookup/${version}"
            sh "mkdir -p ${cacheFileDir}"
            
            def downloadStatus = sh(script: "${awsCmd} s3 cp ${s3Path} ${cacheFile}", returnStatus: true)
            if (downloadStatus == 0) {
                sh "cp ${cacheFile} ${targetFile}"
                lookupCount++
            } else {
                echo "  ✗ WARNING: Failed to download ${s3Path}"
            }
        }
    }
    echo "  Total lookup files: ${lookupCount}"
    
    // Download custom-component files
    echo "Downloading custom-component files..."
    def customComponentVersion = readJSON file: customComponentVersionFile
    sh "mkdir -p ${targetDir}/custom-component"
    
    def componentCount = 0
    customComponentVersion.each { name, version ->
        def cacheFile = "${cacheDir}/custom-component/${version}/${name}.json"
        def targetFile = "${targetDir}/custom-component/${name}.json"
        
        if (fileExists(cacheFile)) {
            echo "  ✓ Using cached: ${name}.json"
            sh "cp ${cacheFile} ${targetFile}"
            componentCount++
        } else {
            def s3Path = "${s3Bucket}/custom-component/${version}/${name}.json"
            echo "  ↓ Downloading: ${name}.json"
            def cacheFileDir = "${cacheDir}/custom-component/${version}"
            sh "mkdir -p ${cacheFileDir}"
            
            def downloadStatus = sh(script: "${awsCmd} s3 cp ${s3Path} ${cacheFile}", returnStatus: true)
            if (downloadStatus == 0) {
                sh "cp ${cacheFile} ${targetFile}"
                componentCount++
            } else {
                echo "  ✗ WARNING: Failed to download ${s3Path}"
            }
        }
    }
    echo "  Total custom-component files: ${componentCount}"
}
