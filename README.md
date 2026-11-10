# 📄 AI PDF Summarizer (Spring Boot & Hugging Face)

An intelligent full-stack PDF summarization web application built with **Spring Boot 4 / Java 17**, **Apache PDFBox**, and the **free Hugging Face Serverless Inference API**.

It generates **both**:
1. **Executive Paragraph Summary**: A fluent, coherent narrative overview of the document.
2. **Key Takeaways & Bullet Points**: Distinct, punchy bullet points capturing the core highlights.

---

## 🚀 Features

- **Apache PDFBox 3.0**: Robust text extraction from multi-page PDF documents.
- **Intelligent Chunking Engine**: Recursively splits large documents into token-safe chunks respecting paragraph and sentence boundaries (guaranteeing compliance with model token limits).
- **Free Hugging Face Serverless Inference API**:
  - `facebook/bart-large-cnn` (Default, high-fidelity abstractive summaries)
  - `sshleifer/distilbart-cnn-12-6` (Fast & lightweight)
  - `google/pegasus-xsum` (Extreme, concise summarization)
- **Both Summary Formats**: Produces both fluent narrative paragraph overview and actionable key bullet points.
- **Modern Web Dashboard**:
  - Drag-and-drop PDF upload zone.
  - Interactive options (model choice, summary format toggle).
  - Hugging Face token saved in browser `localStorage`.
  - Export tools: **Copy**, **Download as Markdown (.md)**, and **Text-to-Speech (Listen)**.

---

## 🔑 Getting Your Free Hugging Face API Token

1. Sign up or log in at [Hugging Face](https://huggingface.co/).
2. Navigate to [Settings -> Access Tokens](https://huggingface.co/settings/tokens).
3. Click **"New token"**, choose the **Read** role, and copy your token (starts with `hf_...`).
4. You can paste this token directly into the web UI or set it as an environment variable:
   ```powershell
   $env:HF_API_KEY="hf_xxxxxxxxxxxxxxxxxxxx"
   ```

---

## 🏃 Running the Application

### Option 1: Run with Maven Wrapper
```powershell
.\mvnw.cmd spring-boot:run
```

### Option 2: Run the Packaged JAR
```powershell
java -jar target\Summarize-0.0.1-SNAPSHOT.jar
```

Once started, open your browser and navigate to:
👉 **[http://localhost:5050](http://localhost:5050)**

---

## 📡 REST API Endpoint

If you want to use the API directly (via cURL, Postman, or Python):

### `POST /api/summarize`
- **Headers**:
  - `X-HuggingFace-Token: hf_xxxx` (or passed via form data)
- **Form Data**:
  - `file`: PDF file (`multipart/form-data`)
  - `summaryType`: `both` | `paragraph` | `bullets`
  - `model`: `facebook/bart-large-cnn` (optional)

#### Example cURL:
```bash
curl -X POST "http://localhost:5050/api/summarize" \
  -H "X-HuggingFace-Token: hf_xxxxxxxxxxxxxxxxxxxx" \
  -F "file=@document.pdf" \
  -F "summaryType=both"
```

#### Example Response:
```json
{
  "fileName": "document.pdf",
  "totalPages": 5,
  "wordCount": 1820,
  "chunksProcessed": 2,
  "paragraphSummary": "This document explores advances in machine learning and natural language processing...",
  "bulletPoints": [
    "Transformer models achieve state-of-the-art results on summarization tasks.",
    "Efficient chunking allows processing documents of arbitrary length without token truncation.",
    "Dual output formats provide both high-level context and quick action items."
  ],
  "modelUsed": "facebook/bart-large-cnn",
  "processingTimeMs": 3410
}
```
