package com.arny.quickshare

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import com.arny.quickshare.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var receivedText: String? = null
    private val clipboard by lazy { getSystemService<ClipboardManager>() }
    private val vibrator by lazy { getSystemService<Vibrator>() }
    private var currentToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Сначала настраиваем начальное состояние кнопок
        updateButtonsState()

        // Затем настраиваем UI и обработчики
        setupUI()

        // Обрабатываем входящие интенты
        handleIntent(intent)

        // Регистрируем слушатель изменений буфера обмена
        clipboard?.addPrimaryClipChangedListener {
            updateButtonsState()
        }
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
        with(binding) {
            // Настройка кнопок буфера обмена
            copyButton.setOnClickListener {
                val text = editText.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    performHapticFeedback()
                    copyToClipboard(text)
                } else {
                    showToast(getString(R.string.nothing_to_copy))
                }
            }

            pasteButton.setOnClickListener {
                if (isClipboardNotEmpty()) {
                    performHapticFeedback()
                    pasteFromClipboard()
                } else {
                    showToast(getString(R.string.clipboard_empty))
                }
            }

            clearButton.setOnClickListener {
                performHapticFeedback()
                editText.text?.clear()
                showToast(getString(R.string.cleared))
            }

            // Настройка основных действий
            extractCodeButton.setOnClickListener {
                val text = editText.text?.toString() ?: ""
                if (text.isBlank()) {
                    showToast(getString(R.string.nothing_to_copy))
                    return@setOnClickListener
                }
                performHapticFeedback()
                val codeBlocks = extractCodeBlocks(text)
                if (codeBlocks.isNotEmpty()) {
                    showCodeSelectionDialog(codeBlocks)
                } else {
                    showToast(getString(R.string.no_code_blocks))
                }
            }

            shareButton.setOnClickListener {
                val text = editText.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    performHapticFeedback()
                    shareAsFile()
                } else {
                    showToast(getString(R.string.nothing_to_share))
                }
            }

            // Обновление состояния кнопок при изменении текста
            editText.doAfterTextChanged {
                updateButtonsState()
            }
        }
    }

    private fun performHapticFeedback() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            if (clipboard == null) {
                showError(getString(R.string.clipboard_not_available))
                return
            }

            clipboard?.setPrimaryClip(ClipData.newPlainText("text", text))
            showToast(getString(R.string.copied))
            updateButtonsState()
        } catch (e: Exception) {
            showError(getString(R.string.error_clipboard))
        }
    }

    private fun pasteFromClipboard() {
        try {
            if (clipboard == null) {
                showError(getString(R.string.clipboard_not_available))
                return
            }

            val clip = clipboard?.primaryClip
            when {
                clip == null || !clipboard?.hasPrimaryClip()!! -> {
                    showToast(getString(R.string.clipboard_empty))
                }

                clip.itemCount > 0 -> {
                    val text = clip.getItemAt(0).text
                    if (!text.isNullOrBlank()) {
                        binding.editText.setText(text)
                        showToast(getString(R.string.pasted))
                    } else {
                        showToast(getString(R.string.clipboard_empty))
                    }
                }

                else -> {
                    showToast(getString(R.string.clipboard_empty))
                }
            }
            updateButtonsState()
        } catch (e: Exception) {
            showError(getString(R.string.error_clipboard))
        }
    }

    private fun showToast(message: String) {
        // Отменяем предыдущий Toast
        currentToast?.cancel()
        // Создаем и показываем новый
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
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
                showToast("Открыт файл типа: $mimeType")
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

            // Создаем файл в кэш-директории
            val file = File(cacheDir, fileName).apply {
                writeText(text)
            }

            // Получаем URI через FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            // Создаем intent для шаринга
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (fileExtension == "md") "text/markdown" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Запускаем диалог выбора приложения для шаринга
            val chooserIntent = Intent.createChooser(shareIntent, "Поделиться файлом через")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Предоставляем временные права доступа всем потенциальным получателям
            val resInfoList = packageManager.queryIntentActivities(chooserIntent, 0)
            resInfoList.forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            startActivity(chooserIntent)
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

    private fun updateUI() {
        binding.editText.setText(receivedText)
        updateButtonsState()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.error))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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

    private fun isClipboardNotEmpty(): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.hasPrimaryClip() &&
                    clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true &&
                    !clipboard.primaryClip?.getItemAt(0)?.text.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun updateButtonsState() {
        with(binding) {
            val text = editText.text?.toString() ?: ""
            val hasText = text.isNotBlank()

            // Обновляем состояния кнопок
            copyButton.isEnabled = hasText
            clearButton.isEnabled = hasText
            // Кнопка вставки всегда активна

            // Кнопка извлечения кода активна если есть текст
            extractCodeButton.isEnabled = hasText

            // Кнопка отправки активна только если есть текст
            shareButton.isEnabled = hasText
        }
    }

    override fun onResume() {
        super.onResume()
        // Принудительно проверяем состояние буфера при возвращении в приложение
        updateButtonsState()
    }
}