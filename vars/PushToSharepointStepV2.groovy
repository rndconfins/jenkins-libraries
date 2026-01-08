/**@
    Push to sharepoint step

    parameters :
    uploadDestination: sharepoint target site
    
*/
def call(Map config = [:]) {
    def destination = config.uploadDestination ?: "rnd"
    
    dir("./$BUILD_PATH") {
        bat "git add ."
        bat "git diff --cached --name-only > list.tmp"
        bat "%SCRIPTS%\\psrun.bat get-diff.ps1 $BUILD_NUMBER \"$WORKSPACE\\$BUILD_PATH\" "
        bat "%SCRIPTS%\\psrun.bat uploadV2.ps1 \"Released Package/CONFINS/$JOB_NAME\" $BUILD_NUMBER \"$WORKSPACE\\$BUILD_PATH\" \"$WORKSPACE\\$BUILD_PATH\\list.tmp\" ${destination}"
        bat "del \"$WORKSPACE\\$BUILD_PATH\\list.tmp\""
    }
}