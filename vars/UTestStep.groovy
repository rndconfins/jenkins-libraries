/**@
    Unit test step

    parameters :
        testParams : Optional parameters to pass angular test
*/
def call(Map config = [:]) {
    if (config.type == "be") {
        bat 'dotnet test --collect "Code Coverage" --logger trx -r VSTestResult'
    } else  {
        if (config.os == "linux") {
            sh "node --max_old_space_size=12288 ./node_modules/@angular/cli/bin/ng test ${config.testParams}"
        } else  {
            sh "node --max_old_space_size=12288 ./node_modules/@angular/cli/bin/ng test ${config.testParams}"
        }
    }
}