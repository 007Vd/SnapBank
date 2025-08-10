package com.example.snapbank


//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import android.app.Activity
import com.example.snapbank.SettingsScreen

import com.example.snapbank.TransactionPage
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
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
import androidx.compose.material.icons.filled.Lock
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
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import android.text.InputType
import android.text.InputFilter
import androidx.compose.foundation.Image
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.FirebaseException
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.snapbank.ui.theme.SnapBankTheme
import androidx.core.view.WindowCompat
//import android.os.Bundle
//import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

//import androidx.compose.ui.Modifier


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapBankTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppFlow(this) // ✅ All UI changes will be inside AppFlow & child composables
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
            "welcome"
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
        "welcome" -> WelcomeScreen { selected ->
            currentScreen = when(selected) {
                "login" -> "login"
                "signup" -> "signup"
                else -> "login"
            }

        }
        "login" -> PhoneLoginScreen(activity) { userId ->
            uid = userId
            val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)

            userRef.get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        currentScreen = "details"
                    } else {
                        // ✅ Step 1: Keeper migration for existing users
                        if (!doc.contains("keeper_balance")) {
                            userRef.update("keeper_balance", 0.0)
                        }

                        if (!doc.contains("pin")) {
                            PromptToSetPin(userId, activity) {
                                currentScreen = "dashboard"
                            }
                        } else {
                            currentScreen = "dashboard"
                        }
                    }
                }
                .addOnFailureListener {
                    currentScreen = "details"
                }
        }




        "signup" -> PhoneLoginScreen(activity) { userId ->
            uid = userId
            currentScreen = "details"
        }

        "details" -> UserDetailsScreen(uid) {
            shouldCheckPin = true
            currentScreen = "dashboard"
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
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(4))
        hint = "Enter 4-digit PIN"
    }

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
fun SnapBankButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val scanner = BarcodeScanning.getClient()

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { scanned ->
                                imageProxy.close()
                                scanner.close()
                                cameraProvider.unbindAll()
                                onScanned(scanned)
                                return@addOnSuccessListener
                            }
                        }
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }
            }
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
    }
}

fun shareQRCode(context: Context, bitmap: Bitmap) {
    try {
        val fileName = "qr_code_share_${System.currentTimeMillis()}.png"
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, fileName)

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = "image/png"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share QR Code via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share QR: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}


fun saveQRCodeToGallery(context: Context, bitmap: Bitmap, fileName: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyAppQR")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        resolver.openOutputStream(it)?.use { outStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        }

        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(it, contentValues, null, null)

        Toast.makeText(context, "QR code saved to gallery!", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to save QR code.", Toast.LENGTH_SHORT).show()
    }

}


