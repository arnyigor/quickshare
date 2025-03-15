package com.arny.quickshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.arny.quickshare.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var receivedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIntent(intent)
        setupUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun extractCodeBlocks(text: String): List<String> {
        val codeBlocks = mutableListOf<String>()

        // Регулярное выражение для Markdown-блоков кода
        val markdownPattern = Regex("```[\\s\\S]*?```", RegexOption.MULTILINE)
        val markdownMatches = markdownPattern.findAll(text)
        markdownMatches.forEach { match ->
            codeBlocks.add(match.value.trim())
        }

        // Регулярное выражение для блоков кода с тильдами
        val tildePattern = Regex("~~~[\\s\\S]*?~~~", RegexOption.MULTILINE)
        val tildeMatches = tildePattern.findAll(text)
        tildeMatches.forEach { match ->
            codeBlocks.add(match.value.trim())
        }

        // Регулярное выражение для inline-кода
        val inlinePattern = Regex("`[^`]+`")
        val inlineMatches = inlinePattern.findAll(text)
        inlineMatches.forEach { match ->
            codeBlocks.add(match.value.trim())
        }

        return codeBlocks.distinct()
    }

    private fun showCodeSelectionDialog(codeBlocks: List<String>) {
        val items = List(codeBlocks.size) { index ->
            getString(
                R.string.code_fragment,
                (index + 1)
            )
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.found_code_fragments))
            .setItems(items) { _, which ->
                val selectedCode = codeBlocks[which]
                showCodePreviewDialog(selectedCode)
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun showCodePreviewDialog(code: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.preview_code))
            .setMessage(code)
            .setPositiveButton(getString(R.string.use_this_code)) { _, _ ->
                binding.editText.setText(code)
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun setupUI() {
        // Настройка кнопок основных действий
        binding.shareButton.setOnClickListener { shareAsFile() }
        binding.clearButton.setOnClickListener {
            binding.editText.text?.clear()
            receivedText = null
            updateUI()
        }
        binding.extractCodeButton.setOnClickListener {
            val text = binding.editText.text.toString()
            val codeBlocks = extractCodeBlocks(text)

            if (codeBlocks.isNotEmpty()) {
                showCodeSelectionDialog(codeBlocks)
            } else {
                showSnackbar(getString(R.string.code_not_found))
            }
        }
        // Кнопки буфера обмена
        binding.copyButton.setOnClickListener { copyToClipboard() }
        binding.pasteButton.setOnClickListener { pasteFromClipboard() }

        // Слушатель изменений текста
        binding.editText.doAfterTextChanged { s ->
            binding.shareButton.isEnabled = !s.isNullOrEmpty()
            binding.copyButton.isEnabled = !s.isNullOrEmpty()
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text
            if (text != null) {
                binding.editText.setText(text)
                showSnackbar(getString(R.string.text_inserted_from_buffer))
            }
        }
    }

    private fun updateUI() {
        binding.editText.setText(receivedText)
        binding.shareButton.isEnabled = !binding.editText.text.isNullOrEmpty()
        binding.copyButton.isEnabled = !binding.editText.text.isNullOrEmpty()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.error))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                // Получаем Uri файла, если он есть
                val fileUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

                when {
                    // Если есть прямой текст
                    intent.getStringExtra(Intent.EXTRA_TEXT) != null -> {
                        receivedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                        updateUI()
                    }
                    // Если есть файл, проверяем его тип и читаем
                    fileUri != null -> {
                        val mimeType = FileUtils.getMimeType(fileUri, this)
                        if (FileUtils.isTextFile(mimeType)) {
                            readTextFromUri(fileUri)
                        } else {
                            showError("Неподдерживаемый тип файла")
                        }
                    }
                }
            }

            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    val mimeType = FileUtils.getMimeType(uri, this)
                    if (FileUtils.isTextFile(mimeType)) {
                        readTextFromUri(uri)
                    } else {
                        showError("Неподдерживаемый тип файла")
                    }
                }
            }
        }
    }

    private fun readTextFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().use { it.readText() }
                receivedText = text
                updateUI()

                // Показываем информацию о файле
                val mimeType = FileUtils.getMimeType(uri, this)
                showSnackbar("Открыт файл типа: $mimeType")
            }
        } catch (e: Exception) {
            showError("Ошибка чтения файла: ${e.message}")
        }
    }

    private fun shareAsFile() {
        try {
            val text = binding.editText.text.toString()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())

            // Определяем расширение файла на основе содержимого
            val fileExtension = if (text.contains("```") || text.contains("#")) "md" else "txt"
            val fileName = "shared_text_${timestamp}.$fileExtension"

            val file = File(cacheDir, fileName)
            file.writeText(text)

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            // Определяем MIME-тип на основе расширения
            val mimeType = if (fileExtension == "md") "text/markdown" else "text/plain"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Отправить файл через"))

        } catch (e: Exception) {
            showError("Ошибка при создании файла: ${e.message}")
        }
    }

    // Добавляем функцию для определения типа содержимого
    private fun detectContentType(text: String): String = when {
        text.contains(Regex("(?i)```\\s*(python|py)")) -> "text/x-python"
        text.contains(Regex("(?i)```\\s*(javascript|js)")) -> "text/javascript"
        text.contains(Regex("(?i)```\\s*(java)")) -> "text/x-java"
        text.contains(Regex("(?i)```\\s*(kotlin|kt)")) -> "text/x-kotlin"
        text.contains(Regex("(?i)```\\s*(html)")) -> "text/html"
        text.contains(Regex("(?i)```\\s*(css)")) -> "text/css"
        else -> "text/plain"
    }

    // Расширенная версия копирования в буфер с определением типа
    private fun copyToClipboard() {
        val text = binding.editText.text.toString()
        val mimeType = detectContentType(text)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(mimeType, text)
        clipboard.setPrimaryClip(clip)

        showSnackbar("Скопировано как $mimeType")
    }

    // Добавляем меню с дополнительными опциями
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_file_info -> {
                showFileInfo()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFileInfo() {
        val text = binding.editText.text.toString()
        val contentType = detectContentType(text)
        val size = text.length
        val lines = text.lines().size

        MaterialAlertDialogBuilder(this)
            .setTitle("Информация о тексте")
            .setMessage(
                """
                Тип содержимого: $contentType
                Размер: $size символов
                Строк: $lines
                
                Рекомендуемое расширение: ${if (contentType == "text/markdown") "md" else "txt"}
            """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }
}