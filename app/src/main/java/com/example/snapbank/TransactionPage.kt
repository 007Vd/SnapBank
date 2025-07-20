package com.example.snapbank

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import com.google.firebase.firestore.Query







@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionPage(uid: String) {
    var allTransactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var filteredTransactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        db.collection("users").document(uid)
            .collection("transactions")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                allTransactions = docs.mapNotNull { doc ->
                    val type = doc.getString("type") ?: return@mapNotNull null
                    val amount = doc.getLong("amount") ?: return@mapNotNull null
                    val timestamp = doc.getLong("timestamp") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: ""
                    val phone = doc.getString("phone") ?: ""
                    mapOf(
                        "type" to type,
                        "amount" to amount,
                        "timestamp" to timestamp,
                        "name" to name,
                        "phone" to phone
                    )
                }
                filteredTransactions = allTransactions
                isLoading = false
            }
            .addOnFailureListener { exception ->
                errorMessage = "Failed to load transactions: ${exception.message}"
                isLoading = false
            }
    }

    LaunchedEffect(searchQuery, selectedFilter, allTransactions) {
        filteredTransactions = allTransactions.filter { transaction ->
            val type = (transaction["type"] as String).lowercase()
            val amount = (transaction["amount"] as Long).toString()
            val name = (transaction["name"] as String).lowercase()
            val phone = (transaction["phone"] as String).lowercase()
            val timeFilter = when (selectedFilter) {
                "Last Hour" -> System.currentTimeMillis() - 3600_000
                "Last Day" -> System.currentTimeMillis() - 86_400_000
                "Last Week" -> System.currentTimeMillis() - 7 * 86_400_000
                "Last Month" -> System.currentTimeMillis() - 30L * 86_400_000
                else -> 0L
            }
            val timestamp = transaction["timestamp"] as Long
            (searchQuery.isBlank() || type.contains(searchQuery) || amount.contains(searchQuery) || name.contains(searchQuery) || phone.contains(searchQuery)) &&
                    (timeFilter == 0L || timestamp >= timeFilter)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Text("📜 Transaction History", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name, phone, type, or amount") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        val filters = listOf("All", "Last Hour", "Last Day", "Last Week", "Last Month")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            errorMessage.isNotEmpty() -> Text("❌ $errorMessage", color = Color.Red)
            filteredTransactions.isEmpty() -> Text("💳 No transactions found", color = Color.Gray)
            else -> LazyColumn {
                items(filteredTransactions) { transaction ->
                    TransactionCard(transaction)
                }
            }
        }
    }
}
@Composable
fun TransactionCard(transaction: Map<String, Any>) {
    val type = transaction["type"] as String
    val amount = transaction["amount"] as Long
    val name = transaction["name"] as String
    val phone = transaction["phone"] as String
    val timestamp = transaction["timestamp"] as Long
    val isSent = type.startsWith("Sent", ignoreCase = true)
    val timeAgo = getTimeAgo(timestamp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSent) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(if (isSent) R.drawable.ic_money_sent else R.drawable.ic_money_received),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isSent) type else type,
                    fontWeight = FontWeight.Bold
                )
                Text("₹$amount", color = if (isSent) Color.Red else Color.Green)
                if (phone.isNotEmpty()) Text("📞 $phone", fontSize = 12.sp, color = Color.Gray)
                Text(timeAgo, fontSize = 12.sp, color = Color.DarkGray)
            }
        }
    }
}


fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 30 -> "$days days ago"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
