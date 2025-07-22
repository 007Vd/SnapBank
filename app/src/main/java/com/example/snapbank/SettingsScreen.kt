package com.example.snapbank

//package com.example.snapbank.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uid: String,
    onChangePin: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    val coroutineScope = rememberCoroutineScope()

    var darkModeEnabled by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Gradient background
    val gradient = Brush.verticalGradient(
        listOf(Color(0xFF87CEEB), Color(0xFF2196F3), Color(0xFF0D47A1))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            // Profile Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = rememberAsyncImagePainter(user?.photoUrl ?: ""),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(user?.displayName ?: "User", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(user?.phoneNumber ?: "Phone not linked", color = Color.Gray, fontSize = 14.sp)
                    Text("UID: $uid", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider()

            // General Settings
            Text("General", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            SettingsItem(Icons.Default.Edit, "Edit Profile") { showEditProfileDialog = true }
            SettingsItem(Icons.Default.Lock, "Change PIN") { onChangePin() }
            SettingsSwitch(Icons.Default.DarkMode, "Dark Mode", darkModeEnabled) { darkModeEnabled = it }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()

            // Security
            Text("Security", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            SettingsSwitch(Icons.Default.Fingerprint, "Enable Biometric", biometricEnabled) {
                biometricEnabled = it
                Toast.makeText(context, "Biometric setup coming soon!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()

            // App Settings
            Text("App", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            SettingsSwitch(Icons.Default.Notifications, "Notifications", notificationsEnabled) { notificationsEnabled = it }
            SettingsItem(Icons.Default.Delete, "Clear Cache") {
                Toast.makeText(context, "Cache Cleared ✅", Toast.LENGTH_SHORT).show()
            }
            SettingsItem(Icons.Default.Info, "About SnapBank") { showAboutDialog = true }
            SettingsItem(Icons.Default.Support, "Contact Support") {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@snapbank.com")
                    putExtra(Intent.EXTRA_SUBJECT, "SnapBank Support")
                }
                context.startActivity(emailIntent)
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Logout Button
            Button(
                onClick = { onLogout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("🚪 Logout", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        EditProfileDialog(
            currentName = user?.displayName ?: "",
            currentPhone = user?.phoneNumber ?: "",
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, phone ->
                coroutineScope.launch {
                    // update firebase here (optional)
                    Toast.makeText(context, "Profile Updated ✅", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About SnapBank") },
            text = { Text("SnapBank v1.0\nA modern mobile banking app built using Jetpack Compose.") },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFF0D47A1))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, fontSize = 15.sp)
    }
}

@Composable
fun SettingsSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = Color(0xFF0D47A1))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentPhone: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var phone by remember { mutableStateOf(currentPhone) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Edit Profile") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone); onDismiss() }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
        }
    )
}
