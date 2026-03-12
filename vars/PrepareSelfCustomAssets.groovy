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
    def pageVersionFile = config.pageVersionFile ?: 'src/assets/page-version.json'
    def lookupVersionFile = config.lookupVersionFile ?: 'src/assets/lookup-version.json'
    def customComponentVersionFile = config.customComponentVersionFile ?: 'src/assets/custom-component-version.json'
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
    
    // Group files by version for batch download
    def pagesByVersion = [:]
    pageVersion.each { path, version ->
        if (!pagesByVersion[version]) {
            pagesByVersion[version] = []
        }
        pagesByVersion[version].add(path)
    }
    
    def pageCount = 0
    pagesByVersion.each { version, paths ->
        def versionCacheDir = "${cacheDir}\\page\\${version}".replace('/', '\\')
        def s3VersionPath = "${s3Bucket}/page/${version}/"
        
        // Check if we need to download this version
        def needsDownload = false
        paths.each { path ->
            def fileName = path.tokenize('/').last()
            def cacheFile = "${versionCacheDir}\\${path}.json".replace('/', '\\')
            if (!fileExists(cacheFile)) {
                needsDownload = true
            }
        }
        
        if (needsDownload) {
            echo "  ↓ Downloading version ${version} from S3..."
            bat "if not exist \"${versionCacheDir}\" mkdir \"${versionCacheDir}\""
            
            // Download entire version folder recursively
            def downloadStatus = bat(script: "${awsCmd} s3 sync ${s3VersionPath} \"${versionCacheDir}\" --exclude \"*\" --include \"*.json\"", returnStatus: true)
            if (downloadStatus != 0) {
                echo "  ✗ WARNING: Failed to sync ${s3VersionPath}"
            }
        }
        
        // Copy files to target directory
        paths.each { path ->
            def fileName = path.tokenize('/').last()
            def cacheFile = "${versionCacheDir}\\${path}.json".replace('/', '\\')
            def targetFile = "${targetDir}\\${fileName}.json".replace('/', '\\')
            
            if (fileExists(cacheFile)) {
                bat "copy /Y \"${cacheFile}\" \"${targetFile}\" >nul 2>&1"
                pageCount++
            } else {
                echo "  ✗ WARNING: File not found in cache: ${fileName}.json"
            }
        }
    }
    echo "  Total page files: ${pageCount}"
    
    // Download lookup files
    echo "Downloading lookup files..."
    def lookupVersion = readJSON file: lookupVersionFile
    bat "if not exist \"${targetDir}\\lookup\" mkdir \"${targetDir}\\lookup\""
    
    // Group by version
    def lookupsByVersion = [:]
    lookupVersion.each { name, version ->
        if (!lookupsByVersion[version]) {
            lookupsByVersion[version] = []
        }
        lookupsByVersion[version].add(name)
    }
    
    def lookupCount = 0
    lookupsByVersion.each { version, names ->
        def versionCacheDir = "${cacheDir}\\lookup\\${version}".replace('/', '\\')
        def s3VersionPath = "${s3Bucket}/lookup/${version}/"
        
        def needsDownload = false
        names.each { name ->
            def cacheFile = "${versionCacheDir}\\${name}.json".replace('/', '\\')
            if (!fileExists(cacheFile)) {
                needsDownload = true
            }
        }
        
        if (needsDownload) {
            echo "  ↓ Downloading lookup version ${version} from S3..."
            bat "if not exist \"${versionCacheDir}\" mkdir \"${versionCacheDir}\""
            
            def downloadStatus = bat(script: "${awsCmd} s3 sync ${s3VersionPath} \"${versionCacheDir}\" --exclude \"*\" --include \"*.json\"", returnStatus: true)
            if (downloadStatus != 0) {
                echo "  ✗ WARNING: Failed to sync ${s3VersionPath}"
            }
        }
        
        names.each { name ->
            def cacheFile = "${versionCacheDir}\\${name}.json".replace('/', '\\')
            def targetFile = "${targetDir}\\lookup\\${name}.json".replace('/', '\\')
            
            if (fileExists(cacheFile)) {
                bat "copy /Y \"${cacheFile}\" \"${targetFile}\" >nul 2>&1"
                lookupCount++
            } else {
                echo "  ✗ WARNING: File not found in cache: ${name}.json"
            }
        }
    }
    echo "  Total lookup files: ${lookupCount}"
    
    // Download custom-component files
    echo "Downloading custom-component files..."
    def customComponentVersion = readJSON file: customComponentVersionFile
    bat "if not exist \"${targetDir}\\custom-component\" mkdir \"${targetDir}\\custom-component\""
    
    // Group by version
    def componentsByVersion = [:]
    customComponentVersion.each { name, version ->
        if (!componentsByVersion[version]) {
            componentsByVersion[version] = []
        }
        componentsByVersion[version].add(name)
    }
    
    def componentCount = 0
    componentsByVersion.each { version, names ->
        def versionCacheDir = "${cacheDir}\\custom-component\\${version}".replace('/', '\\')
        def s3VersionPath = "${s3Bucket}/custom-component/${version}/"
        
        def needsDownload = false
        names.each { name ->
            def cacheFile = "${versionCacheDir}\\${name}.json".replace('/', '\\')
            if (!fileExists(cacheFile)) {
                needsDownload = true
            }
        }
        
        if (needsDownload) {
            echo "  ↓ Downloading custom-component version ${version} from S3..."
            bat "if not exist \"${versionCacheDir}\" mkdir \"${versionCacheDir}\""
            
            def downloadStatus = bat(script: "${awsCmd} s3 sync ${s3VersionPath} \"${versionCacheDir}\" --exclude \"*\" --include \"*.json\"", returnStatus: true)
            if (downloadStatus != 0) {
                echo "  ✗ WARNING: Failed to sync ${s3VersionPath}"
            }
        }
        
        names.each { name ->
            def cacheFile = "${versionCacheDir}\\${name}.json".replace('/', '\\')
            def targetFile = "${targetDir}\\custom-component\\${name}.json".replace('/', '\\')
            
            if (fileExists(cacheFile)) {
                bat "copy /Y \"${cacheFile}\" \"${targetFile}\" >nul 2>&1"
                componentCount++
            } else {
                echo "  ✗ WARNING: File not found in cache: ${name}.json"
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
    
    def pagesByVersion = [:]
    pageVersion.each { path, version ->
        if (!pagesByVersion[version]) pagesByVersion[version] = []
        pagesByVersion[version].add(path)
    }
    
    def pageCount = 0
    pagesByVersion.each { version, paths ->
        def versionCacheDir = "${cacheDir}/page/${version}"
        def s3VersionPath = "${s3Bucket}/page/${version}/"
        
        def needsDownload = paths.any { path -> !fileExists("${versionCacheDir}/${path}.json") }
        
        if (needsDownload) {
            echo "  ↓ Downloading version ${version} from S3..."
            sh "mkdir -p ${versionCacheDir}"
            def downloadStatus = sh(script: "${awsCmd} s3 sync ${s3VersionPath} ${versionCacheDir} --exclude '*' --include '*.json'", returnStatus: true)
            if (downloadStatus != 0) echo "  ✗ WARNING: Failed to sync ${s3VersionPath}"
        }
        
        paths.each { path ->
            def fileName = path.tokenize('/').last()
            def cacheFile = "${versionCacheDir}/${path}.json"
            def targetFile = "${targetDir}/${fileName}.json"
            if (fileExists(cacheFile)) {
                sh "cp ${cacheFile} ${targetFile}"
                pageCount++
            } else {
                echo "  ✗ WARNING: File not found in cache: ${fileName}.json"
            }
        }
    }
    echo "  Total page files: ${pageCount}"
    
    // Download lookup files
    echo "Downloading lookup files..."
    def lookupVersion = readJSON file: lookupVersionFile
    sh "mkdir -p ${targetDir}/lookup"
    
    def lookupsByVersion = [:]
    lookupVersion.each { name, version ->
        if (!lookupsByVersion[version]) lookupsByVersion[version] = []
        lookupsByVersion[version].add(name)
    }
    
    def lookupCount = 0
    lookupsByVersion.each { version, names ->
        def versionCacheDir = "${cacheDir}/lookup/${version}"
        def s3VersionPath = "${s3Bucket}/lookup/${version}/"
        
        def needsDownload = names.any { name -> !fileExists("${versionCacheDir}/${name}.json") }
        
        if (needsDownload) {
            echo "  ↓ Downloading lookup version ${version} from S3..."
            sh "mkdir -p ${versionCacheDir}"
            def downloadStatus = sh(script: "${awsCmd} s3 sync ${s3VersionPath} ${versionCacheDir} --exclude '*' --include '*.json'", returnStatus: true)
            if (downloadStatus != 0) echo "  ✗ WARNING: Failed to sync ${s3VersionPath}"
        }
        
        names.each { name ->
            def cacheFile = "${versionCacheDir}/${name}.json"
            def targetFile = "${targetDir}/lookup/${name}.json"
            if (fileExists(cacheFile)) {
                sh "cp ${cacheFile} ${targetFile}"
                lookupCount++
            } else {
                echo "  ✗ WARNING: File not found in cache: ${name}.json"
            }
        }
    }
    echo "  Total lookup files: ${lookupCount}"
    
    // Download custom-component files
    echo "Downloading custom-component files..."
    def customComponentVersion = readJSON file: customComponentVersionFile
    sh "mkdir -p ${targetDir}/custom-component"
    
    def componentsByVersion = [:]
    customComponentVersion.each { name, version ->
        if (!componentsByVersion[version]) componentsByVersion[version] = []
        componentsByVersion[version].add(name)
    }
    
    def componentCount = 0
    componentsByVersion.each { version, names ->
        def versionCacheDir = "${cacheDir}/custom-component/${version}"
        def s3VersionPath = "${s3Bucket}/custom-component/${version}/"
        
        def needsDownload = names.any { name -> !fileExists("${versionCacheDir}/${name}.json") }
        
        if (needsDownload) {
            echo "  ↓ Downloading custom-component version ${version} from S3..."
            sh "mkdir -p ${versionCacheDir}"
            def downloadStatus = sh(script: "${awsCmd} s3 sync ${s3VersionPath} ${versionCacheDir} --exclude '*' --include '*.json'", returnStatus: true)
            if (downloadStatus != 0) echo "  ✗ WARNING: Failed to sync ${s3VersionPath}"
        }
        
        names.each { name ->
            def cacheFile = "${versionCacheDir}/${name}.json"
            def targetFile = "${targetDir}/custom-component/${name}.json"
            if (fileExists(cacheFile)) {
                sh "cp ${cacheFile} ${targetFile}"
                componentCount++
            } else {
                echo "  ✗ WARNING: File not found in cache: ${name}.json"
            }
        }
    }
    echo "  Total custom-component files: ${componentCount}"
}
