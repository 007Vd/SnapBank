package com.example.snapbank.network; // ← change to match your actual package

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SnapBankApi {
    class VerifyPinRequest {
        String userId;
        String pin;

        public VerifyPinRequest(String userId, String pin) {
            this.userId = userId;
            this.pin = pin;
        }
    }

    class VerifyPinResponse {
        boolean valid;

        public boolean isValid() {
            return valid;
        }
    }

    @POST("/verify-pin")
    Call<VerifyPinResponse> verifyPin(@Body VerifyPinRequest request);
}
