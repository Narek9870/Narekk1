package com.example.narekk

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.narekk.ui.theme.NarekkTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource

// Модель нашей заметки
data class DiaryNote(
    val fileName: String,
    val timestamp: Long,
    val title: String?,
    val content: String
)

// ViewModel для работы с файлами (загрузка 1 раз в init
class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // Список записей, который будет обновляться локально (без пересканирования)
    var notes = mutableStateListOf<DiaryNote>()
        private set

    init {
        loadNotes()
    }

    private fun loadNotes() {
        // Получить путь к папке files
        val filesDir = context.filesDir
        val files = filesDir.listFiles() ?: return

        val loadedNotes = files.filter { it.name.endsWith(".txt") }.mapNotNull { file ->
            val nameParts = file.name.removeSuffix(".txt").split("_", limit = 2)
            val timestamp = nameParts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val title = nameParts.getOrNull(1)?.takeIf { it.isNotBlank() }

            // Прочитать файл 
            val content = context.openFileInput(file.name).use {
                String(it.readBytes())
            }

            DiaryNote(file.name, timestamp, title, content)
        }

        // Сортируем так, чтобы новые были сверху
        notes.addAll(loadedNotes.sortedByDescending { it.timestamp })
    }

    fun saveNote(oldFileName: String?, title: String, text: String) {
        // Если мы редактируем, удаляем старый файл
        if (oldFileName != null) {
            deleteNote(oldFileName)
        }

        val timestamp = System.currentTimeMillis()
        val safeTitle = title.trim().replace("_", " ").replace("/", "")

        // Имя файла — timestamp_[опционально_заголовок].txt
        val fileName = if (safeTitle.isNotEmpty()) {
            "${timestamp}_${safeTitle}.txt"
        } else {
            "${timestamp}_.txt"
        }

        // Записать файл 
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(text.toByteArray())
        }

        val newNote = DiaryNote(fileName, timestamp, title.takeIf { it.isNotBlank() }, text)

        // При сохранении новой записи — добавлять её вручную в начало списка 
        notes.add(0, newNote)
    }

    fun deleteNote(fileName: String) {
        // Получить File объект 
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
        // При удалении записи — удалять её из текущего списка по имени файла 
        notes.removeAll { it.fileName == fileName }
    }
}

// Простая навигация
sealed class Screen {
    object List : Screen()
    data class Edit(val note: DiaryNote?) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NarekkTheme {
                val viewModel: DiaryViewModel = viewModel()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (val screen = currentScreen) {
                            is Screen.List -> DiaryListScreen(
                                viewModel = viewModel,
                                onNavigateToEdit = { note -> currentScreen = Screen.Edit(note) }
                            )
                            is Screen.Edit -> DiaryEditScreen(
                                note = screen.note,
                                onSave = { oldFileName, title, text ->
                                    viewModel.saveNote(oldFileName, title, text)
                                    currentScreen = Screen.List
                                },
                                onBack = { currentScreen = Screen.List }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiaryListScreen(
    viewModel: DiaryViewModel,
    onNavigateToEdit: (DiaryNote?) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Мой дневник") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToEdit(null) }) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                    Spacer(Modifier.width(8.dp))
                    Text("Новая запись")
                }
            }
        }
    ) { paddingValues ->
        val notes = viewModel.notes

        if (notes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("У вас пока нет записей", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Нажмите +, чтобы создать первую", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.fileName }) { note ->
                    var showMenu by remember { mutableStateOf(false) }
                    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

                    val interactionSource = remember { MutableInteractionSource() }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = interactionSource, // Явно указываем
                                indication = LocalIndication.current,  // Явно указываем
                                onClick = { onNavigateToEdit(note) },
                                onLongClick = { showMenu = true }
                            )
                    ) {
                        Box {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (!note.title.isNullOrBlank()) {
                                    Text(
                                        text = note.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Text(
                                    text = dateFormat.format(Date(note.timestamp)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                // Показываем только первые 30-40 символов
                                Text(
                                    text = note.content,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Контекстное меню при долгом нажатии
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Удалить") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deleteNote(note.fileName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditScreen(
    note: DiaryNote?,
    onSave: (String?, String, String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var text by remember { mutableStateOf(note?.content ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note == null) "Новая запись" else "Редактировать") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Заголовок (опционально)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Ваша запись...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Занимает всё оставшееся пространство
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (text.isNotBlank() || title.isNotBlank()) {
                        onSave(note?.fileName, title, text)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить запись")
            }
        }
    }
}
