package id.xms.xcai.data.util

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class for reading document content from various file types
 */
object DocumentReader {
    
    // Supported file extensions
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown",
        "html", "htm", "css", "js", "ts", "jsx", "tsx",
        "kt", "kts", "java", "py", "rb", "go", "rs", "c", "cpp", "h",
        "json", "xml", "yaml", "yml", "toml",
        "sh", "bash", "zsh", "bat", "ps1",
        "sql", "csv"
    )
    
    private val PDF_EXTENSIONS = setOf("pdf")
    
    // Max file size (500KB for text, 5MB for PDF)
    private const val MAX_TEXT_SIZE = 500 * 1024L
    private const val MAX_PDF_SIZE = 5 * 1024 * 1024L
    
    /**
     * Read document content from URI
     * @return Pair of (content, mimeType) or null if unsupported
     */
    fun readDocument(context: Context, uri: Uri): Result<DocumentContent> {
        return try {
            val fileName = getFileName(context, uri)
            val extension = fileName.substringAfterLast('.', "").lowercase()
            
            when {
                extension in TEXT_EXTENSIONS -> readTextFile(context, uri, fileName)
                extension in PDF_EXTENSIONS -> readPdfFile(context, uri, fileName)
                else -> Result.failure(UnsupportedFileException("Unsupported file type: .$extension"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if file extension is supported
     */
    fun isSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in TEXT_EXTENSIONS || extension in PDF_EXTENSIONS
    }
    
    /**
     * Get MIME type filter for file picker
     */
    fun getSupportedMimeTypes(): Array<String> = arrayOf(
        "text/*",
        "application/pdf",
        "application/json",
        "application/xml",
        "application/javascript"
    )
    
    private fun readTextFile(context: Context, uri: Uri, fileName: String): Result<DocumentContent> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Check file size
                val size = inputStream.available().toLong()
                if (size > MAX_TEXT_SIZE) {
                    return Result.failure(FileTooLargeException("File too large. Max size: 500KB"))
                }
                
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                
                val extension = fileName.substringAfterLast('.', "").lowercase()
                Result.success(DocumentContent(
                    fileName = fileName,
                    content = content,
                    fileType = getFileTypeLabel(extension),
                    charCount = content.length
                ))
            } ?: Result.failure(Exception("Cannot open file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun readPdfFile(context: Context, uri: Uri, fileName: String): Result<DocumentContent> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val pdfReader = PdfReader(inputStream)
                val pdfDoc = PdfDocument(pdfReader)
                
                val textBuilder = StringBuilder()
                val pageCount = pdfDoc.numberOfPages
                
                for (i in 1..pageCount) {
                    val page = pdfDoc.getPage(i)
                    val text = PdfTextExtractor.getTextFromPage(page)
                    textBuilder.append(text)
                    if (i < pageCount) textBuilder.append("\n\n--- Page ${i + 1} ---\n\n")
                }
                
                pdfDoc.close()
                
                val content = textBuilder.toString()
                
                // Limit content size
                val truncatedContent = if (content.length > 50000) {
                    content.take(50000) + "\n\n[... content truncated, ${content.length - 50000} characters remaining ...]"
                } else content
                
                Result.success(DocumentContent(
                    fileName = fileName,
                    content = truncatedContent,
                    fileType = "PDF ($pageCount pages)",
                    charCount = content.length
                ))
            } ?: Result.failure(Exception("Cannot open PDF file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = "document"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
    
    private fun getFileTypeLabel(extension: String): String = when (extension) {
        "txt" -> "Text"
        "md", "markdown" -> "Markdown"
        "html", "htm" -> "HTML"
        "css" -> "CSS"
        "js", "jsx" -> "JavaScript"
        "ts", "tsx" -> "TypeScript"
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "py" -> "Python"
        "json" -> "JSON"
        "xml" -> "XML"
        "sql" -> "SQL"
        "csv" -> "CSV"
        else -> extension.uppercase()
    }
}

data class DocumentContent(
    val fileName: String,
    val content: String,
    val fileType: String,
    val charCount: Int
)

class UnsupportedFileException(message: String) : Exception(message)
class FileTooLargeException(message: String) : Exception(message)
