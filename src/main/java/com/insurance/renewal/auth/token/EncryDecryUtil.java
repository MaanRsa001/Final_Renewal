package com.insurance.renewal.auth.token;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryDecryUtil {

	private static final String SECRET = "MaanSarovar@1234";

	public static String encrypt(String data) throws Exception {
		SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] encrypted = cipher.doFinal(data.getBytes());
		return Base64.getEncoder().encodeToString(encrypted);
	}

	public static String decrypt(String encryptedData) throws Exception {
		SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] decoded = Base64.getDecoder().decode(encryptedData);
		return new String(cipher.doFinal(decoded));
	}


}