package com.example.snapbank

import android.widget.EditText
import android.text.InputFilter
import android.text.InputType
import android.app.Activity
import com.google.firebase.firestore.SetOptions
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapbank.ui.theme.SnapBankTheme
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import android.app.AlertDialog



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this

        enableEdgeToEdge()
        setContent {
            SnapBankTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppFlow(activity)
                }
            }
        }
    }
}

lateinit var storedVerificationId: String

@Composable
fun AppFlow(activity: Activity) {
    var currentScreen by remember { mutableStateOf<String?>(null) }
    var uid by remember { mutableStateOf("") }

    // 🔁 Check if user is already signed in
    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentScreen = if (currentUser != null) {
            uid = currentUser.uid
            "dashboard"
        } else {
            "login"
        }
    }

    // ⏳ Show loading spinner while auth state is being determined
    if (currentScreen == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // ✅ Flags to trigger side-effects
    var shouldCheckPin by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    // ✅ Main navigation
    when (currentScreen) {

        "login" -> PhoneLoginScreen(activity) { userId ->
            uid = userId
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        currentScreen = "details"
                    } else if (!doc.contains("pin")) {
                        PromptToSetPin(userId, activity) {
                            currentScreen = "dashboard"
                        }
                    } else {
                        currentScreen = "dashboard"
                    }
                }
                .addOnFailureListener {
                    currentScreen = "details"
                }
        }


        "details" -> UserDetailsScreen(uid) {
            // After user enters details, trigger Firestore PIN check
            shouldCheckPin = true
        }

        "dashboard" -> MainNavigationScreen(uid)
    }

    // 🔍 PIN check logic after "details"
    LaunchedEffect(shouldCheckPin) {
        if (shouldCheckPin) {
            shouldCheckPin = false
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.contains("pin")) {
                        showPinDialog = true
                    } else {
                        currentScreen = "dashboard"
                    }
                }
        }
    }

    // ✅ Show PIN dialog *safely* in Compose
    if (showPinDialog) {
        SideEffect {
            PromptToSetPin(uid, activity) {
                currentScreen = "dashboard"
            }
            showPinDialog = false
        }
    }
}
fun PromptToSetPin(userId: String, context: Context, onSuccess: () -> Unit) {
    val input = EditText(context)
    input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    input.filters = arrayOf(InputFilter.LengthFilter(4))

    AlertDialog.Builder(context)
        .setTitle("Set a 4-digit PIN")
        .setView(input)
        .setCancelable(false)
        .setPositiveButton("Save") { dialog, _ ->
            val pin = input.text.toString()
            if (pin.length == 4) {
                val hashed = hashPin(pin)
                FirebaseFirestore.getInstance().collection("users")
                    .document(userId)
                    .update("pin", hashed)
                    .addOnSuccessListener {
                        Toast.makeText(context, "PIN set successfully!", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }
            } else {
                Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        .show()
}

fun hashPin(pin: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray())
        .joinToString("") { "%02x".format(it) }
}


fun verifyPin(context: Context, userId: String, onSuccess: () -> Unit) {
    val input = EditText(context)
    input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    input.filters = arrayOf(InputFilter.LengthFilter(4))

    AlertDialog.Builder(context)
        .setTitle("Enter your 4-digit PIN")
        .setView(input)
        .setCancelable(true)
        .setPositiveButton("Verify") { dialog, _ ->
            val enteredPin = input.text.toString()
            val hashedEnteredPin = hashPin(enteredPin)

            FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    val correctPin = doc.getString("pin")
                    if (hashedEnteredPin == correctPin) {
                        onSuccess()
                    } else {
                        Toast.makeText(context, "❌ Incorrect PIN", Toast.LENGTH_SHORT).show()
                    }
                }
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

@Composable
fun MainNavigationScreen(uid: String) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabItems = listOf("Dashboard", "Transactions", "Send", "Settings")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabItems.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            when (label) {
                                "Dashboard" -> Icon(Icons.Filled.Home, contentDescription = label)
                                "Transactions" -> Icon(Icons.Filled.List, contentDescription = label)
                                "Send" -> Icon(Icons.Filled.Send, contentDescription = label)
                                "Settings" -> Icon(Icons.Filled.Settings, contentDescription = label)
                                else -> Icon(Icons.Filled.Info, contentDescription = label)
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(uid)
                1 -> TransactionsScreen(uid)
                2 -> SendMoneyScreen(uid)
                3 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun TransactionsScreen(uid: String) {
    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        db.collection("users").document(uid)
            .collection("transactions")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                transactions = docs.mapNotNull { doc ->
                    val type = doc.getString("type") ?: return@mapNotNull null
                    val amount = doc.getLong("amount") ?: return@mapNotNull null
                    val timestamp = doc.getLong("timestamp") ?: return@mapNotNull null
                    mapOf(
                        "type" to type,
                        "amount" to amount,
                        "timestamp" to timestamp
                    )
                }
                isLoading = false
            }
            .addOnFailureListener { exception ->
                errorMessage = "Failed to load transactions: ${exception.message}"
                isLoading = false
                Log.e("TRANSACTIONS", "Error loading transactions", exception)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📜 Transaction History", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage.isNotEmpty() -> {
                Text(
                    text = "❌ $errorMessage",
                    color = MaterialTheme.colorScheme.error
                )
            }
            transactions.isEmpty() -> {
                Text(
                    text = "💳 No transactions yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                transactions.forEach { transaction ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = transaction["type"] as String,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "₹${transaction["amount"]}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun SendMoneyScreen(senderUid: String) {
    var recipient by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📤 Send Money", fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = recipient,
            onValueChange = {
                recipient = it
                status = ""
            },
            label = { Text("Recipient Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                status = ""
            },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val amount = amountText.toLongOrNull()
                when {
                    recipient.isBlank() -> {
                        status = "⚠ Please enter recipient username"
                    }
                    recipient.length < 3 -> {
                        status = "⚠ Username must be at least 3 characters"
                    }
                    amount == null || amount <= 0 -> {
                        status = "⚠ Please enter a valid amount"
                    }
                    amount > 100000 -> {
                        status = "⚠ Maximum transfer limit is ₹1,00,000"
                    }
                    else -> {
                        // ✅ Secure PIN prompt before actual transfer
                        verifyPin(context, senderUid) {
                            sending = true
                            performMoneyTransfer(senderUid, recipient, amount, db, context) { success, message ->
                                sending = false
                                status = message
                                if (success) {
                                    recipient = ""
                                    amountText = ""
                                }
                            }
                        }
                    }
                }
            },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sending) "Sending..." else "Send Money")
        }

        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = status,
                color = if (status.startsWith("✅")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}


private fun performMoneyTransfer(
    senderUid: String,
    recipient: String,
    amount: Long,
    db: FirebaseFirestore,
    context: Context,
    onComplete: (Boolean, String) -> Unit
) {
    db.collection("users")
        .whereEqualTo("username", recipient)
        .get()
        .addOnSuccessListener { docs ->
            if (docs.isEmpty) {
                onComplete(false, "❌ User not found")
                return@addOnSuccessListener
            }

            val recipientDoc = docs.first()
            val recipientUid = recipientDoc.id

            if (senderUid == recipientUid) {
                onComplete(false, "❌ Cannot send money to yourself")
                return@addOnSuccessListener
            }

            val senderRef = db.collection("users").document(senderUid)
            val recipientRef = db.collection("users").document(recipientUid)

            db.runTransaction { transaction ->
                val senderSnapshot = transaction.get(senderRef)
                val recipientSnapshot = transaction.get(recipientRef)

                val senderBalance = senderSnapshot.getLong("balance") ?: 0L
                val recipientBalance = recipientSnapshot.getLong("balance") ?: 0L

                if (senderBalance < amount) {
                    throw Exception("Insufficient balance")
                }

                // Update balances
                transaction.update(senderRef, "balance", senderBalance - amount)
                transaction.update(recipientRef, "balance", recipientBalance + amount)

                // Create transaction records
                val timestamp = System.currentTimeMillis()
                val senderTxn = hashMapOf(
                    "type" to "Sent to $recipient",
                    "amount" to amount,
                    "timestamp" to timestamp
                )
                val recipientTxn = hashMapOf(
                    "type" to "Received from ${senderSnapshot.getString("username") ?: "Unknown"}",
                    "amount" to amount,
                    "timestamp" to timestamp
                )

                transaction.set(senderRef.collection("transactions").document(), senderTxn)
                transaction.set(recipientRef.collection("transactions").document(), recipientTxn)
            }.addOnSuccessListener {
                val successMessage = "✅ ₹$amount sent to $recipient"
                onComplete(true, successMessage)
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { exception ->
                val errorMessage = when (exception) {
                    is FirebaseFirestoreException -> {
                        when (exception.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "❌ Permission denied"
                            FirebaseFirestoreException.Code.UNAVAILABLE -> "❌ Service unavailable"
                            else -> "❌ ${exception.message}"
                        }
                    }
                    else -> "❌ ${exception.message}"
                }
                onComplete(false, errorMessage)
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                Log.e("SEND_MONEY", "Transfer failed", exception)
            }
        }
        .addOnFailureListener { exception ->
            val errorMessage = "❌ Failed to find user: ${exception.message}"
            onComplete(false, errorMessage)
            Log.e("SEND_MONEY", "User lookup failed", exception)
        }
}


@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚙ Settings", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(24.dp))

        // User info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("👤 Account Info", fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Name: ${user?.displayName ?: "Unknown"}")
                Text("Phone: ${user?.phoneNumber ?: "Unknown"}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(context, "✅ Logged out successfully", Toast.LENGTH_SHORT).show()
                (context as? Activity)?.recreate()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("🚪 Logout")
        }
    }
}

fun addMoney(uid: String, amount: Long, onComplete: (Boolean, String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val userRef = db.collection("users").document(uid)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(userRef)
        val currentBalance = snapshot.getLong("balance") ?: 0L
        transaction.update(userRef, "balance", currentBalance + amount)

        val txn = hashMapOf(
            "type" to "Money Added",
            "amount" to amount,
            "timestamp" to System.currentTimeMillis()
        )
        transaction.set(userRef.collection("transactions").document(), txn)
    }.addOnSuccessListener {
        onComplete(true, "✅ ₹$amount added successfully!")
    }.addOnFailureListener { exception ->
        onComplete(false, "❌ Failed to add money: ${exception.message}")
        Log.e("ADD_MONEY", "Failed to add money", exception)
    }
}
@Composable
fun DashboardScreen(uid: String) {
    val user = FirebaseAuth.getInstance().currentUser
    val name = user?.displayName ?: "User"

    val db = FirebaseFirestore.getInstance()
    val balanceState = remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var addAmount by remember { mutableStateOf("") }
    var addingMoney by remember { mutableStateOf(false) }
    var showBalance by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    // 👂 Real-time listener for balance
    LaunchedEffect(uid) {
        val docRef = db.collection("users").document(uid)
        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("DASHBOARD", "❌ Listen failed: ${e.message}")
                balanceState.value = -1L
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val updatedBalance = snapshot.getLong("balance") ?: 0L
                balanceState.value = updatedBalance
                Log.d("DASHBOARD", "✅ Balance updated: ₹$updatedBalance")
            } else {
                balanceState.value = -1L
                Log.w("DASHBOARD", "⚠ Snapshot is null or does not exist")
            }
        }
    }

    val gradient = Brush.verticalGradient(colors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text("Hello, $name 👋", fontSize = 24.sp, color = Color(0xFF006064))
            Spacer(modifier = Modifier.height(16.dp))

            when (val balance = balanceState.value) {
                null -> CircularProgressIndicator()
                -1L -> Text("❌ Error loading balance", color = Color.Red)
                else -> {
                    Text("💰 Current Balance", fontSize = 16.sp, color = Color(0xFF006064))

                    if (showBalance) {
                        Text("₹$balance", fontSize = 32.sp, color = Color(0xFF004D40))
                    } else {
                        Button(
                            onClick = {
                                verifyPin(context, uid) {
                                    showBalance = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40))
                        ) {
                            Text("🔒 Tap to View Balance", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ➕ Add Money Button
            Button(
                onClick = { showAddMoneyDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
            ) {
                Text("➕ Add Money", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔁 Change PIN Button
            Button(
                onClick = { showChangePinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
            ) {
                Text("🔁 Change PIN", fontSize = 16.sp, color = Color.White)
            }
        }
    }

    // 💵 Add Money Dialog
    if (showAddMoneyDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!addingMoney) {
                    showAddMoneyDialog = false
                    addAmount = ""
                }
            },
            title = { Text("💰 Add Money") },
            text = {
                Column {
                    Text("Enter amount to add to your wallet:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addAmount,
                        onValueChange = { addAmount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !addingMoney
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = addAmount.toLongOrNull()
                        when {
                            amount == null || amount <= 0 -> {
                                Toast.makeText(context, "⚠ Enter a valid amount", Toast.LENGTH_SHORT).show()
                            }
                            amount > 50000 -> {
                                Toast.makeText(context, "⚠ Maximum add limit is ₹50,000", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                addingMoney = true
                                addMoney(uid, amount) { success, message ->
                                    addingMoney = false
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        showAddMoneyDialog = false
                                        addAmount = ""
                                    }
                                }
                            }
                        }
                    },
                    enabled = !addingMoney
                ) {
                    Text(if (addingMoney) "Adding..." else "Add Money")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddMoneyDialog = false
                        addAmount = ""
                    },
                    enabled = !addingMoney
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 🔁 Change PIN trigger
    if (showChangePinDialog) {
        changePin(context, uid)
        showChangePinDialog = false
    }
}
fun changePin(context: Context, userId: String) {
    val currentInput = EditText(context)
    currentInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    currentInput.filters = arrayOf(InputFilter.LengthFilter(4))

    AlertDialog.Builder(context)
        .setTitle("🔐 Enter Current PIN")
        .setView(currentInput)
        .setCancelable(false)
        .setPositiveButton("Next") { dialog, _ ->
            val entered = currentInput.text.toString()
            val hashed = hashPin(entered)

            FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    val correctHash = doc.getString("pin")
                    if (hashed == correctHash) {
                        dialog.dismiss()

                        // Prompt for new PIN
                        val newInput = EditText(context)
                        newInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        newInput.filters = arrayOf(InputFilter.LengthFilter(4))

                        AlertDialog.Builder(context)
                            .setTitle("🆕 Enter New PIN")
                            .setView(newInput)
                            .setCancelable(false)
                            .setPositiveButton("Save") { d2, _ ->
                                val newPin = newInput.text.toString()
                                if (newPin.length == 4) {
                                    val newHash = hashPin(newPin)
                                    FirebaseFirestore.getInstance().collection("users")
                                        .document(userId)
                                        .update("pin", newHash)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "✅ PIN changed", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    Toast.makeText(context, "❌ PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                                }
                                d2.dismiss()
                            }
                            .setNegativeButton("Cancel") { d2, _ -> d2.dismiss() }
                            .show()

                    } else {
                        Toast.makeText(context, "❌ Incorrect current PIN", Toast.LENGTH_SHORT).show()
                    }
                }
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        .show()
}


@Composable
fun PhoneLoginScreen(activity: Activity, onLoginSuccess: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val gradient = Brush.verticalGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))

    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📱 Welcome to SnapBank", fontSize = 24.sp, color = Color(0xFF4A00E0))

            Spacer(modifier = Modifier.height(16.dp))

            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = if (message.startsWith("✅")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!otpSent) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        message = "" // Clear message on input change
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91XXXXXXXXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        when {
                            phone.isBlank() -> {
                                message = "⚠ Enter a valid phone number"
                                return@Button
                            }
                            !phone.startsWith("+91") -> {
                                message = "⚠ Phone number must start with +91"
                                return@Button
                            }
                            phone.length != 13 -> {
                                message = "⚠ Phone number must be 10 digits after +91"
                                return@Button
                            }
                            else -> {
                                loading = true
                                message = ""
                                sendVerificationCode(phone, activity,
                                    onSent = {
                                        loading = false
                                        otpSent = true
                                        message = "✅ OTP sent successfully"
                                    },
                                    onError = { error ->
                                        message = "❌ $error"
                                        loading = false
                                    }
                                )
                            }
                        }
                    },
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Sending..." else "Send OTP")
                }
            } else {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = {
                        otpCode = it
                        message = "" // Clear message on input change
                    },
                    label = { Text("Enter OTP") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        when {
                            otpCode.length != 6 -> {
                                message = "⚠ OTP must be 6 digits"
                                return@Button
                            }
                            else -> {
                                loading = true
                                message = ""
                                verifyCode(otpCode,
                                    onSuccess = { uid ->
                                        loading = false
                                        message = "✅ Login successful"
                                        onLoginSuccess(uid)
                                    },
                                    onFailure = { error ->
                                        message = "❌ $error"
                                        loading = false
                                    }
                                )
                            }
                        }
                    },
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Verifying..." else "Verify OTP")
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        otpSent = false
                        otpCode = ""
                        message = ""
                    }
                ) {
                    Text("← Back to Phone Number")
                }
            }
        }
    }
}

@Composable
fun UserDetailsScreen(uid: String, onSubmit: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text("Complete Your Profile", fontSize = 20.sp, color = Color(0xFF2F80ED))
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    message = ""  // clear error on edit
                },
                label = { Text("Your Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    message = ""  // clear error on edit
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    when {
                        name.isBlank() -> {
                            message = "⚠ Name is required"
                            return@Button
                        }
                        name.length < 2 -> {
                            message = "⚠ Name must be at least 2 characters"
                            return@Button
                        }
                        username.isBlank() -> {
                            message = "⚠ Username is required"
                            return@Button
                        }
                        username.length < 3 -> {
                            message = "⚠ Username must be at least 3 characters"
                            return@Button
                        }
                        !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> {
                            message = "⚠ Username can only contain letters, numbers, and underscores"
                            return@Button
                        }
                        else -> {
                            loading = true
                            message = ""
                            saveUserDataToFirestore(uid, name, username) { success, errorMsg ->
                                loading = false
                                if (success) {
                                    Log.d("DEBUG", "Moving to next screen")
                                    Toast.makeText(context, "✅ Profile created successfully", Toast.LENGTH_SHORT).show()
                                    onSubmit()
                                } else {
                                    message = errorMsg
                                }
                            }
                        }
                    }
                },
                enabled = !loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Saving..." else "Create Profile")
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

fun sendVerificationCode(
    phoneNumber: String,
    activity: Activity,
    onSent: () -> Unit,
    onError: (String) -> Unit
) {
    val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
        .setPhoneNumber(phoneNumber)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d("PHONE_AUTH", "✅ Auto-verification complete.")
                signInWithPhoneAuthCredential(credential, {}, { Log.e("PHONE_AUTH", it) })
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("PHONE_AUTH", "❌ Verification failed: ${e.message}")
                onError("Verification failed: ${e.message}")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                Log.d("PHONE_AUTH", "📩 OTP sent to $phoneNumber.")
                storedVerificationId = verificationId
                onSent()
            }
        }).build()

    PhoneAuthProvider.verifyPhoneNumber(options)
}

fun verifyCode(code: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
    try {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId, code)
        signInWithPhoneAuthCredential(credential, onSuccess, onFailure)
    } catch (e: Exception) {
        onFailure("Invalid OTP: ${e.message}")
    }
}

