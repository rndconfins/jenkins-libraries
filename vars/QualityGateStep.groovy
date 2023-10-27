/**@
    Quality gate step

    parameters :
    
*/
def call() {
    def qualitygate = waitForQualityGate()
    if (qualitygate.status != "OK") {
        error "Pipeline aborted due to quality gate coverage failure: ${qualitygate.status}"
    }
}