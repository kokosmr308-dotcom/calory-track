package ru.schetchikkaloriy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class FoodEntry(
    val id: Long, val date: String, val meal: String, val name: String,
    val grams: Double, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalorieApp() }
    }
}

@Composable
fun CalorieApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calorie_diary", 0) }
    var entries by remember {
        mutableStateOf(loadEntries(prefs.getString("entries", "[]") ?: "[]"))
    }
    var goal by remember { mutableStateOf(prefs.getInt("goal", 2000).toString()) }
    var showAdd by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    val today = LocalDate.now().toString()
    val todays = entries.filter { it.date == today }
    val kcal = todays.sumOf { it.kcal }
    val protein = todays.sumOf { it.protein }
    val fat = todays.sumOf { it.fat }
    val carbs = todays.sumOf { it.carbs }
    val goalValue = goal.toDoubleOrNull()?.takeIf { it > 0 } ?: 2000.0

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Счётчик калорий", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = {
                            dark = !dark
                            prefs.edit().putBoolean("dark", dark).apply()
                        }) { Text(if (dark) "Светлая" else "Тёмная") }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить продукт")
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Сегодня", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(kcal.toInt().toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                Text(" / ${goalValue.toInt()} ккал", modifier = Modifier.padding(bottom = 10.dp))
                            }
                            LinearProgressIndicator(
                                progress = { (kcal / goalValue).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Macro("Белки", protein, "г")
                                Macro("Жиры", fat, "г")
                                Macro("Углеводы", carbs, "г")
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = goal, onValueChange = {
                            goal = it.filter(Char::isDigit)
                            prefs.edit().putInt("goal", goal.toIntOrNull() ?: 2000).apply()
                        },
                        label = { Text("Цель на день, ккал") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    val distinctFoods = todays.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.distinct().size
                    val fruitVegWords = listOf("яблок", "банан", "груш", "апельс", "мандар", "ягод", "клубник", "черник", "морков", "огур", "томат", "помидор", "капуст", "брокколи", "перец", "салат", "шпинат", "кабач", "тыкв", "свекл", "зелень", "авокадо")
                    val hasFruitOrVeg = todays.any { e -> fruitVegWords.any { e.name.lowercase().contains(it) } }
                    val hasProteinSource = todays.any { it.protein >= 10.0 }
                    val mealCount = todays.map { it.meal }.distinct().size
                    val quests = listOf(
                        "Запиши хотя бы один приём пищи" to todays.isNotEmpty(),
                        "Добавь фрукт или овощ" to hasFruitOrVeg,
                        "Включи продукт — источник белка" to hasProteinSource,
                        "Попробуй разнообразие: 3 разных продукта" to (distinctFoods >= 3),
                        "Отметь питание в двух разных приёмах" to (mealCount >= 2)
                    )
                    val completed = quests.count { it.second }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Дейлики", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Маленькие шаги к более разнообразному питанию",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("$completed/${quests.size}", fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { completed.toFloat() / quests.size },
                                modifier = Modifier.fillMaxWidth()
                            )
                            quests.forEach { (label, done) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (done) "✓" else "○",
                                        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(label,
                                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text("Задания обновляются каждый день. Это мягкие подсказки, а не обязательные нормы.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Text("Дневник питания", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                val meals = listOf("Завтрак", "Обед", "Ужин", "Перекус")
                items(meals) { meal ->
                    val mealEntries = todays.filter { it.meal == meal }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(meal, fontWeight = FontWeight.SemiBold)
                                Text("${mealEntries.sumOf { it.kcal }.toInt()} ккал")
                            }
                            if (mealEntries.isEmpty()) {
                                Text("Пока ничего не добавлено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                mealEntries.forEach { entry ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.name)
                                            Text("${entry.grams.toInt()} г · ${entry.kcal.toInt()} ккал · Б ${entry.protein.toInt()} Ж ${entry.fat.toInt()} У ${entry.carbs.toInt()}",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = {
                                            entries = entries.filterNot { it.id == entry.id }
                                            saveEntries(prefs, entries)
                                        }) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Данные хранятся локально на этом телефоне.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showAdd) {
            AddFoodDialog(onDismiss = { showAdd = false }, onSave = { entry ->
                entries = entries + entry.copy(id = System.currentTimeMillis(), date = today)
                saveEntries(prefs, entries)
                showAdd = false
            })
        }
    }
}

@Composable
fun Macro(label: String, amount: Double, unit: String) {
    Column {
        Text("${amount.toInt()} $unit", fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AddFoodDialog(onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
    var meal by remember { mutableStateOf("Завтрак") }
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("0") }
    var p by remember { mutableStateOf("0") }
    var f by remember { mutableStateOf("0") }
    var c by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить продукт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = meal, onValueChange = {}, readOnly = true,
                        label = { Text("Приём пищи") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Завтрак", "Обед", "Ужин", "Перекус").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { meal = it; expanded = false })
                        }
                    }
                }
                Field("Название продукта", name, { name = it }, false)
                Field("Вес, г", grams, { grams = it }, true)
                Text("Пищевая ценность на 100 г", style = MaterialTheme.typography.labelMedium)
                Field("Калории, ккал", kcal, { kcal = it }, true)
                Field("Белки, г", p, { p = it }, true)
                Field("Жиры, г", f, { f = it }, true)
                Field("Углеводы, г", c, { c = it }, true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = grams.toDoubleOrNull()
                val k = kcal.toDoubleOrNull()
                val pp = p.toDoubleOrNull()
                val ff = f.toDoubleOrNull()
                val cc = c.toDoubleOrNull()
                if (name.isNotBlank() && g != null && g > 0 && k != null && pp != null && ff != null && cc != null) {
                    val factor = g / 100.0
                    onSave(FoodEntry(0, "", meal, name.trim(), g, k * factor, pp * factor, ff * factor, cc * factor))
                }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit, numeric: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth()
    )
}

fun saveEntries(prefs: android.content.SharedPreferences, entries: List<FoodEntry>) {
    val array = JSONArray()
    entries.forEach { e ->
        array.put(JSONObject().apply {
            put("id", e.id); put("date", e.date); put("meal", e.meal); put("name", e.name)
            put("grams", e.grams); put("kcal", e.kcal); put("protein", e.protein); put("fat", e.fat); put("carbs", e.carbs)
        })
    }
    prefs.edit().putString("entries", array.toString()).apply()
}

fun loadEntries(raw: String): List<FoodEntry> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        FoodEntry(o.getLong("id"), o.getString("date"), o.getString("meal"), o.getString("name"),
            o.getDouble("grams"), o.getDouble("kcal"), o.getDouble("protein"), o.getDouble("fat"), o.getDouble("carbs"))
    }
} catch (_: Exception) { emptyList() }