fun signInWithPhoneAuthCredential(
    credential: PhoneAuthCredential,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    FirebaseAuth.getInstance().signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = task.result?.user?.uid ?: "Unknown"
                Log.d("PHONE_AUTH", "✅ Login successful! UID: $uid")
                onSuccess(uid)
            } else {
                val error = task.exception?.message ?: "Unknown error"
                Log.e("PHONE_AUTH", "❌ Login failed: $error")
                onFailure("Login failed: $error")
            }
        }
}

fun saveUserDataToFirestore(
    uid: String,
    name: String,
    username: String,
    onComplete: (Boolean, String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()

    // First check if username already exists
    db.collection("users")
        .whereEqualTo("username", username)
        .get()
        .addOnSuccessListener { documents ->
            val takenBySomeoneElse = documents.any { it.id != uid }
            if (takenBySomeoneElse) {
                onComplete(false, "❌ Username already taken")
                return@addOnSuccessListener
            }


            // Username is available, proceed with saving
            val userMap = hashMapOf(
                "name" to name,
                "username" to username,
                "balance" to 1000L  // 🪙 Initial balance
            )

            db.collection("users")
                .document(uid)
                .set(userMap, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FIRESTORE", "✅ User profile saved for UID: $uid")

                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()

                    currentUser?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("AUTH_PROFILE", "✅ FirebaseAuth display name updated")
                            } else {
                                Log.e("AUTH_PROFILE", "❌ Failed to update display name: ${task.exception?.message}")
                            }
                            onComplete(true, "")
                        } ?: run {
                        Log.e("AUTH_PROFILE", "❌ currentUser is null")
                        onComplete(true, "")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FIRESTORE", "❌ Failed to save user: ${exception.message}")
                    onComplete(false, "❌ Failed to save profile: ${exception.message}")
                }
        }
        .addOnFailureListener { exception ->
            Log.e("FIRESTORE", "❌ Failed to check username: ${exception.message}")
            onComplete(false, "❌ Failed to verify username: ${exception.message}")
        }
}