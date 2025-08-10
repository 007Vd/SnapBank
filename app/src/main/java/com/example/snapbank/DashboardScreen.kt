package com.example.snapbank

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uid: String,
    onNavigateToTab: (Int) -> Unit = {},
    onShowQRCode: () -> Unit = {},
    onShowAddMoney: () -> Unit = {},
    onShowChangePin: () -> Unit = {},
    onShowAccountBalance: () -> Unit = {},
    onShowKeeper: () -> Unit = {}
) {
    // Define Material 3 colors matching the exact design
    val surfaceColor = Color(0xFFFEF7FF)
    val primaryContainerColor = Color(0xFFEADDFF)
    val onPrimaryContainerColor = Color(0xFF4F378A)
    val primaryColor = Color(0xFF6750A4)
    val onPrimaryColor = Color(0xFFFFFFFF)
    val onSurfaceColor = Color(0xFF1D1B20)
    val onSurfaceVariantColor = Color(0xFF49454F)
    val outlineVariantColor = Color(0xFFCAC4D0)
    val surfaceContainerColor = Color(0xFFF3EDF7)
    val surfaceContainerHighColor = Color(0xFFECE6F0)
    val secondaryContainerColor = Color(0xFFE8DEF8)
    val secondaryColor = Color(0xFF625B71)
    val actionGridBackground = Color(0xFFF8F8F5)
    val coroutineScope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser
    val name = user?.displayName ?: "User"
    var showQR by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val balanceState = remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()


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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Bar
            DashboardAppBar(
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                outlineVariantColor = outlineVariantColor
            )

            // Action Grid
            ActionGrid(
                primaryContainerColor = primaryContainerColor,
                onPrimaryContainerColor = onPrimaryContainerColor,
                actionGridBackground = actionGridBackground,
                onAccountBalanceClick = onShowAccountBalance, // Show balance with PIN verification
                onPayAnyoneClick = { onNavigateToTab(2) }, // Navigate to Send Money
                onQRCodeClick = onShowQRCode,
                onKeeperClick = onShowKeeper // Navigate to Keeper with PIN verification
            )

            // Pay Contacts Section
            PayContactsSection(
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                surfaceColor = surfaceColor
            )

            // More Like Section
            MoreLikeSection(
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                surfaceColor = surfaceColor,
                onAddMoneyClick = onShowAddMoney,
                onChangePinClick = onShowChangePin,
                onTransactionsClick = { onNavigateToTab(1) }
            )
        }
    }
}
@Composable
fun DashboardAppBar(
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    outlineVariantColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top row with icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Leading icon (Account Circle)
            IconButton(
                onClick = { },
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, outlineVariantColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Account",
                    tint = onSurfaceColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        val user = Firebase.auth.currentUser
        val username = user?.displayName ?: "User"
        Text(
            text = username ,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = onSurfaceColor,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 36.sp
        )
    }
}

@Composable
fun ActionGrid(
    primaryContainerColor: Color,
    onPrimaryContainerColor: Color,
    actionGridBackground: Color,
    onAccountBalanceClick: () -> Unit = {},
    onPayAnyoneClick: () -> Unit = {},
    onQRCodeClick: () -> Unit = {},
    onKeeperClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(width = 376.dp, height = 220.dp)
            .background(actionGridBackground, RoundedCornerShape(2.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
            .padding(10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                ActionCard(
                    title = "Account Balance",
                    icon = Icons.Default.Star,
                    primaryContainerColor = primaryContainerColor,
                    onPrimaryContainerColor = onPrimaryContainerColor,
                    onClick = onAccountBalanceClick,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    title = "Pay Anyone",
                    icon = Icons.Default.Star,
                    primaryContainerColor = primaryContainerColor,
                    onPrimaryContainerColor = onPrimaryContainerColor,
                    onClick = onPayAnyoneClick,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                ActionCard(
                    title = "My QR Code",
                    icon = Icons.Default.Star,
                    primaryContainerColor = primaryContainerColor,
                    onPrimaryContainerColor = onPrimaryContainerColor,
                    onClick = onQRCodeClick,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    title = "Keeper",
                    icon = Icons.Default.Star,
                    primaryContainerColor = primaryContainerColor,
                    onPrimaryContainerColor = onPrimaryContainerColor,
                    onClick = onKeeperClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    icon: ImageVector,
    primaryContainerColor: Color,
    onPrimaryContainerColor: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .size(width = 160.dp, height = 88.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primaryContainerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = onPrimaryContainerColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = onPrimaryContainerColor,
                letterSpacing = 0.15.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun PayContactsSection(
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    surfaceColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .padding(bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pay Contacts",
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF381E72),
                lineHeight = 28.sp
            )
            IconButton(
                onClick = { },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "View all",
                    tint = onSurfaceVariantColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Contact avatars carousel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(8) { index ->
                ContactItem(
                    label = if (index < 6) "Label" else "",
                    imageUrl = when (index) {
                        4 -> "https://api.builder.io/api/v1/image/assets/TEMP/6758989ea0cdc8098e86d92ed48af6bfd51ec092?width=108"
                        5 -> "https://api.builder.io/api/v1/image/assets/TEMP/abc3d703c0fcfa3ae4632f30d68d64166df9f78a?width=108"
                        else -> "https://api.builder.io/api/v1/image/assets/TEMP/0b3030af0f251518b584f4fafaef48384173acb2?width=108"
                    },
                    onSurfaceColor = onSurfaceColor,
                    isVisible = index < 6,
                    alpha = if (index == 5) 0.54f else 1f
                )
            }
        }
    }
}

@Composable
fun ContactItem(
    label: String,
    imageUrl: String,
    onSurfaceColor: Color,
    isVisible: Boolean = true,
    alpha: Float = 1f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(alpha)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = label,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        if (isVisible && label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = onSurfaceColor,
                textAlign = TextAlign.Center,
                letterSpacing = 0.1.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(54.dp)
            )
        }
    }
}

@Composable
fun MoreLikeSection(
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color,
    onPrimaryColor: Color,
    surfaceColor: Color,
    onAddMoneyClick: () -> Unit = {},
    onChangePinClick: () -> Unit = {},
    onTransactionsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with user info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = "https://api.builder.io/api/v1/image/assets/TEMP/b6efc7b74a9d33845e58d9807000a88aaee890e6?width=80",
                    contentDescription = "User",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        text = "More like",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = onSurfaceVariantColor,
                        letterSpacing = 0.4.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Title",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceColor,
                        letterSpacing = 0.15.sp,
                        lineHeight = 24.sp
                    )
                }
            }
            IconButton(
                onClick = { },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "View more",
                    tint = onSurfaceVariantColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                text = "Add Money",
                icon = Icons.Default.Star,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                onClick = onAddMoneyClick
            )
            ActionButton(
                text = "Change Pin",
                icon = Icons.Default.Star,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                onClick = onChangePinClick
            )
            ActionButton(
                text = "Transactions",
                icon = Icons.Default.Star,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                onClick = onTransactionsClick
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    primaryColor: Color,
    onPrimaryColor: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = onPrimaryColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun QRNavigationItem(
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

@Composable
fun NavigationItem(
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
