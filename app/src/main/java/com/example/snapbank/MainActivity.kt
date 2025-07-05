package com.example.snapbank

import android.app.Activity
import com.google.firebase.firestore.SetOptions

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapbank.ui.theme.SnapBankTheme
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit





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
        return  // 🔒 Prevent UI from proceeding until screen is decided
    }

    // ✅ Main navigation
    when (currentScreen) {
        "login" -> PhoneLoginScreen(activity) { userId ->
            uid = userId
            currentScreen = "details"
        }

        "details" -> UserDetailsScreen(uid) {
            currentScreen = "dashboard"
        }

//        "dashboard" -> DashboardScreen()
        "dashboard" -> MainNavigationScreen(uid)

        "home" -> HomeScreen()
    }
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
                        }
                        ,
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen()
                1 -> TransactionsScreen(uid)
                2 -> SendMoneyScreen(uid)
                3 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun TransactionsScreen(uid: String) {
    var transactions by remember { mutableStateOf<List<String>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        db.collection("users").document(uid)
            .collection("transactions")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                transactions = docs.mapNotNull {
                    val type = it.getString("type") ?: return@mapNotNull null
                    val amount = it.getLong("amount") ?: return@mapNotNull null
                    "$type ₹$amount"
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📜 Transaction History", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        transactions.forEach {
            Text(it)
            Spacer(modifier = Modifier.height(4.dp))
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

    Column(modifier = Modifier.padding(16.dp)) {
        Text("📤 Send Money", fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = { Text("Recipient Username") }
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount") }
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val amount = amountText.toLongOrNull()
                if (amount == null || recipient.isBlank()) {
                    status = "⚠️ Enter valid recipient and amount"
                    return@Button
                }

                sending = true
                db.collection("users")
                    .whereEqualTo("username", recipient)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) {
                            status = "❌ User not found"
                            sending = false
                            return@addOnSuccessListener
                        }

                        val recipientDoc = docs.first()
                        val recipientUid = recipientDoc.id

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

                            // ✅ Always update directly
                            transaction.update(senderRef, "balance", senderBalance - amount)
                            transaction.update(recipientRef, "balance", recipientBalance + amount)

                            val senderTxn = hashMapOf(
                                "type" to "Sent to $recipient",
                                "amount" to amount,
                                "timestamp" to System.currentTimeMillis()
                            )
                            val recipientTxn = hashMapOf(
                                "type" to "Received from ${senderSnapshot.getString("username") ?: "Unknown"}",
                                "amount" to amount,
                                "timestamp" to System.currentTimeMillis()
                            )

                            transaction.set(
                                senderRef.collection("transactions").document(),
                                senderTxn
                            )
                            transaction.set(
                                recipientRef.collection("transactions").document(),
                                recipientTxn
                            )
                        }.addOnSuccessListener {
                            status = "✅ ₹$amount sent to $recipient"
                            amountText = ""
                            recipient = ""
                            sending = false
                            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            status = "❌ ${it.message}"
                            sending = false
                            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        status = "❌ ${it.message}"
                        sending = false
                    }
            },
            enabled = !sending
        ) {
            Text(if (sending) "Sending..." else "Send")
        }

        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(status)
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚙️ Settings", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
            (context as? Activity)?.recreate()
        }) {
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
        onComplete(true, "Money added!")
    }.addOnFailureListener {
        onComplete(false, it.message ?: "Failed")
    }
}
@Composable
fun DashboardScreen() {
    val user = FirebaseAuth.getInstance().currentUser
    val name = user?.displayName ?: "User"
    val uid = user?.uid

    if (uid == null) {
        Log.e("DASHBOARD", "❌ UID is null. User might not be logged in.")
        return
    }

    val db = FirebaseFirestore.getInstance()
    val balanceState = remember { mutableStateOf<Long?>(null) }

    // 👂 Real-time listener for balance changes
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
                Log.d("DASHBOARD", "✅ Live update: ₹$updatedBalance")
            } else {
                balanceState.value = -1L
                Log.w("DASHBOARD", "⚠️ Snapshot is null or does not exist")
            }
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))
    )

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
                else -> Text("💰 Balance: ₹$balance", fontSize = 20.sp)
            }
        }
    }
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
                Text(message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!otpSent) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91XXXXXXXXXX") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (phone.isBlank()) {
                            message = "⚠️ Enter a valid number"
                            return@Button
                        }
                        loading = true
                        sendVerificationCode(phone, activity,
                            onSent = {
                                loading = false
                                otpSent = true
                            },
                            onError = {
                                message = it
                                loading = false
                            }
                        )
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
                    onValueChange = { otpCode = it },
                    label = { Text("Enter OTP") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (otpCode.length < 6) {
                            message = "⚠️ Invalid OTP"
                            return@Button
                        }
                        loading = true
                        verifyCode(otpCode,
                            onSuccess = {
                                loading = false
                                onLoginSuccess(it)
                            },
                            onFailure = {
                                message = it
                                loading = false
                            }
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Verifying..." else "Verify")
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
                    if (name.isBlank() || username.isBlank()) {
                        message = "⚠️ Both fields are required"
                        return@Button
                    }
                    loading = true
                    saveUserDataToFirestore(uid, name, username) {
                        Log.d("DEBUG", "Moving to next screen")
                        Toast.makeText(context, "✅ Details saved", Toast.LENGTH_SHORT).show()
                        onSubmit()
                    }

                },
                enabled = !loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Saving..." else "Submit")
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val gradient = Brush.verticalGradient(listOf(Color(0xFFee9ca7), Color(0xFFffdde1)))

    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎊 You're all set!", fontSize = 26.sp, color = Color(0xFF6A0572))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Thanks for registering with SnapBank.", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onContinue, shape = RoundedCornerShape(12.dp)) {
                Text("Continue")
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid ?: return
    var name by remember { mutableStateOf(user.displayName ?: "User") }
    var balance by remember { mutableStateOf<Long?>(null) }

    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(uid) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                name = doc.getString("name") ?: name
                balance = doc.getLong("balance") ?: 0
            }
            .addOnFailureListener {
                Toast.makeText(context, "⚠️ Failed to load balance", Toast.LENGTH_SHORT).show()
            }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF00b09b), Color(0xFF96c93d))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 64.dp, start = 24.dp, end = 24.dp)
                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text("👋 Hello, $name", fontSize = 24.sp, color = Color(0xFF0B5345))
            Spacer(modifier = Modifier.height(16.dp))

            if (balance != null) {
                Text("💰 Balance", fontSize = 20.sp, color = Color(0xFF117864))
                Text("₹${balance}", fontSize = 32.sp, color = Color(0xFF1A5276))
            } else {
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { /* add money */ }) {
                    Text("➕ Add Money")
                }
                Button(onClick = { /* send money */ }) {
                    Text("📤 Send Money")
                }
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
    onSuccess: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val userMap = hashMapOf(
        "name" to name,
        "username" to username,
        "balance" to 1000L  // 🪙 Add initial balance if not present
    )

    db.collection("users")
        .document(uid)
        .set(userMap)
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
                    onSuccess()  // ✅ Call onSuccess regardless
                } ?: run {
                Log.e("AUTH_PROFILE", "❌ currentUser is null")
                onSuccess()  // ✅ Call onSuccess even if user is null
            }

        }
        .addOnFailureListener {
            Log.e("FIRESTORE", "❌ Failed to save user: ${it.message}")
            it.printStackTrace()
        }
}





