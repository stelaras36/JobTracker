package com.stelios.jobtracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* ---------------- DataStore ---------------- */

private val Context.dataStore by preferencesDataStore(name = "jobtracker_prefs")
private val JOBS_JSON_KEY = stringPreferencesKey("jobs_json")

private fun encodeJobs(jobs: List<JobItem>): String {
    val arr = JSONArray()
    jobs.forEach {
        arr.put(
            JSONObject().apply {
                put("title", it.title)
                put("company", it.company)
                put("status", it.status)
            }
        )
    }
    return arr.toString()
}

private fun decodeJobs(json: String): List<JobItem> {
    val arr = JSONArray(json)
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        JobItem(
            title = o.optString("title"),
            company = o.optString("company"),
            status = o.optString("status", "Wishlist")
        )
    }
}

private suspend fun saveJobs(context: Context, jobs: List<JobItem>) {
    context.dataStore.edit { it[JOBS_JSON_KEY] = encodeJobs(jobs) }
}

private suspend fun loadJobs(context: Context): String? =
    context.dataStore.data.first()[JOBS_JSON_KEY]

/* ---------------- Activity ---------------- */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JobTrackerApp() }
    }
}

/* ---------------- UI ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobTrackerApp() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Wishlist") }

    val statuses = listOf("Wishlist", "Applied", "Interview", "Offer", "Rejected")

    var statusExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filterOptions = listOf("All") + statuses
    var selectedFilter by remember { mutableStateOf("All") }
    var filterExpanded by remember { mutableStateOf(false) }

    val jobs = remember { mutableStateListOf<JobItem>() }

    /* Load saved jobs */
    LaunchedEffect(Unit) {
        val saved = loadJobs(context)
        if (saved.isNullOrBlank()) {
            val demo = listOf(
                JobItem("Android Developer", "Yodeck", "Applied"),
                JobItem("Junior Software Engineer", "Netcompany", "Wishlist"),
                JobItem("Backend Developer Intern", "Intralot", "Interview")
            )
            jobs.addAll(demo)
            saveJobs(context, jobs)
        } else {
            jobs.addAll(decodeJobs(saved))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("JobTracker") }) }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            /* -------- Add Job -------- */

            Text("Add new job", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Job title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = !statusExpanded }
            ) {
                OutlinedTextField(
                    value = status,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                DropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false }
                ) {
                    statuses.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                status = it
                                statusExpanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (title.isNotBlank() && company.isNotBlank()) {
                        jobs.add(0, JobItem(title, company, status))
                        title = ""
                        company = ""
                        status = "Wishlist"
                        scope.launch { saveJobs(context, jobs) }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Add")
            }

            HorizontalDivider()

            /* -------- Filter -------- */

            ExposedDropdownMenuBox(
                expanded = filterExpanded,
                onExpandedChange = { filterExpanded = !filterExpanded }
            ) {
                OutlinedTextField(
                    value = selectedFilter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(filterExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    filterOptions.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                selectedFilter = it
                                filterExpanded = false
                            }
                        )
                    }
                }
            }

            /* -------- Search -------- */

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search (title or company)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            /* -------- Visible jobs -------- */

            val query = searchQuery.lowercase().trim()

            val filteredByStatus =
                if (selectedFilter == "All") jobs
                else jobs.filter { it.status == selectedFilter }

            val visibleJobs =
                if (query.isBlank()) filteredByStatus
                else filteredByStatus.filter {
                    it.title.lowercase().contains(query) ||
                            it.company.lowercase().contains(query)
                }

            Text(
                text = "My jobs (${visibleJobs.size})",
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleJobs) { job ->
                    JobCard(
                        job = job,
                        statuses = statuses,
                        onStatusChange = {
                            val i = jobs.indexOf(job)
                            jobs[i] = job.copy(status = it)
                            scope.launch { saveJobs(context, jobs) }
                        },
                        onDelete = {
                            jobs.remove(job)
                            scope.launch { saveJobs(context, jobs) }
                        }
                    )
                }
            }
        }
    }
}

/* ---------------- Job Card ---------------- */

@Composable
fun JobCard(
    job: JobItem,
    statuses: List<String>,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.padding(12.dp)) {
            Text(job.title, fontWeight = FontWeight.SemiBold)
            Text(job.company)

            Row(verticalAlignment = Alignment.CenterVertically) {

                AssistChip(
                    onClick = { expanded = true },
                    label = { Text(job.status) }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    statuses.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                expanded = false
                                onStatusChange(it)
                            }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

/* ---------------- Model ---------------- */

data class JobItem(
    val title: String,
    val company: String,
    val status: String
)
