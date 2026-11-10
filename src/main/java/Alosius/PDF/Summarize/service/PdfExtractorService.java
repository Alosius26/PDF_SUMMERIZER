package Alosius.PDF.Summarize.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PdfExtractorService {

    public record ExtractedPdf(String text, int totalPages, int wordCount) {}

    public ExtractedPdf extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] bytes = file.getBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("The uploaded PDF is password protected or encrypted.");
            }

            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String rawText = stripper.getText(document);

            String cleanedText = cleanText(rawText);
            int wordCount = cleanedText.isBlank() ? 0 : cleanedText.trim().split("\\s+").length;

            return new ExtractedPdf(cleanedText, pageCount, wordCount);
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // Normalize whitespace and remove repeated blank lines
        return text.replace("\r\n", "\n")
                   .replace('\r', '\n')
                   .replaceAll("[ \\t]+", " ")
                   .replaceAll("\n{3,}", "\n\n")
                   .trim();
    }
}
