**Project Title :**  
**SnapBank**

**Screenshots :**  
https://drive.google.com/drive/folders/1mG3LvyJHFnk991dAmUO2SF7lozJx5piw  


**Features Implemented :**

**Frontend Features:**  
1\.**Dashboard Screen**: Displays account balance, quick access to 'Send Money' and 'View Balance' buttons, and navigation to other sections.  
2\.**Send Money Screen**: Allows searching contacts by name (from local phonebook) and displays suggestions only if the contact is registered in Firestore.  
3\.**QR Code Payment**: Generates a unique QR code based on the user's phone number and scans other users’ QR codes to fetch their phone numbers for transactions.  
4\.**Transaction History Page**: Shows a detailed list of past transactions with date, time, and transaction amount.  
5\.**UI Design**: Minimal and clean layout with clearly labeled buttons, modern color schemes, and intuitive navigation.  
6\.**Login/Signup Page with OTP and PIN Setup**: Allows users to create or log into their account via phone number verification using OTP. New account creation includes an option to set a secure PIN for future transactions.  
**Backend Features:**

**1\.	Firebase Phone Authentication:** Handles user sign-up and login using OTP verification.  
**2\.	Firestore Integration:** Stores user details including phone number, name, balance, and transaction history.  
**3\.	PIN Verification:** Before sending money, validates the entered PIN against the one stored securely in Firestore.  
**4\.	Real-Time Balance Updates:** Ensures both sender and receiver balances are updated immediately after a transaction.  
**5\.	Contact Verification:** Checks if the contact exists in Firestore before displaying it in search results.  
**6\.	QR Code Generation and Scanning:** Uses phone number as the encoded data to identify users in transactions.  
**7\.	Permissions Handling:** Requests and manages runtime permissions for accessing contacts and camera for QR code scanning.

**Technologies Used :**

1.  Programming Language: Kotlin  
     
2. UI Framework: Jetpack Compose (Material 3 Design)  
     
3. Backend Services: Firebase Authentication, Firebase Firestore, Firebase Analytics

4. Image Loading: Coil for Jetpack Compose  
     
5. Animations: Lottie, Shimmer Effect  
     
6. QR & Barcode Scanning: Google ML Kit, ZXing  
     
7. Camera API: AndroidX CameraX  
     
8. Architecture: MVVM (Model-View-ViewModel)  
     
9. Platform: Android (Minimum SDK … / Target SDK …)  
     
10. Version Control: Git & GitHub  
      
    

**Libraries and packages used :**  
**Frontend (UI / UX)**  
•	Jetpack Compose Core  
•	androidx.activity:activity-compose – Compose activity support  
•	androidx.compose.ui:ui – Core UI elements  
•	androidx.compose.ui:ui-tooling-preview – Preview UI in Android Studio  
•	androidx.compose.ui:ui-graphics – UI graphics rendering  
•	androidx.compose.material3:material3 – Material Design 3 components  
•	androidx.compose.material:material-icons-extended – Extended Material Icons  
•	Animations & Effects  
•	com.airbnb.android:lottie-compose – Lottie animations  
•	com.valentinilk.shimmer:compose-shimmer – Shimmer loading effect  
•	Image Loading  
•	io.coil-kt:coil-compose – Image loading in Jetpack Compose

**Backend (Logic, Data, and Services)**  
•	Firebase  
•	com.google.firebase:firebase-auth-ktx – OTP authentication & account handling  
•	com.google.firebase:firebase-firestore-ktx – Firestore database for storing user/account data  
•	com.google.firebase:firebase-analytics – Usage analytics  
•	com.google.android.gms:play-services-auth – Google Play services auth support  
•	Networking  
•	com.squareup.retrofit2:retrofit – HTTP requests handling  
•	com.squareup.retrofit2:converter-gson – JSON data parsing  
•	Barcode & QR Scanning  
•	com.google.mlkit:barcode-scanning – ML Kit barcode scanning  
•	com.google.zxing:core – QR code encoding/decoding  
•	Camera  
•	androidx.camera:camera-core  
•	androidx.camera:camera-camera2  
•	androidx.camera:camera-lifecycle  
•	androidx.camera:camera-view  
•	androidx.camera:camera-mlkit-vision  
**\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_**  
**Core Android / Kotlin**  
•	androidx.core:core-ktx – Kotlin extensions for Android core APIs  
•	androidx.lifecycle:lifecycle-runtime-ktx – Lifecycle-aware components

**Local Setup :**  
For the time being, Firebase has blocked the use of real time phone numbers for otp generation due to several reasons. So we had to resort to the use of test case phone numbers for example : \+91xxxxxxxxxx where x ranges from 1 to 9 with the default OTP of 123456\.  
To enable QR scanning, the user has to manually go into the phone settings and allow the camera permissions to the app.  
Some features like the Contacts paying, biometric features, Dark Mode, etc. are still under progress.  
![][image11]  
Here is the screenshot of the OTPs received via Firebase while it was functioning as per requirement.

**Team Members :**

1.  Lakshya Mulchandani  
2.  Vedang Sawant  
3.  Vijit Deogade

**Demo Video :**  
https://drive.google.com/file/d/1j9ouXvp5OfJtH-AoXZBCXcnRVkLNw4kN/view?usp=drivesdk
