/**@
    Push to sharepoint step

    parameters :
    
*/
def call(Map config = [:]) {
    dir("./$BUILD_PATH") {
        bat "git add ."
        bat "git diff --cached --name-only > list.tmp"
        bat "%SCRIPTS%\\psrun.bat get-diff.ps1 $BUILD_NUMBER \"$WORKSPACE\\$BUILD_PATH\" "
        bat "%SCRIPTS%\\psrun.bat upload.ps1 \"Released Package/CONFINS/$JOB_NAME\" $BUILD_NUMBER \"$WORKSPACE\\$BUILD_PATH\" \"$WORKSPACE\\$BUILD_PATH\\list.tmp\" "
        bat "del \"$WORKSPACE\\$BUILD_PATH\\list.tmp\""
    }
}