fun generateQRCode(text: String, context: Context): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun SendMoneyScreen(senderUid: String) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var recipient by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0XFF87CEEB),
            Color.Blue,
            Color.Black,
            Color.Blue,
            Color(0XFF87CEEB)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Send Money 💸",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF006064)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("Recipient Phone Number") },
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                leadingIcon = { Icon(Icons.Filled.CurrencyRupee, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val amount = amountText.toLongOrNull()
                    when {
                        recipient.isBlank() -> Toast.makeText(context, "⚠ Enter recipient phone", Toast.LENGTH_SHORT).show()
                        amount == null || amount <= 0 -> Toast.makeText(context, "⚠ Enter a valid amount", Toast.LENGTH_SHORT).show()
                        else -> {
                            verifyPin(context, senderUid) {
                                sending = true
                                performMoneyTransfer(
                                    senderUid = senderUid,
                                    recipient = recipient,
                                    amount = amount,
                                    db = db,
                                    context = context
                                ) { success, message ->
                                    sending = false
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        recipient = ""
                                        amountText = ""
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                shape = CutCornerShape(50.dp)
            ) {
                Text(
                    if (sending) "Sending..." else "Send Money",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // QR Code Scan Button
            Button(
                onClick = { showScanner = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121)),
                shape = CutCornerShape(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code", color = Color.White, fontSize = 16.sp)
            }
        }
    }

    // ✅ Show QR Scanner when needed
    if (showScanner) {
        QRScannerScreen(
            onScanned = { scannedPhone ->
                showScanner = false
                recipient = scannedPhone
            },
            onCancel = { showScanner = false }
        )
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
        .whereEqualTo("phone", recipient)
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

                if (senderBalance < amount) throw Exception("Insufficient balance")

                transaction.update(senderRef, "balance", senderBalance - amount)
                transaction.update(recipientRef, "balance", recipientBalance + amount)

                val timestamp = System.currentTimeMillis()
                val senderTxn = hashMapOf(
                    "type" to "Sent",
                    "amount" to amount,
                    "timestamp" to timestamp,
                    "name" to (recipientDoc.getString("name") ?: ""),
                    "phone" to (recipientDoc.getString("phone") ?: "")
                )
                val recipientTxn = hashMapOf(
                    "type" to "Received",
                    "amount" to amount,
                    "timestamp" to timestamp,
                    "name" to (senderSnapshot.getString("username") ?: ""),
                    "phone" to (senderSnapshot.getString("phone") ?: "")
                )

                transaction.set(senderRef.collection("transactions").document(), senderTxn)
                transaction.set(recipientRef.collection("transactions").document(), recipientTxn)
            }.addOnSuccessListener {
                val successMessage = "✅ ₹$amount sent to $recipient"
                onComplete(true, successMessage)
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { exception ->
                val errorMessage = exception.message ?: "Transaction failed"
                onComplete(false, "❌ $errorMessage")
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
            .background(color = Color(0xFFD2A679))
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
    val coroutineScope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser
    val name = user?.displayName ?: "User"
    var showQR by remember { mutableStateOf(false) }

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

    val gradient = Brush.verticalGradient(colors = listOf(Color(0XFF87CEEB),Color.Blue, Color.Black, Color.Blue,Color(0XFF87CEEB)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center,

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
                        AnimatedVisibility(
                            visible = showBalance,
                            enter = fadeIn(animationSpec = tween(700)) + scaleIn(initialScale = 0.8f),
                            exit = fadeOut()
                        ){
                            Text("₹$balance", fontSize = 32.sp, color = Color(0xFF004D40))
                        }
                    } else {
                        Button(
                            onClick = {
                                verifyPin(context, uid) {
                                    coroutineScope.launch {
                                        delay(400)
                                        showBalance = true
                                    }

                                }
                            },
                        ) {
                            Text("🔒 Tap to View Balance", color = Color.Yellow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ➕ Add Money Button
            Button(
                onClick = { showAddMoneyDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x88000000)),
                shape = CutCornerShape(50.dp)
            ) {
                Text("➕ Add Money", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔁 Change PIN Button
            Button(
                onClick = { showChangePinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                shape = CutCornerShape(50.dp)
            ) {
                Text("🔁 Change PIN", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 📷 QR Code Button
            Button(
                onClick = { showQR = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121)),
                shape = CutCornerShape(50.dp)
            ) {
                Text("📷 My QR Code", fontSize = 16.sp, color = Color.White)
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
    if (showQR) {
        QRCodeScreen(uid) {
            showQR = false
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScreen(uid: String, onBack: () -> Unit) {
    val context = LocalContext.current

    // ✅ Get phone number if available, otherwise fall back to UID
    val currentUser = FirebaseAuth.getInstance().currentUser
    val dataToEncode = currentUser?.phoneNumber ?: uid

    // ✅ Log to verify what we are encoding
    Log.d("QR_CODE", "Encoding: $dataToEncode")

    // ✅ Generate QR code based on phone number (or UID fallback)
    val qrCodeBitmap = remember(dataToEncode) { generateQRCode(dataToEncode, context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Scan this to send money", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            qrCodeBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(250.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ Row with Download & Share buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = {
                        saveQRCodeToGallery(context, bitmap, "qr_code_${System.currentTimeMillis()}.png")
                    }) {
                        Text("Download QR")
                    }

                    Button(onClick = {
                        shareQRCode(context, bitmap)
                    }) {
                        Text("Share QR")
                    }
                }
            }
        }
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
fun WelcomeScreen(onSelection: (String) -> Unit) {
    val gradient = Brush.verticalGradient(listOf(Color(0xFFFFA726), Color(0xFFF57C00)))

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
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text("👋 Welcome to SnapBank", fontSize = 28.sp, color = Color(0xFFF57C00))
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSelection("login") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🔐 Login")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onSelection("signup") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🆕 Sign Up")
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
            Text(" Login ", fontSize = 24.sp, color = Color(0xFF4A00E0))

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
fun transferToKeeper(userId: String, amount: Double, context: Context) {
    val db = FirebaseFirestore.getInstance()
    val userRef = db.collection("users").document(userId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(userRef)
        val mainBalance = snapshot.getDouble("balance") ?: 0.0
        val keeperBalance = snapshot.getDouble("keeper_balance") ?: 0.0

        if (mainBalance >= amount) {
            transaction.update(userRef, mapOf(
                "balance" to (mainBalance - amount),
                "keeper_balance" to (keeperBalance + amount)
            ))
        } else {
            throw Exception("Insufficient balance")
        }
    }.addOnSuccessListener {
        Toast.makeText(context, "Saved ₹$amount to Keeper", Toast.LENGTH_SHORT).show()
    }.addOnFailureListener {
        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
    }
}

fun withdrawFromKeeper(userId: String, amount: Double, context: Context) {
    val db = FirebaseFirestore.getInstance()
    val userRef = db.collection("users").document(userId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(userRef)
        val mainBalance = snapshot.getDouble("balance") ?: 0.0
        val keeperBalance = snapshot.getDouble("keeper_balance") ?: 0.0

        if (keeperBalance >= amount) {
            transaction.update(userRef, mapOf(
                "balance" to (mainBalance + amount),
                "keeper_balance" to (keeperBalance - amount)
            ))
        } else {
            throw Exception("Insufficient Keeper balance")
        }
    }.addOnSuccessListener {
        Toast.makeText(context, "Withdrew ₹$amount from Keeper", Toast.LENGTH_SHORT).show()
    }.addOnFailureListener {
        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
    }
}
@Composable
fun KeeperScreen(uid: String) {
    val context = LocalContext.current
    var keeperBalance by remember { mutableStateOf(0.0) }
    var amountInput by remember { mutableStateOf("") }

    // Load Keeper Balance
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    keeperBalance = snapshot.getDouble("keeper_balance") ?: 0.0
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFD2A679))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Keeper Balance", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("₹$keeperBalance", fontSize = 26.sp, color = Color.Red)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it },
            label = { Text("Enter Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save to Keeper
        Button(
            onClick = {
                val amount = amountInput.toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    verifyPin(context, uid) {
                        transferToKeeper(uid, amount, context)
                    }
                } else {
                    Toast.makeText(context, "Enter valid amount", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save to Keeper")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Withdraw from Keeper
        Button(
            onClick = {
                val amount = amountInput.toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    verifyPin(context, uid) {
                        withdrawFromKeeper(uid, amount, context)
                    }
                } else {
                    Toast.makeText(context, "Enter valid amount", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
        ) {
            Text("Withdraw from Keeper")
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

            val phone = FirebaseAuth.getInstance().currentUser?.phoneNumber

            // ✅ User data map with Keeper support
            val userMap = hashMapOf(
                "name" to name,
                "username" to username,
                "balance" to 1000L,
                "keeper_balance" to 0.0,   // ✅ Added Keeper balance field
                "phone" to phone
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
