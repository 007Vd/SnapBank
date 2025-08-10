package com.example.snapbank.network; // ← change to match your actual package

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String BASE_URL = "http://10.0.2.2:3000"; // For Emulator. Use your Mac IP for real device.
    private static Retrofit retrofit = null;

    public static SnapBankApi getInstance() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(SnapBankApi.class);
    }
}
