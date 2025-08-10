package com.example.snapbank

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

fun fetchBalance(uid: String, onComplete: (Long?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    db.collection("users").document(uid)
        .get()
        .addOnSuccessListener { document ->
            if (document.exists()) {
                val balance = document.getLong("balance") ?: 0L
                onComplete(balance)
            } else {
                onComplete(null)
            }
        }
        .addOnFailureListener {
            onComplete(null)
        }
}

@Composable
fun MainNavigationScreen(uid: String) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val tabItems = listOf("Dashboard", "Transactions", "Send", "Keeper", "Settings")

    // Dialog states
    var showQR by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var addAmount by remember { mutableStateOf("") }
    var addingMoney by remember { mutableStateOf(false) }

    // Balance display states
    var showBalanceDialog by remember { mutableStateOf(false) }
    var currentBalance by remember { mutableStateOf<Long?>(null) }
    var loadingBalance by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            CustomBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    if (index == 3) { // Keeper tab
                        verifyPin(context, uid) {
                            selectedTab = index
                        }
                    } else {
                        selectedTab = index
                    }
                },
                onQRScannerClick = { showQRScanner = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    uid = uid,
                    onNavigateToTab = { tabIndex -> selectedTab = tabIndex },
                    onShowQRCode = { showQR = true },
                    onShowAddMoney = { showAddMoneyDialog = true },
                    onShowChangePin = { showChangePinDialog = true },
                    onShowAccountBalance = {
                        verifyPin(context, uid) {
                            loadingBalance = true
                            fetchBalance(uid) { balance ->
                                currentBalance = balance
                                loadingBalance = false
                                showBalanceDialog = true
                            }
                        }
                    },
                    onShowKeeper = {
                        verifyPin(context, uid) {
                            selectedTab = 3 // Navigate to Keeper tab after PIN verification
                        }
                    }
                )
                1 -> TransactionPage(uid)
                2 -> SendMoneyScreen(uid)
                3 -> KeeperScreen(uid)
                4 -> SettingsScreen(
                    uid = uid,
                    onChangePin = {
                        // ✅ Example action: Navigate to Change PIN Screen
                        Toast.makeText(context, "Change PIN clicked!", Toast.LENGTH_SHORT).show()
                    },
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(context, "Logged out!", Toast.LENGTH_SHORT).show()
                        // ✅ Optionally navigate back to Login Screen
                    }
                )
                // ✅ Pass uid for settings screen
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
                    if (addingMoney) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("Add Money")
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

    // 📷 QR Code Screen
    if (showQR) {
        QRCodeScreen(uid) {
            showQR = false
        }
    }

    // 📷 QR Scanner Screen
    if (showQRScanner) {
        QRScannerScreen(
            onScanned = { scannedData ->
                showQRScanner = false
                // You can handle the scanned data here (e.g., show a dialog, navigate, etc.)
                Toast.makeText(context, "Scanned: $scannedData", Toast.LENGTH_LONG).show()
            },
            onCancel = { showQRScanner = false }
        )
    }

    // 💰 Balance Display Dialog
    if (showBalanceDialog) {
        AlertDialog(
            onDismissRequest = {
                showBalanceDialog = false
                currentBalance = null
            },
            title = { Text("💰 Account Balance") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (loadingBalance) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading balance...")
                    } else {
                        currentBalance?.let { balance ->
                            Text(
                                text = "₹${balance}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF004D40)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Current available balance",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBalanceDialog = false
                        currentBalance = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun CustomBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onQRScannerClick: () -> Unit = {}
) {
    // Define Material 3 colors matching the design
    val surfaceContainerColor = Color(0xFFF3EDF7)
    val secondaryContainerColor = Color(0xFFE8DEF8)
    val secondaryColor = Color(0xFF625B71)
    val onSurfaceVariantColor = Color(0xFF49454F)
    val onSurfaceColor = Color(0xFF1D1B20)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Navigation items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceContainerColor)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // Home
            CustomNavigationItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = selectedTab == 0,
                secondaryContainerColor = secondaryContainerColor,
                secondaryColor = secondaryColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )

            // Scan QR (middle)
            CustomQRNavigationItem(
                label = "Scan QR",
                isSelected = false, // QR scanner is not a main tab
                secondaryContainerColor = secondaryContainerColor,
                secondaryColor = secondaryColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                onClick = onQRScannerClick,
                modifier = Modifier.weight(1f)
            )

            // Settings
            CustomNavigationItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = selectedTab == 4,
                secondaryContainerColor = secondaryContainerColor,
                secondaryColor = secondaryColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                onClick = { onTabSelected(4) },
                modifier = Modifier.weight(1f)
            )
        }

        // Gesture bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(surfaceContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(4.dp)
                    .background(onSurfaceColor, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun CustomNavigationItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    secondaryContainerColor: Color,
    secondaryColor: Color,
    onSurfaceVariantColor: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Icon container with proper Material 3 styling
        Box(
            modifier = Modifier
                .background(
                    if (isSelected) secondaryContainerColor else Color.Transparent,
                    RoundedCornerShape(16.dp)
                )
                .size(width = 56.dp, height = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF1E1E1E) else onSurfaceVariantColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // Label with exact Figma styling
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) secondaryColor else onSurfaceVariantColor,
            letterSpacing = 0.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CustomQRNavigationItem(
    label: String,
    isSelected: Boolean,
    secondaryContainerColor: Color,
    secondaryColor: Color,
    onSurfaceVariantColor: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // QR Icon container with actual QR image as shown in Figma
        Box(
            modifier = Modifier
                .background(
                    if (isSelected) secondaryContainerColor else Color.Transparent,
                    RoundedCornerShape(16.dp)
                )
                .size(width = 56.dp, height = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = "https://api.builder.io/api/v1/image/assets/TEMP/44aba617a6e45f62e616d1fe3e1182c61fe3081a?width=112",
                contentDescription = "QR Code",
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        }

        // Label with exact Figma styling
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) secondaryColor else onSurfaceVariantColor,
            letterSpacing = 0.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

