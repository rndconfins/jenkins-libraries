/**@
    Build project step
    
    Parameters
        executableName : dll file name as entry point
        dockerfile : dockerfile config id (default: dockerfile-fe/dockerfile-be)
        baseHref : base href of FE project (default: /)
        nginxconfig : nginx config id to override default nginx config (default: nginx-fe)
        useNodeTool : use nodejs from environment tool
        skipBuildEvent: skip dotnet build event (default: false)
        isWorker: use build release for worker (default: false)
        isConsumer: use build release for consumer (default: false)
        pathProject: use build release for consumer (default: "")
*/

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream

def call(Map config = [:]) {
    FileInputStream excelFile = new FileInputStream('C:\\Build\\Publish\\WOMF\\CLIENT\\ListDeployment.xlsx')
    Workbook workbook = new XSSFWorkbook(excelFile)
    Sheet sheet = workbook.getSheetAt(0) // Mengasumsikan data berada di sheet pertama
    
    // Mengambil data dari kolom Path Object dan Nama Object
    for (Row row : sheet) {
        // Melakukan pembacaan kolom-kolom yang sesuai
        def ipRanges = row.getCell(0).getStringCellValue().split('s/d').collect { it.trim() }
        def jenisObject = row.getCell(1).getStringCellValue() // Kolom jenis Object
        def pathObject = row.getCell(2).getStringCellValue() // Kolom Path Object 
        def namaObject = row.getCell(3).getStringCellValue() // Kolom Nama Object
        
        // Menampilkan nilai-nilai yang dibaca
        echo "IpRanges: ${ipRanges}"
        echo "JenisObject: ${jenisObject}"
        echo "PathObject: ${pathObject}"
        echo "NamaObject: ${namaObject}"
    }
    
    // Menutup workbook dan FileInputStream untuk membebaskan sumber daya
    workbook.close()
    excelFile.close()
}
